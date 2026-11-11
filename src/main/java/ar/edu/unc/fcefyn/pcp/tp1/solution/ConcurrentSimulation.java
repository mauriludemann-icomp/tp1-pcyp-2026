package ar.edu.unc.fcefyn.pcp.tp1.solution;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import ar.edu.unc.fcefyn.pcp.tp1.api.OrderSnapshot;
import ar.edu.unc.fcefyn.pcp.tp1.api.OrderState;
import ar.edu.unc.fcefyn.pcp.tp1.api.PrinterSnapshot;
import ar.edu.unc.fcefyn.pcp.tp1.api.PrinterState;
import ar.edu.unc.fcefyn.pcp.tp1.api.Simulation;
import ar.edu.unc.fcefyn.pcp.tp1.api.SimulationConfig;
import ar.edu.unc.fcefyn.pcp.tp1.api.SimulationResult;

/**
 * Coordina el arranque, la espera y el cierre de una ejecucion.
 * El estado compartido de las etapas pertenece a SimulationMonitor.
 */
public final class ConcurrentSimulation implements Simulation {

    public ConcurrentSimulation() {
    }

    @Override
    public SimulationResult execute(SimulationConfig config) throws InterruptedException {
        Objects.requireNonNull(config, "La configuracion no puede ser null");
        checkInterrupted();

        long startNanos = System.nanoTime();
        EventLogger logger = null;
        SimulationMonitor monitor = null;
        List<Thread> startedThreads = new ArrayList<>();
        Throwable failure = null;
        boolean interrupted = false;

        try {
            logger = new EventLogger(config.outputDirectory(), startNanos);
            monitor = new SimulationMonitor(config, logger);
            List<Thread> threads = createThreads(monitor, config);
            // Reservar capacidad antes del primer start para registrar cada arranque.
            startedThreads = new ArrayList<>(threads.size());

            for (Thread thread : threads) {
                checkInterrupted();
                failure = monitor.getFailure();
                if (failure != null) {
                    break;
                }
                thread.start();
                startedThreads.add(thread);
            }

            if (failure == null) {
                monitor.awaitCompletion();
            }
        } catch (InterruptedException error) {
            interrupted = true;
            failure = addFailure(failure, error);
        } catch (IOException | RuntimeException | Error error) {
            failure = addFailure(failure, error);
        }

        if (monitor != null) {
            failure = addFailure(failure, monitor.getFailure());
        }
        if (failure != null) {
            cancelAndInterrupt(monitor, startedThreads, failure);
        }

        // Fuera del monitor. Una interrupcion no permite abandonar los join pendientes.
        for (Thread thread : startedThreads) { //esperamos que terminen todos los hilos 
            boolean joined = false;
            while (!joined) {
                try {
                    thread.join();
                    joined = true;
                } catch (InterruptedException error) {
                    interrupted = true;
                    failure = addFailure(failure, error);
                    cancelAndInterrupt(monitor, startedThreads, failure);
                }
            }
        }

        List<OrderSnapshot> orders = List.of();
        List<PrinterSnapshot> printers = List.of();
        int processedOrders = 0;
        boolean allThreadsTerminated = true;

        try {
            if (monitor != null) {
                failure = addFailure(failure, monitor.getFailure());
            }
            checkInterrupted();
            if (failure == null) {
                for (Thread thread : startedThreads) {
                    if (thread.isAlive()) {
                        allThreadsTerminated = false;
                    }
                }
                processedOrders = monitor.getProcessedOrders();
                orders = monitor.getOrderSnapshots();
                printers = monitor.getPrinterSnapshots();
                verifyResult(config, processedOrders, orders, printers, allThreadsTerminated);
            }
        } catch (InterruptedException error) {
            interrupted = true;
            failure = addFailure(failure, error);
        } catch (RuntimeException | Error error) {
            failure = addFailure(failure, error);
        }

        // Ya no hay workers que puedan utilizar las reservas ni escribir eventos.
        try {
            if (failure != null && monitor != null) {
                monitor.cancel(failure);
                monitor.releaseReservationsAfterCancellation();
            }
        } catch (RuntimeException | Error error) {
            failure = addFailure(failure, error);
        } finally {
            if (logger != null) {
                try {
                    logger.close();
                } catch (IOException | RuntimeException | Error error) {
                    failure = addFailure(failure, error);
                }
            }
        }

        rethrowFailure(failure, interrupted);

        // Duracion hasta cerrar eventos, sin incluir la serializacion de resultados.
        long durationMillis = (System.nanoTime() - startNanos) / 1_000_000;
        Map<OrderState, Integer> totals = new EnumMap<>(OrderState.class);
        for (OrderState state : OrderState.values()) {
            totals.put(state, 0);
        }
        for (OrderSnapshot order : orders) {
            totals.put(order.state(), totals.get(order.state()) + 1);
        }
        SimulationResult result = new SimulationResult(config.totalOrders(), processedOrders,
                totals, orders, printers, durationMillis, allThreadsTerminated);

        try {
            new ResultWriter(config.outputDirectory()).write(result);
        } catch (IOException | RuntimeException | Error error) {
            failure = addFailure(failure, error);
        }
        rethrowFailure(failure, false);
        return result;
    }

