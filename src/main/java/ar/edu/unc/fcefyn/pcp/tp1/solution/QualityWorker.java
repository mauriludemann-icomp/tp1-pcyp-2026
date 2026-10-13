package ar.edu.unc.fcefyn.pcp.tp1.solution;

import ar.edu.unc.fcefyn.pcp.tp1.api.OrderState;
import ar.edu.unc.fcefyn.pcp.tp1.api.OutcomeDecider;
import ar.edu.unc.fcefyn.pcp.tp1.api.SimulationConfig;

import java.io.IOException;
import java.io.UncheckedIOException;

public class QualityWorker extends Worker {

    private final SimulationConfig config;

    public QualityWorker(
            WorkQueue inputQueue,
            EventLogger eventLogger,
            SimulationConfig config
    ) {
        // Pasamos null a outputQueue porque es la etapa terminal
        super(inputQueue, null, eventLogger, "QUALITY_CONTROL");
        this.config = config;
    }

    @Override
    protected void processOrder(Order order) throws InterruptedException {
        // Demora propia de la etapa de control de calidad
        Thread.sleep(config.qualityControlDelayMillis());

        // Evaluo calidad
        boolean isApproved = OutcomeDecider.isQualityApproved(order.getId(), config);

        // Modifico estado
        synchronized (eventLogger) {
            synchronized (order) {
                OrderState fromState = order.getState(); // Debería ser PRINTED
                order.onQualityResult(isApproved);      // Pasa a APPROVED o DEFECTIVE

                try {
                    eventLogger.logEvent(
                            order.getId(),
                            EventLogger.Stage.QUALITY_CONTROL,
                            EventLogger.EventType.ORDER_STATE_CHANGED,
                            fromState,
                            order.getState(),
                            order.getAssignedPrinterId() // Guarda la impresora histórica
                    );
                } catch (IOException e) {
                    throw new UncheckedIOException("Error checking order quality " + order.getId(), e);
                }
            }
        }

    }
}
