
package ar.edu.unc.fcefyn.pcp.tp1.solution;

import ar.edu.unc.fcefyn.pcp.tp1.api.OrderState;
import ar.edu.unc.fcefyn.pcp.tp1.api.OutcomeDecider;
import ar.edu.unc.fcefyn.pcp.tp1.api.SimulationConfig;

import java.io.IOException;
import java.io.UncheckedIOException;

public class ValidationWorker extends Worker {

    private final SimulationConfig config;
    private final PrinterManager printerManager;

    public ValidationWorker(
            WorkQueue inputQueue,
            WorkQueue outputQueue,
            EventLogger eventLogger,
            SimulationConfig config,
            PrinterManager printerManager
    ) {
        super(inputQueue, outputQueue, eventLogger, "VALIDATION");

        this.config = config;
        this.printerManager = printerManager;
    }

    @Override
    protected void processOrder(Order order) throws InterruptedException {
        Thread.sleep(config.validationDelayMillis());
        boolean esValida = OutcomeDecider.isModelValid(order.getId(), config);
        synchronized (order) {
            OrderState estadoAnterior = order.getState();
            order.onValidationResult(esValida);
            try {
                eventLogger.logEvent(
                        order.getId(),
                        EventLogger.Stage.VALIDATION,
                        EventLogger.EventType.ORDER_STATE_CHANGED,
                        estadoAnterior,
                        order.getState(),
                        order.getAssignedPrinterId()
                );
            } catch (IOException e) {
                throw new UncheckedIOException("Error al registrar la validación", e);
            }
        }
        if (esValida) {
            outputQueue.push(order);
        } else {
            Printer impresora = printerManager.getPrinterById(order.getAssignedPrinterId());
            if (impresora == null) {
                throw new IllegalStateException("No se encontró la impresora de la orden " + order.getId());
            }
            printerManager.releasePrinter(impresora, false);
        }
    }
}