    private static List<Thread> createThreads(SimulationMonitor monitor, SimulationConfig config) {
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < config.assignmentThreads(); i++) {
            threads.add(new Thread(new AssignmentWorker(monitor, config), "assignment-" + (i + 1)));
        }
        for (int i = 0; i < config.validationThreads(); i++) {
            threads.add(new Thread(new ValidationWorker(monitor, config), "validation-" + (i + 1)));
        }
        for (int i = 0; i < config.printingThreads(); i++) {
            threads.add(new Thread(new PrintingWorker(monitor, config), "printing-" + (i + 1)));
        }
        for (int i = 0; i < config.qualityControlThreads(); i++) {
            threads.add(new Thread(new QualityControlWorker(monitor, config), "quality-" + (i + 1)));
        }
        for (Thread thread : threads) {
            // Tambien comunicar fallos que escapen de los catch de un worker.
            thread.setUncaughtExceptionHandler((failedThread, error) -> monitor.cancel(error));
        }
        return threads;
    }

    private static void cancelAndInterrupt(SimulationMonitor monitor, List<Thread> threads,
                                           Throwable cause) {
        if (monitor != null) {
            monitor.cancel(cause);
        }
        for (Thread thread : threads) {
            thread.interrupt();
        }
    }

    private static void verifyResult(SimulationConfig config, int processedOrders,
                                     List<OrderSnapshot> orders, List<PrinterSnapshot> printers,
                                     boolean allThreadsTerminated) {
        if (!allThreadsTerminated || processedOrders != config.totalOrders()
                || orders.size() != config.totalOrders() || printers.size() != config.totalPrinters()) {
            throw new IllegalStateException("La simulacion no finalizo completamente");
        }

        Set<String> printerIds = new HashSet<>();
        long totalUses = 0;
        for (PrinterSnapshot printer : printers) {
            if (!printerIds.add(printer.id()) || printer.state() == PrinterState.RESERVED
                    || printer.assignedOrderId() != null || printer.usageCount() < 0) {
                throw new IllegalStateException("Impresora inconsistente al finalizar: " + printer.id());
            }
            totalUses += printer.usageCount();
        }

        Set<Integer> orderIds = new HashSet<>();
        long totalAssignments = 0;
        for (OrderSnapshot order : orders) {
            if (order.id() < 1 || order.id() > config.totalOrders() || !orderIds.add(order.id())
                    || !order.state().isTerminal() || !printerIds.contains(order.assignedPrinterId())) {
                throw new IllegalStateException("Orden inconsistente al finalizar: " + order.id());
            }
            int expectedPrinting = order.state() == OrderState.REJECTED ? 0 : 1;
            int expectedQuality = (order.state() == OrderState.APPROVED
                    || order.state() == OrderState.DEFECTIVE) ? 1 : 0;
            if (order.assignmentCount() != 1 || order.validationCount() != 1
                    || order.printingCount() != expectedPrinting
                    || order.qualityControlCount() != expectedQuality) {
                throw new IllegalStateException("Contadores inconsistentes en la orden " + order.id());
            }
            totalAssignments += order.assignmentCount();
        }
        if (totalAssignments != config.totalOrders() || totalUses != totalAssignments) {
            throw new IllegalStateException("Los usos de impresoras no coinciden con las asignaciones");
        }
    }

    // Conserva la primera causa y adjunta las posteriores sin duplicar la misma instancia.
    private static Throwable addFailure(Throwable first, Throwable next) {
        if (first == null) {
            return next;
        }
        if (next != null && next != first) {
            for (Throwable suppressed : first.getSuppressed()) {
                if (suppressed == next) {
                    return first;
                }
            }
            first.addSuppressed(next);
        }
        return first;
    }

    private static void checkInterrupted() throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException("Se interrumpio el hilo coordinador");
        }
    }

    private static void rethrowFailure(Throwable failure, boolean interrupted) throws InterruptedException {
        if (Thread.interrupted()) {
            interrupted = true;
            failure = addFailure(failure, new InterruptedException("Se interrumpio el hilo coordinador"));
        }
        if (failure instanceof InterruptedException error) {
            throw error;
        }
        if (interrupted) {
            InterruptedException error = new InterruptedException("Se interrumpio el hilo coordinador");
            error.initCause(failure);
            throw error;
        }
        if (failure instanceof IOException error) {
            throw new UncheckedIOException("No se pudieron escribir los archivos de la simulacion", error);
        }
        if (failure instanceof RuntimeException error) {
            throw error;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure != null) {
            throw new IllegalStateException("Fallo la simulacion", failure);
        }
    }
}

