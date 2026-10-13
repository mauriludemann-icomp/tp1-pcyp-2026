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
    public SimulationResult execute(SimulationConfig config) throws InterruptedException {

        long startTimeNanos = System.nanoTime();

        try (EventLogger eventLogger = new EventLogger(config.outputDirectory())) {
            OrderManager orderManager = new OrderManager();
            PrinterManager printerManager = new PrinterManager(config.printerRows(), config.printerColumns());

            /** Creacion de Queues*/
            WorkQueue ordersCreated = new WorkQueue();
            WorkQueue ordersWaitingValidation = new WorkQueue();
            WorkQueue ordersReadyToPrint = new WorkQueue();
            WorkQueue ordersPrinted = new WorkQueue();
            List<WorkQueue> queues = new ArrayList<>();
            queues.add(ordersCreated);
            queues.add(ordersWaitingValidation);
            queues.add(ordersReadyToPrint);
            queues.add(ordersPrinted);

            /** Creacion de ordenes */
            OrderFactory orderFactory = new OrderFactory();
            List<Order> ordersList = orderFactory.createOrders(config.totalOrders());
            for (Order order : ordersList) {
                orderManager.addOrder(order);
            }

            /** Creacion de hilos*/
            List<Thread> assignment =  new ArrayList<>();
            for (int i = 1; i <= config.assignmentThreads(); i++){
                PrinterAssignmentWorker printerAssignmentWorker = new PrinterAssignmentWorker(
                        ordersCreated,
                        ordersWaitingValidation,
                        eventLogger,
                        config,
                        printerManager
                );
                Thread thread = new Thread(printerAssignmentWorker, "Assignment " + i);
                assignment.add(thread);
            }

            List<Thread> validation =  new ArrayList<>();
            for (int i = 1; i <= config.validationThreads(); i++){
                ValidationWorker validationWorker = new ValidationWorker(
                        ordersWaitingValidation,
                        ordersReadyToPrint,
                        eventLogger,
                        config,
                        printerManager
                );
                Thread thread = new Thread(validationWorker, "Validation " + i);
                validation.add(thread);
            }

            List<Thread> printing =  new ArrayList<>();
            for  (int i = 1; i <= config.printingThreads(); i++){
                PrintingWorker printingWorker = new PrintingWorker(
                        ordersReadyToPrint,
                        ordersPrinted,
                        eventLogger,
                        config,
                        printerManager
                );
                Thread thread = new Thread(printingWorker, "Printing " + i);
                printing.add(thread);
            }

            List<Thread> qualityControl =  new ArrayList<>();
            for  (int i = 1; i <= config.qualityControlThreads(); i++){
                QualityWorker qualityWorker = new QualityWorker(
                        ordersPrinted,
                        eventLogger,
                        config
                );
                Thread thread = new Thread(qualityWorker, "Quality Control " + i);
                qualityControl.add(thread);
            }

            List<List<Thread>> threads =  new ArrayList<>();
            threads.add(assignment);
            threads.add(validation);
            threads.add(printing);
            threads.add(qualityControl);

            /** Iniciar el pipeline */
            PipelineCoordinator pipelineCoordinator = new PipelineCoordinator(threads, queues);
            pipelineCoordinator.startAllWorkers();

            /** Ingresar órdenes en la simulación */
            for (Order order:  ordersList) {
                ordersCreated.push(order);
            }
            ordersCreated.close();

            /** Propagar el cierre */
            pipelineCoordinator.awaitStageAndCloseOutput(0,ordersWaitingValidation);
            pipelineCoordinator.awaitStageAndCloseOutput(1,ordersReadyToPrint);
            pipelineCoordinator.awaitStageAndCloseOutput(2,ordersPrinted);
            pipelineCoordinator.awaitStage(3);

            /** Verificacion del estado de todas las impresoras*/
            if (!printerManager.allPrintersReleased()){
                throw new InterruptedException("Printers are not released");
            }

            /** Construir el resultado*/
            List<OrderSnapshot> orderSnapshots = orderManager.getAllSnapshots().values().stream()
                    .sorted(Comparator.comparingInt(OrderSnapshot::id))
                    .toList();
            List<PrinterSnapshot> printerSnapshots = printerManager.getSnapshots();

            Map<OrderState, Integer> totals = new EnumMap<>(OrderState.class);
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

            boolean allThreadsTerminated = threads.stream()
                    .flatMap(List::stream)
                    .noneMatch(Thread::isAlive);

            if (orderSnapshots.size() != config.totalOrders()
                    || processedOrders != config.totalOrders()
                    || !allThreadsTerminated) {
                throw new IllegalStateException("La simulación terminó incompleta");
            }

            long durationMillis = (System.nanoTime() - startTimeNanos) / 1_000_000L;
            SimulationResult result = new SimulationResult(
                    config.totalOrders(),
                    processedOrders,
                    totals,
                    orderSnapshots,
                    printerSnapshots,
                    durationMillis,
                    allThreadsTerminated
            );

            new ResultWriter(config.outputDirectory()).write(result);
            return result;
        }
        catch (IOException e) {
            throw new UncheckedIOException("No se pudieron escribir los archivos", e);
        }

    }
}

