package ar.edu.unc.fcefyn.pcp.tp1.solution;

import ar.edu.unc.fcefyn.pcp.tp1.api.OutcomeDecider;
import ar.edu.unc.fcefyn.pcp.tp1.api.SimulationConfig;

public final class ValidationWorker implements Runnable {

    private final SimulationMonitor monitor;
    private final SimulationConfig simulationConfig;

    public ValidationWorker(SimulationMonitor monitor, SimulationConfig simulationConfig) {
        this.monitor = monitor;
        this.simulationConfig = simulationConfig;
    }

    @Override
    public void run() {
        try {
            while (true) {
                // Espera dentro del monitor (wait) hasta que haya una orden en WAITING_VALIDATION
                Order order = monitor.takeForValidation();
                if (order == null) {
                    return; 
                }


                Thread.sleep(simulationConfig.validationDelayMillis());

                // Funcion determinista, no necesita lock
                boolean isValid = OutcomeDecider.isModelValid(order.getId(), simulationConfig);

                monitor.completeValidation(order, isValid);
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