package ar.edu.unc.fcefyn.pcp.tp1.solution;

import ar.edu.unc.fcefyn.pcp.tp1.api.OrderState;
import ar.edu.unc.fcefyn.pcp.tp1.api.PrinterState;
import ar.edu.unc.fcefyn.pcp.tp1.api.SimulationConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
class SimulationMonitorConcurrencyTest {
    private static final List<String> QUEUES = List.of(
            "assignmentQueue", "validationQueue", "printingQueue", "qualityControlQueue");

    @TempDir
    Path directory;

    // Pausa una extraccion real dentro de la seccion critica: otra cola debe avanzar.
    @ParameterizedTest
    @CsvSource({
            "assignmentQueue,validationQueue",
            "validationQueue,printingQueue",
            "printingQueue,qualityControlQueue",
            "qualityControlQueue,assignmentQueue"
    })
    void takingFromOneQueueDoesNotBlockAnother(String blockedName, String independentName) throws Exception {
        EventLogger logger = new EventLogger(directory, System.nanoTime());
        SimulationMonitor monitor = new SimulationMonitor(config(directory, 2, 1, 100, 42), logger);
        PausingQueue blockedQueue = new PausingQueue();
        Order blockedOrder = new Order(1);
        Order independentOrder = new Order(2);
        blockedQueue.offer(blockedOrder);
        Field field = SimulationMonitor.class.getDeclaredField(blockedName);
        field.setAccessible(true);
        field.set(monitor, blockedQueue);
        Queue<Order> independentQueue = queue(monitor, independentName);
        independentQueue.clear();
        independentQueue.offer(independentOrder);
        List<RunningTask<?>> tasks = new ArrayList<>();
        try {
            RunningTask<Order> first = start(() -> take(monitor, blockedName), tasks);
            assertTrue(blockedQueue.entered.await(2, TimeUnit.SECONDS), "No comenzo la extraccion");
            RunningTask<Order> second = start(() -> take(monitor, independentName), tasks);
            assertSame(independentOrder, second.result.get(2, TimeUnit.SECONDS),
                    "Una cola distinta debe avanzar sin esperar la extraccion bloqueada");
            blockedQueue.release.countDown();
            assertSame(blockedOrder, first.result.get(2, TimeUnit.SECONDS));
        } finally {
            blockedQueue.release.countDown();
            stop(tasks);
            logger.close();
        }
    }

    @Test
    void cancellationWakesEveryQueueAndPrinterWaiter() throws Exception {
        EventLogger logger = new EventLogger(directory, System.nanoTime());
        SimulationMonitor monitor = new SimulationMonitor(config(directory, 2, 1, 100, 42), logger);
        List<RunningTask<?>> tasks = new ArrayList<>();
        try {
            Order first = monitor.takeForAssignment();
            Order second = monitor.takeForAssignment();
            assertTrue(monitor.assignPrinter(first));
            assertSame(first, monitor.takeForValidation());
            for (String name : QUEUES) {
                start(() -> take(monitor, name), tasks);
            }
            RunningTask<Boolean> printerWaiter = start(() -> monitor.assignPrinter(second), tasks);
            start(() -> { monitor.awaitCompletion(); return null; }, tasks);
            awaitWaiting(tasks);
            IllegalStateException cause = new IllegalStateException("cancelacion de prueba");
            monitor.cancel(cause);
            for (RunningTask<?> task : tasks) {
                Object result = task.result.get(2, TimeUnit.SECONDS);
                if (task == printerWaiter) {
                    assertEquals(false, result);
                } else {
                    assertNull(result);
                }
            }
            monitor.cancel(new IllegalStateException("segunda causa"));
            assertSame(cause, monitor.getFailure());
            monitor.releaseReservationsAfterCancellation();
            assertTrue(monitor.getPrinterSnapshots().stream()
                    .noneMatch(p -> p.state() == PrinterState.RESERVED));
        } finally {
            stop(tasks);
            logger.close();
        }
    }

    @Test
    void lastTerminalOrderWakesEveryEmptyQueueAndTheCoordinator() throws Exception {
        EventLogger logger = new EventLogger(directory, System.nanoTime());
        SimulationMonitor monitor = new SimulationMonitor(config(directory, 1, 1, 100, 42), logger);
        List<RunningTask<?>> tasks = new ArrayList<>();
        try {
            Order order = monitor.takeForAssignment();
            assertTrue(monitor.assignPrinter(order));
            assertSame(order, monitor.takeForValidation());
            monitor.completeValidation(order, true);
            assertSame(order, monitor.takeForPrinting());
            monitor.completePrinting(order, true);
            assertSame(order, monitor.takeForQualityControl());
            for (String name : QUEUES) {
                start(() -> take(monitor, name), tasks);
            }
            start(() -> { monitor.awaitCompletion(); return null; }, tasks);
            awaitWaiting(tasks);
            monitor.completeQualityControl(order, true);
            for (RunningTask<?> task : tasks) {
                assertNull(task.result.get(2, TimeUnit.SECONDS));
            }
            assertEquals(1, monitor.getProcessedOrders());
            assertEquals(0, monitor.getRemainingIntermediateOrders());
            assertNull(monitor.getFailure());
        } finally {
            stop(tasks);
            logger.close();
        }
    }

