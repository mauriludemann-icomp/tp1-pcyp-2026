package ar.edu.unc.fcefyn.pcp.tp1.solution;

import ar.edu.unc.fcefyn.pcp.tp1.api.OrderState;
import ar.edu.unc.fcefyn.pcp.tp1.api.PrinterState;
import ar.edu.unc.fcefyn.pcp.tp1.api.SimulationConfig;
import ar.edu.unc.fcefyn.pcp.tp1.api.SimulationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
class ConcurrentSimulationTest {
    @TempDir
    Path directory;

    // Detecta etapas omitidas, contadores incorrectos y resultados sin archivos completos.
    @ParameterizedTest
    @CsvSource({"0,100,100,REJECTED,0,0", "100,0,100,PRINT_FAILED,1,0",
            "100,100,0,DEFECTIVE,1,1", "100,100,100,APPROVED,1,1"})
    void completesEveryTerminalRoute(int valid, int print, int quality, OrderState state,
                                    int printingCount, int qualityCount) throws Exception {
        SimulationConfig config = config(directory, valid, print, quality, 0);
        SimulationResult result = new ConcurrentSimulation().execute(config);
        assertEquals(12, result.processedOrders());
        assertTrue(result.allThreadsTerminated());
        assertEquals(12, result.totalsByState().get(state));
        for (var order : result.orders()) {
            assertEquals(state, order.state());
            assertEquals(1, order.assignmentCount());
            assertEquals(1, order.validationCount());
            assertEquals(printingCount, order.printingCount());
            assertEquals(qualityCount, order.qualityControlCount());
            assertNotNull(order.assignedPrinterId());
        }
        assertEquals(12, result.printers().stream().mapToInt(p -> p.usageCount()).sum());
        for (var printer : result.printers()) {
            assertNotEquals(PrinterState.RESERVED, printer.state());
            assertNull(printer.assignedOrderId());
        }
        List<String> events = Files.readAllLines(directory.resolve("eventos.csv"));
        assertEquals(1 + 12 * (3 + printingCount + qualityCount), events.size());
        for (int i = 1; i <= 12; i++) {
            String[] fields = events.get(i).split(";", -1);
            assertEquals("INITIALIZATION", fields[4]);
            assertEquals("ORDER_CREATED", fields[5]);
            assertEquals("", fields[6]);
            assertEquals("CREATED", fields[7]);
            assertEquals("", fields[8]);
        }
        List<String> orders = Files.readAllLines(directory.resolve("elementos.csv"));
        assertEquals(13, orders.size());
        for (int i = 1; i <= 12; i++) {
            assertEquals(Integer.toString(i), orders.get(i).split(";", -1)[0]);
        }
        Properties summary = new Properties();
        try (var reader = Files.newBufferedReader(directory.resolve("resumen.properties"))) {
            summary.load(reader);
        }
        assertEquals("12", summary.getProperty("processedOrders"));
        assertEquals("0", summary.getProperty("remainingIntermediateOrders"));
        assertEquals("true", summary.getProperty("allThreadsTerminated"));
        assertEquals(Long.toString(result.durationMillis()), summary.getProperty("durationMillis"));
        assertEquals(state == OrderState.APPROVED ? "12" : "0", summary.getProperty("approvedOrders"));
        assertEquals(state == OrderState.REJECTED ? "12" : "0", summary.getProperty("rejectedOrders"));
        assertEquals(state == OrderState.PRINT_FAILED ? "12" : "0", summary.getProperty("printFailedOrders"));
        assertEquals(state == OrderState.DEFECTIVE ? "12" : "0", summary.getProperty("defectiveOrders"));
        assertClosedOutputs(directory);
    }

    @Test
    void sameInstanceCanExecuteTwiceWithIndependentResults() throws Exception {
        ConcurrentSimulation simulation = new ConcurrentSimulation();
        SimulationResult first = simulation.execute(config(directory.resolve("first"), 0, 100, 100, 0));
        SimulationResult second = simulation.execute(config(directory.resolve("second"), 100, 100, 100, 0));
        assertEquals(12, first.totalsByState().get(OrderState.REJECTED));
        assertEquals(12, second.totalsByState().get(OrderState.APPROVED));
        assertEquals("1", Files.readAllLines(directory.resolve("second/eventos.csv")).get(1).split(";")[0]);
    }

    // Detecta arranque por etapas, cantidades fijas y retorno sin cancelar/unir a todos.
    @Test
    void callerInterruptionStopsAllConfiguredWorkersBeforePropagating() throws Exception {
        assertCancellation(false);
    }

    @Test
    void workerInterruptionIsReportedAndStopsTheOtherWorkers() throws Exception {
        assertCancellation(true);
    }

