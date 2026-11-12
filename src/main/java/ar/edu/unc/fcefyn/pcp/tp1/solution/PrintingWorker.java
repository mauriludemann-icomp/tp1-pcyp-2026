package ar.edu.unc.fcefyn.pcp.tp1.solution;

import ar.edu.unc.fcefyn.pcp.tp1.api.OutcomeDecider;
import ar.edu.unc.fcefyn.pcp.tp1.api.SimulationConfig;

public final class PrintingWorker implements Runnable {

    private final SimulationMonitor monitor;
    private final SimulationConfig config;

    public PrintingWorker(SimulationMonitor monitor, SimulationConfig config) {
        this.monitor = monitor;
        this.config = config;
    }

    @Override
    public void run() {
        try {
            while (true) {
      // Espera dentro del monitor (wait) hasta que haya una orden en READY_TO_PRINT
                Order order = monitor.takeForPrinting();             
                  if (order == null) {
                    return; 
                }


                sleep(config.printingDelayMillis());

                // Funcion determinista, no toca estado compartido y no necesita lock
                boolean successful = OutcomeDecider.isPrintSuccessful(order.getId(), config);

            monitor.completePrinting(order, successful);
            }
            } catch (InterruptedException e) {
            monitor.cancel(e);
            Thread.currentThread().interrupt();
        } catch (RuntimeException error) {
            
            monitor.cancel(error);
        }
    }

    // Solo se duerme si la demora es mayor que cero 
    private static void sleep(long millis) throws InterruptedException {
        if (millis > 0) {
            Thread.sleep(millis);
        }
    }
}