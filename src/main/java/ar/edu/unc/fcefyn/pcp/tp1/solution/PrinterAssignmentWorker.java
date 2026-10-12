
package ar.edu.unc.fcefyn.pcp.tp1.solution;

import ar.edu.unc.fcefyn.pcp.tp1.api.OrderState;
import ar.edu.unc.fcefyn.pcp.tp1.api.SimulationConfig;

import java.io.IOException;
import java.io.UncheckedIOException;

public class PrinterAssignmentWorker extends Worker {

    private final PrinterManager printerManager;
    private final SimulationConfig config;

    public PrinterAssignmentWorker(
            WorkQueue inputQueue,
            WorkQueue outputQueue,
            EventLogger eventLogger,
            SimulationConfig config,
            PrinterManager printerManager
    ) {
        super(inputQueue, outputQueue, eventLogger, "ASSIGNMENT");
        this.config = config;
        this.printerManager = printerManager;
    }

    @Override
    protected void processOrder(Order order) throws InterruptedException {

        Thread.sleep(config.assignmentDelayMillis());

        Printer impresora =
                printerManager.reserveAvailablePrinter(order.getId());

        boolean entregada = false;

        try {
            synchronized (order) {
                OrderState estadoAnterior = order.getState();

                order.onAssigned(impresora.getId());

                try {
                    eventLogger.logEvent(
                            order.getId(),
                            EventLogger.Stage.ASSIGNMENT,
                            EventLogger.EventType.ORDER_STATE_CHANGED,
                            estadoAnterior,
                            order.getState(),
                            order.getAssignedPrinterId()
                    );
                } catch (IOException e) {
                    throw new UncheckedIOException(
                            "Error al registrar la asignación", e
                    );
                }
            }

            outputQueue.push(order);
            entregada = true;

        } finally {
            if (!entregada) {
                printerManager.releasePrinter(impresora, false);
            }
        }
    }
}

