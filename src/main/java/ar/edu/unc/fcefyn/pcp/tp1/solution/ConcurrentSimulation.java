
package ar.edu.unc.fcefyn.pcp.tp1.solution;

import ar.edu.unc.fcefyn.pcp.tp1.api.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;

/**
 * Punto de entrada obligatorio para la implementacion del grupo.
 */
public final class ConcurrentSimulation implements Simulation {

    @Override
    public SimulationResult execute(SimulationConfig config)
            throws InterruptedException {

        long startTimeNanos = System.nanoTime();

        try (EventLogger eventLogger =
                     new EventLogger(config.outputDirectory())) {

            OrderManager orderManager = new OrderManager();
            PrinterManager printerManager = new PrinterManager(
                    config.printerRows(),
                    config.printerColumns()
            );

            // Creacion de las colas
            WorkQueue ordersCreated = new WorkQueue();
            WorkQueue ordersWaitingValidation = new WorkQueue();
            WorkQueue ordersReadyToPrint = new WorkQueue();
            WorkQueue ordersPrinted = new WorkQueue();

            List<WorkQueue> queues = new ArrayList<>();
            queues.add(ordersCreated);
            queues.add(ordersWaitingValidation);
            queues.add(ordersReadyToPrint);
            queues.add(ordersPrinted);

            // Creacion de ordenes
            OrderFactory orderFactory = new OrderFactory();
            List<Order> ordersList =
                    orderFactory.createOrders(config.totalOrders());

            for (Order order : ordersList) {
                orderManager.addOrder(order);
            }

            // Creacion de hilos de asignacion
            List<Thread> assignment = new ArrayList<>();

            for (int i = 1; i <= config.assignmentThreads(); i++) {
                PrinterAssignmentWorker worker =
                        new PrinterAssignmentWorker(
                                ordersCreated,
                                ordersWaitingValidation,
                                eventLogger,
                                config,
                                printerManager
                        );

                assignment.add(new Thread(worker, "Assignment " + i));
            }

            // Creacion de hilos de validacion
            List<Thread> validation = new ArrayList<>();

            for (int i = 1; i <= config.validationThreads(); i++) {
                ValidationWorker worker = new ValidationWorker(
                        ordersWaitingValidation,
                        ordersReadyToPrint,
                        eventLogger,
                        config,
                        printerManager
                );

                validation.add(new Thread(worker, "Validation " + i));
            }

            // Creacion de hilos de impresion
            List<Thread> printing = new ArrayList<>();

            for (int i = 1; i <= config.printingThreads(); i++) {
                PrintingWorker worker = new PrintingWorker(
                        ordersReadyToPrint,
                        ordersPrinted,
                        eventLogger,
                        config,
                        printerManager
                );

                printing.add(new Thread(worker, "Printing " + i));
            }

            // Creacion de hilos de control de calidad
            List<Thread> qualityControl = new ArrayList<>();

            for (int i = 1; i <= config.qualityControlThreads(); i++) {
                QualityWorker worker = new QualityWorker(
                        ordersPrinted,
                        eventLogger,
                        config
                );

                qualityControl.add(
                        new Thread(worker, "Quality Control " + i)
                );
            }

            // Agrupacion de hilos por etapa
            List<List<Thread>> threads = new ArrayList<>();
            threads.add(assignment);
            threads.add(validation);
            threads.add(printing);
            threads.add(qualityControl);

            // Creacion del coordinador
            PipelineCoordinator pipelineCoordinator =
                    new PipelineCoordinator(threads, queues);

            boolean simulationCompleted = false;

            try {
                // Iniciar todas las etapas concurrentemente
                pipelineCoordinator.startAllWorkers();

                // Ingresar las ordenes al pipeline
                for (Order order : ordersList) {
                    ordersCreated.push(order);
                }

                ordersCreated.close();

                // Esperar cada etapa y cerrar su cola de salida
                pipelineCoordinator.awaitStageAndCloseOutput(
                        0, ordersWaitingValidation
                );

                pipelineCoordinator.awaitStageAndCloseOutput(
                        1, ordersReadyToPrint
                );

                pipelineCoordinator.awaitStageAndCloseOutput(
                        2, ordersPrinted
                );

                // Esperar el control de calidad
                pipelineCoordinator.awaitStage(3);

                // Verificar que no queden impresoras reservadas
                if (!printerManager.allPrintersReleased()) {
                    throw new IllegalStateException(
                            "La simulacion termino con impresoras reservadas"
                    );
                }

                // Obtener estados finales de las ordenes
                List<OrderSnapshot> orderSnapshots =
                        orderManager.getAllSnapshots()
                                .values()
                                .stream()
                                .sorted(Comparator.comparingInt(
                                        OrderSnapshot::id
                                ))
                                .toList();

                // Obtener estados finales de las impresoras
                List<PrinterSnapshot> printerSnapshots =
                        printerManager.getSnapshots();

                // Contar ordenes por estado
                Map<OrderState, Integer> totals =
                        new EnumMap<>(OrderState.class);

                for (OrderState state : OrderState.values()) {
                    totals.put(state, 0);
                }

                int processedOrders = 0;

                for (OrderSnapshot order : orderSnapshots) {
                    totals.merge(order.state(), 1, Integer::sum);

                    if (order.state().isTerminal()) {
                        processedOrders++;
                    }
                }

                // Comprobar finalizacion completa
                boolean allThreadsTerminated = threads.stream()
                        .flatMap(List::stream)
                        .noneMatch(Thread::isAlive);

                if (orderSnapshots.size() != config.totalOrders()
                        || processedOrders != config.totalOrders()
                        || !allThreadsTerminated) {

                    throw new IllegalStateException(
                            "La simulacion termino incompleta"
                    );
                }

                // Calcular duracion
                long durationMillis =
                        (System.nanoTime() - startTimeNanos) / 1_000_000L;

                // Construir resultado
                SimulationResult result = new SimulationResult(
                        config.totalOrders(),
                        processedOrders,
                        totals,
                        orderSnapshots,
                        printerSnapshots,
                        durationMillis,
                        allThreadsTerminated
                );

                // Generar archivos de salida
                new ResultWriter(config.outputDirectory()).write(result);

                simulationCompleted = true;
                return result;

            } finally {
                // Ante error o interrupcion, finalizar todos los workers
                if (!simulationCompleted) {
                    pipelineCoordinator.cancelAndAwaitTermination();
                }
            }

        } catch (IOException e) {
            throw new UncheckedIOException(
                    "No se pudieron escribir los archivos", e
            );
        }
    }
}
