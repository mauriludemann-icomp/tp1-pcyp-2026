package ar.edu.unc.fcefyn.pcp.tp1.solution;

import ar.edu.unc.fcefyn.pcp.tp1.api.OrderSnapshot;
import ar.edu.unc.fcefyn.pcp.tp1.api.OrderState;
import ar.edu.unc.fcefyn.pcp.tp1.api.Simulation;
import ar.edu.unc.fcefyn.pcp.tp1.api.SimulationConfig;
import ar.edu.unc.fcefyn.pcp.tp1.api.SimulationResult;
import ar.edu.unc.fcefyn.pcp.tp1.solution.log.EventLog;
import ar.edu.unc.fcefyn.pcp.tp1.solution.model.Order;
import ar.edu.unc.fcefyn.pcp.tp1.solution.monitor.OrderQueue;
import ar.edu.unc.fcefyn.pcp.tp1.solution.monitor.PrinterFarm;
import ar.edu.unc.fcefyn.pcp.tp1.solution.output.ResultWriter;
import ar.edu.unc.fcefyn.pcp.tp1.solution.workers.AssignmentWorker;
import ar.edu.unc.fcefyn.pcp.tp1.solution.workers.PrintingWorker;
import ar.edu.unc.fcefyn.pcp.tp1.solution.workers.QualityControlWorker;
import ar.edu.unc.fcefyn.pcp.tp1.solution.workers.ValidationWorker;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Pipeline de cuatro etapas: asignacion -> validacion -> impresion -> control de calidad.
 * Cada etapa tiene su pool de hilos y las etapas se conectan con colas (OrderQueue).
 *
 * Terminacion: main encola todas las ordenes y cierra la primera cola. Despues hace join
 * de cada etapa y, recien cuando todos sus hilos terminaron, cierra la cola de la
 * siguiente. Un worker termina cuando take() devuelve null (cola cerrada y vacia).
 */
public final class ConcurrentSimulation implements Simulation {

    @Override
    public SimulationResult execute(SimulationConfig config) throws InterruptedException {
        long startNanos = System.nanoTime();
        EventLog log = new EventLog(startNanos);
        PrinterFarm farm = new PrinterFarm(config);

        OrderQueue createdQueue = new OrderQueue();
        OrderQueue validationQueue = new OrderQueue();
        OrderQueue printingQueue = new OrderQueue();
        OrderQueue qualityQueue = new OrderQueue();

        // Se crean todas las ordenes, se registran y se encolan en la primera etapa.
        List<Order> orders = new ArrayList<>(config.totalOrders());
        for (int id = 1; id <= config.totalOrders(); id++) {
            Order order = new Order(id);
            orders.add(order);
            log.created(order);
            createdQueue.put(order);
        }
        createdQueue.close(); // no habra mas ordenes nuevas

        // Si un worker muere por una excepcion, se guarda aca y se relanza despues de los join.
        FailureHolder failure = new FailureHolder();

        List<Thread> assignment = createThreads("assignment", config.assignmentThreads(), failure,
                () -> new AssignmentWorker(createdQueue, validationQueue, farm, log, config));
        List<Thread> validation = createThreads("validation", config.validationThreads(), failure,
                () -> new ValidationWorker(validationQueue, printingQueue, farm, log, config));
        List<Thread> printing = createThreads("printing", config.printingThreads(), failure,
                () -> new PrintingWorker(printingQueue, qualityQueue, farm, log, config));
        List<Thread> quality = createThreads("quality-control", config.qualityControlThreads(), failure,
                () -> new QualityControlWorker(qualityQueue, log, config));

        List<Thread> allThreads = new ArrayList<>();
        allThreads.addAll(assignment);
        allThreads.addAll(validation);
        allThreads.addAll(printing);
        allThreads.addAll(quality);

        try {
            // Todos los hilos arrancan juntos: los de las etapas siguientes quedan bloqueados en take().
            for (Thread thread : allThreads) {
                thread.start();
            }

            joinAll(assignment);
            validationQueue.close();
            joinAll(validation);
            printingQueue.close();
            joinAll(printing);
            qualityQueue.close();
            joinAll(quality);
        } catch (InterruptedException interrupted) {
            // Si interrumpen a main, se interrumpe a los workers para que no queden colgados.
            for (Thread thread : allThreads) {
                thread.interrupt();
            }
            throw interrupted;
        }

        Throwable error = failure.get();
        if (error != null) {
            throw new IllegalStateException("Un worker termino con una excepcion", error);
        }

        boolean allThreadsTerminated = true;
        for (Thread thread : allThreads) {
            if (thread.isAlive()) {
                allThreadsTerminated = false;
            }
        }

        long durationMillis = (System.nanoTime() - startNanos) / 1_000_000;
        SimulationResult result = buildResult(config, orders, farm, durationMillis, allThreadsTerminated);

        try {
            ResultWriter.write(config.outputDirectory(), log.lines(), result);
        } catch (IOException exception) {
            throw new UncheckedIOException("No se pudieron escribir los resultados", exception);
        }
        return result;
    }

    private List<Thread> createThreads(String prefix, int count, FailureHolder failure,
                                       Supplier<Runnable> workerFactory) {
        List<Thread> threads = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            Thread thread = new Thread(workerFactory.get(), prefix + "-" + i);
            thread.setUncaughtExceptionHandler((t, e) -> failure.set(e));
            threads.add(thread);
        }
        return threads;
    }

    private void joinAll(List<Thread> threads) throws InterruptedException {
        for (Thread thread : threads) {
            thread.join();
        }
    }

    private SimulationResult buildResult(SimulationConfig config, List<Order> orders, PrinterFarm farm,
                                         long durationMillis, boolean allThreadsTerminated) {
        List<OrderSnapshot> orderSnapshots = new ArrayList<>(orders.size());
        Map<OrderState, Integer> totals = new EnumMap<>(OrderState.class);
        for (OrderState state : OrderState.values()) {
            totals.put(state, 0);
        }
        int processed = 0;
        for (Order order : orders) {
            OrderSnapshot snapshot = order.toSnapshot();
            orderSnapshots.add(snapshot);
            totals.merge(snapshot.state(), 1, Integer::sum);
            if (snapshot.state().isTerminal()) {
                processed++;
            }
        }

        return new SimulationResult(config.totalOrders(), processed, totals,
                orderSnapshots, farm.snapshots(), durationMillis, allThreadsTerminated);
    }

    /** Guarda la primera excepcion no atrapada de un worker. Monitor propio, sin clases atomicas. */
    private static final class FailureHolder {
        private Throwable failure;

        synchronized void set(Throwable throwable) {
            if (failure == null) {
                failure = throwable;
            }
        }

        synchronized Throwable get() {
            return failure;
        }
    }
}