    @Test
    void eventWriteFailureCancelsAndWakesWaitingStages() throws Exception {
        EventLogger logger = new EventLogger(directory, System.nanoTime());
        SimulationMonitor monitor = new SimulationMonitor(config(directory, 1, 1, 100, 42), logger);
        List<RunningTask<?>> tasks = new ArrayList<>();
        try {
            Order order = monitor.takeForAssignment();
            for (String name : QUEUES) {
                start(() -> take(monitor, name), tasks);
            }
            start(() -> { monitor.awaitCompletion(); return null; }, tasks);
            awaitWaiting(tasks);
            logger.close(); // Una escritura real sobre el archivo cerrado debe fallar.
            UncheckedIOException error = assertThrows(UncheckedIOException.class,
                    () -> monitor.assignPrinter(order));
            for (RunningTask<?> task : tasks) {
                assertNull(task.result.get(2, TimeUnit.SECONDS));
            }
            assertSame(error, monitor.getFailure());
            monitor.releaseReservationsAfterCancellation();
            assertTrue(monitor.getPrinterSnapshots().stream()
                    .noneMatch(p -> p.state() == PrinterState.RESERVED));
        } finally {
            stop(tasks);
            logger.close();
        }
    }

    // Con demoras nulas se fuerzan transferencias rapidas, reutilizacion y cierres en carrera.
    @ParameterizedTest
    @CsvSource({"1,100", "401,85"})
    void repeatedRunsKeepOrderedEventsAndExclusivePrinterReservations(int printers, int printSuccess)
            throws Exception {
        for (int run = 0; run < 10; run++) {
            Path output = directory.resolve("run-" + run);
            SimulationConfig config = config(output, 400, printers, printSuccess, 20261001L + run);
            var result = new ConcurrentSimulation().execute(config);
            assertEquals(400, result.processedOrders());
            assertTrue(result.allThreadsTerminated());
            assertEquals(400, result.printers().stream().mapToInt(p -> p.usageCount()).sum());
            assertTrue(result.printers().stream().noneMatch(p -> p.state() == PrinterState.RESERVED));

            Map<Integer, OrderState> states = new HashMap<>();
            Map<String, Integer> reservations = new HashMap<>();
            List<String> lines = Files.readAllLines(output.resolve("eventos.csv"));
            for (int index = 1; index < lines.size(); index++) {
                String[] event = lines.get(index).split(";", -1);
                assertEquals(index, Long.parseLong(event[0]));
                int id = Integer.parseInt(event[3]);
                OrderState next = OrderState.valueOf(event[7]);
                if (event[5].equals("ORDER_CREATED")) {
                    assertEquals("", event[6]);
                    assertEquals(OrderState.CREATED, next);
                    assertNull(states.put(id, next));
                } else {
                    OrderState previous = OrderState.valueOf(event[6]);
                    assertEquals(previous, states.put(id, next), "Eventos fuera de orden");
                    if (next == OrderState.WAITING_VALIDATION) {
                        assertNull(reservations.put(event[8], id), "Impresora asignada a dos ordenes");
                    } else if (next == OrderState.REJECTED || next == OrderState.PRINTED
                            || next == OrderState.PRINT_FAILED) {
                        assertEquals(Integer.valueOf(id), reservations.remove(event[8]));
                    }
                }
            }
            assertTrue(reservations.isEmpty());
            assertEquals(400, states.size());
            for (var order : result.orders()) {
                assertTrue(order.state().isTerminal());
                assertEquals(order.state(), states.get(order.id()));
            }
        }
    }

    private static SimulationConfig config(Path output, int orders, int printers, int printSuccess, long seed) {
        return new SimulationConfig(orders, 1, printers, 8, 8, 8, 8,
                85, printSuccess, 95, 0, 0, 0, 0, seed, output);
    }

    @SuppressWarnings("unchecked")
    private static Queue<Order> queue(SimulationMonitor monitor, String name) throws Exception {
        Field field = SimulationMonitor.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Queue<Order>) field.get(monitor);
    }

    private static Order take(SimulationMonitor monitor, String name) throws InterruptedException {
        return switch (name) {
            case "assignmentQueue" -> monitor.takeForAssignment();
            case "validationQueue" -> monitor.takeForValidation();
            case "printingQueue" -> monitor.takeForPrinting();
            case "qualityControlQueue" -> monitor.takeForQualityControl();
            default -> throw new IllegalArgumentException(name);
        };
    }

    private record RunningTask<T>(Thread thread, FutureTask<T> result) {}

    private static <T> RunningTask<T> start(Callable<T> action, List<RunningTask<?>> tasks) {
        FutureTask<T> result = new FutureTask<>(action);
        Thread thread = new Thread(result, "monitor-test-" + tasks.size());
        RunningTask<T> task = new RunningTask<>(thread, result);
        tasks.add(task);
        thread.start();
        return task;
    }

    private static void awaitWaiting(List<RunningTask<?>> tasks) throws InterruptedException {
        BooleanSupplier waiting = () -> tasks.stream()
                .allMatch(task -> task.thread.getState() == Thread.State.WAITING);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!waiting.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertTrue(waiting.getAsBoolean(), "Los hilos no llegaron a sus esperas");
    }

    private static void stop(List<RunningTask<?>> tasks) throws InterruptedException {
        for (RunningTask<?> task : tasks) {
            task.thread.interrupt();
        }
        for (RunningTask<?> task : tasks) {
            task.thread.join(3000);
            assertFalse(task.thread.isAlive(), "Quedo un hilo de prueba activo");
        }
    }

    private static final class PausingQueue extends ArrayDeque<Order> {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        public Order poll() {
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("La prueba no libero la cola");
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(error);
            }
            return super.poll();
        }
    }
}
