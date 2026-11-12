package ar.edu.unc.fcefyn.pcp.tp1.solution;

import ar.edu.unc.fcefyn.pcp.tp1.api.OutcomeDecider;
import ar.edu.unc.fcefyn.pcp.tp1.api.SimulationConfig;

public final class QualityControlWorker implements Runnable {

    private final SimulationMonitor monitor;
    private final SimulationConfig config;

    public QualityControlWorker(SimulationMonitor monitor, SimulationConfig config) {
        this.monitor = monitor;
        this.config = config;
    }

    @Override
    public void run() {
        try {
            while (true) {
                // Espera dentro del monitor (wait) hasta que haya una orden en PRINTED
                Order order = monitor.takeForQualityControl();             
                  if (order == null) {
                    return; 
                }


                sleep(config.qualityControlDelayMillis());

                // Función determinista, no necesita lock.
                boolean approved = OutcomeDecider.isQualityApproved(order.getId(), config);

            monitor.completeQualityControl(order, approved);
            }
            } catch (InterruptedException e) {
            monitor.cancel(e);
            Thread.currentThread().interrupt();
        } catch (RuntimeException error) {
            
            monitor.cancel(error);
        }
    }

    private static void sleep(long millis) throws InterruptedException {
        if (millis > 0) {
            Thread.sleep(millis);
        }
    }
}