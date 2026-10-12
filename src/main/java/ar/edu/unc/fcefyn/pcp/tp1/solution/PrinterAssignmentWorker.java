package ar.edu.unc.fcefyn.pcp.tp1.solution;

import java.io.IOException;
import java.io.UncheckedIOException;

import ar.edu.unc.fcefyn.pcp.tp1.api.OrderState;
import ar.edu.unc.fcefyn.pcp.tp1.api.OutcomeDecider;
import ar.edu.unc.fcefyn.pcp.tp1.api.SimulationConfig;

public class PrinterAssignmentWorker extends Worker {
    
    private final PrinterManager printerManager;
    private final SimulationConfig config;

    public PrinterAssignmentWorker(
        WorkQueue inputQueue,
        WorkQueue outputQueue,
        EventLogger eventLogger,
        SimulationConfig config,
        PrinterManager printerManager) {
    super(inputQueue, outputQueue, eventLogger, "ASSIGNMENT");
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
        try {
            Thread.sleep(config.printingDelayMillis());
            boolean succes = OutcomeDecider.isPrintSuccessful(order.getId(), config);
            synchronized (order) {
                OrderState estadoAnterior = order.getState();
                order.onPrintResult(succes);
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
            printerManager.releasePrinter(impresora, !succes);
            impresoraGestionada = true;
            if (succes) {
                outputQueue.push(order);
            }
        } finally {
            if (!impresoraGestionada) {
                printerManager.releasePrinter(impresora, false);
            }
        }
    }
}