    @Test
    void alreadyInterruptedCallerDoesNotReturnSuccess() throws Exception {
        Throwable[] failure = new Throwable[1];
        Thread caller = new Thread(() -> {
            Thread.currentThread().interrupt();
            try {
                new ConcurrentSimulation().execute(config(directory, 100, 100, 100, 0));
            } catch (Throwable error) {
                failure[0] = error;
            }
        });
        caller.start();
        caller.join(3000);
        assertFalse(caller.isAlive());
        assertInstanceOf(InterruptedException.class, failure[0]);
        assertFalse(Files.exists(directory.resolve("resumen.properties")));
    }

    @Test
    void loggerCreationFailureIsPropagatedAsIoFailure() throws Exception {
        Files.createDirectory(directory.resolve("eventos.csv"));
        assertThrows(UncheckedIOException.class,
                () -> new ConcurrentSimulation().execute(config(directory, 100, 100, 100, 0)));
        assertFalse(Files.exists(directory.resolve("elementos.csv")));
    }

    @ParameterizedTest
    @CsvSource({"elementos.csv", "resumen.properties"})
    void finalFileFailureDoesNotReturnSuccessAndClosesEarlierFiles(String blockedFile) throws Exception {
        Files.createDirectory(directory.resolve(blockedFile));
        assertThrows(UncheckedIOException.class,
                () -> new ConcurrentSimulation().execute(config(directory, 100, 100, 100, 0)));
        assertClosedOutputs(directory);
    }

    private void assertCancellation(boolean interruptWorker) throws Exception {
        ThreadGroup group = new ThreadGroup("simulation-cancellation-test");
        Throwable[] failure = new Throwable[1];
        Thread caller = new Thread(group, () -> {
            try {
                new ConcurrentSimulation().execute(config(directory, 100, 100, 100, 60_000));
            } catch (Throwable error) {
                failure[0] = error;
            }
        }, "simulation-caller");
        caller.start();
        try {
            await(() -> !caller.isAlive() || (threads(group).size() == 9
                    && caller.getState() == Thread.State.WAITING));
            assertTrue(caller.isAlive(), () -> "El coordinador termino prematuramente: " + failure[0]);
            List<Thread> workers = threads(group).stream().filter(t -> t != caller).toList();
            assertEquals(3, workers.stream().filter(t -> t.getName().startsWith("assignment-")).count());
            assertEquals(1, workers.stream().filter(t -> t.getName().startsWith("validation-")).count());
            assertEquals(2, workers.stream().filter(t -> t.getName().startsWith("printing-")).count());
            assertEquals(2, workers.stream().filter(t -> t.getName().startsWith("quality-")).count());
            if (interruptWorker) {
                workers.stream().filter(t -> t.getName().equals("assignment-1")).findFirst().orElseThrow().interrupt();
            } else {
                caller.interrupt();
            }
            caller.join(3000);
            assertFalse(caller.isAlive(), "El coordinador no termino tras cancelar");
            assertInstanceOf(InterruptedException.class, failure[0]);
            assertTrue(workers.stream().noneMatch(Thread::isAlive), "Quedaron workers vivos");
            assertFalse(Files.exists(directory.resolve("resumen.properties")));
            assertClosedOutputs(directory);
        } finally {
            for (Thread thread : threads(group)) {
                thread.interrupt();
            }
            caller.join(3000);
        }
    }

    private static SimulationConfig config(Path output, int valid, int print, int quality, long delay) {
        return new SimulationConfig(12, 1, 13, 3, 1, 2, 2, valid, print, quality,
                delay, delay, delay, delay, 42L, output);
    }

    private static List<Thread> threads(ThreadGroup group) {
        Thread[] buffer = new Thread[group.activeCount() * 2 + 16];
        int count = group.enumerate(buffer, false);
        List<Thread> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (buffer[i].isAlive()) {
                result.add(buffer[i]);
            }
        }
        return result;
    }

    // Solo observa el estado del test; no sincroniza las etapas de produccion.
    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + 3_000_000_000L;
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertTrue(condition.getAsBoolean(), "No se alcanzo el estado esperado");
    }

    private static void assertClosedOutputs(Path output) throws Exception {
        for (String name : List.of("eventos.csv", "elementos.csv", "resumen.properties")) {
            Path file = output.resolve(name);
            if (Files.isRegularFile(file)) {
                // En Windows detecta los handles abiertos que impedirian mover el archivo.
                Path moved = file.resolveSibling(name + ".closed");
                Files.move(file, moved);
                Files.move(moved, file);
            }
        }
    }
}
