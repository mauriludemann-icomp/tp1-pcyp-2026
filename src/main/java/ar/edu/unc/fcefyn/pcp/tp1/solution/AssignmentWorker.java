package ar.edu.unc.fcefyn.pcp.tp1.solution;

import ar.edu.unc.fcefyn.pcp.tp1.api.SimulationConfig;


public final class AssignmentWorker implements Runnable {

    private final SimulationMonitor monitor;
    private final SimulationConfig config;

    public AssignmentWorker(SimulationMonitor monitor, SimulationConfig config) {
        this.monitor = monitor;
        this.config = config;
    }

    @Override
    public void run() {
        try {
            while (true) {
                // Espera dentro del monitor (wait) hasta que haya una orden en CREATED
                Order order = monitor.takeForAssignment();
                if (order == null) {
                    // null = no hay mas trabajo:
                    return;
                }

                // La demora va afuera del monitor para no frenar a los demas hilos
                Thread.sleep(config.assignmentDelayMillis());

                // El monitor reserva la impresora, pasa la orden a WAITING_VALIDATION
                
                if (!monitor.assignPrinter(order)) {
                    return;
                }
            }
        } catch (InterruptedException error) {
            monitor.cancel(error);
            Thread.currentThread().interrupt();
        } catch (RuntimeException error) {            
            monitor.cancel(error);
        }
    }
}