package ar.edu.unc.fcefyn.pcp.tp1.solution;

import java.io.IOException;
import java.io.UncheckedIOException;

import ar.edu.unc.fcefyn.pcp.tp1.api.OrderState;
import ar.edu.unc.fcefyn.pcp.tp1.api.OutcomeDecider;
import ar.edu.unc.fcefyn.pcp.tp1.api.SimulationConfig;

public class PrintingWorker extends Worker {
    private final SimulationConfig config;
    private final PrinterManager printerManager;

    public PrintingWorker(
            WorkQueue inputQueue,
            WorkQueue outputQueue,
            EventLogger eventLogger,
            SimulationConfig config,
            PrinterManager printerManager) {
        super(inputQueue, outputQueue, eventLogger, "PRINTING");
        this.config = config;
        this.printerManager = printerManager;
    }

    @Override
    protected void processOrder(Order order) throws InterruptedException {
        Printer impresora = printerManager.getPrinterById(order.getAssignedPrinterId());
        if (impresora == null) {
            throw new IllegalStateException(
                    "No se encontró la impresora asignada a la orden " + order.getId()
            );
        }

        boolean impresoraGestionada = false;
        boolean successful = false;
        try {
            Thread.sleep(config.printingDelayMillis());
            successful = OutcomeDecider.isPrintSuccessful(order.getId(), config);

            synchronized (eventLogger) {
                synchronized (order) {
                    OrderState estadoAnterior = order.getState();
                    order.onPrintResult(successful);
                    try {
                        eventLogger.logEvent(
                                order.getId(),
                                EventLogger.Stage.PRINTING,
                                EventLogger.EventType.ORDER_STATE_CHANGED,
                                estadoAnterior,
                                order.getState(),
                                order.getAssignedPrinterId()
                        );
                    } catch (IOException e) {
                        throw new UncheckedIOException(
                                "Error al registrar la impresión", e
                        );
                    }
                }
            }

            printerManager.releasePrinter(impresora, !successful);
            impresoraGestionada = true;

            if (successful) {
                outputQueue.push(order);
            }

        } finally {
            if (!impresoraGestionada) {
                printerManager.releasePrinter(impresora, false);
            }
        }
    }
}
