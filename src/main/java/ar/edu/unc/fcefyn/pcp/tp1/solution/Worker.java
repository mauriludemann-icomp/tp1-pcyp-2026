package ar.edu.unc.fcefyn.pcp.tp1.solution;

public abstract class Worker implements Runnable {
    protected WorkQueue inputQueue;
    protected WorkQueue outputQueue;
    protected EventLogger eventLogger;
    protected String stageName;

    public Worker(WorkQueue inputQueue,WorkQueue outputQueue ,EventLogger eventLogger, String stageName) {
        this.inputQueue = inputQueue;
        this.outputQueue = outputQueue;
        this.eventLogger = eventLogger;
        this.stageName = stageName;
    }

    /**
     * Bucle principal de ejecución del hilo.
     * Consume órdenes de la cola de entrada de forma bloqueante.
     */
    @Override
    public void run() {
        try {
            while (true) {
                // Extrae la siguiente orden. Si la cola cerró y se vació, devuelve null.
                Order order = inputQueue.pop();

                if (order == null) {
                    // Graceful Shutdown: finaliza la ejecución del hilo de esta etapa
                    break;
                }

                // Delegación polimórfica a la etapa concreta
                processOrder(order);
            }
        } catch (InterruptedException e) {
            // Finaliza esta etapa y conserva la señal para quien coordina los workers.
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Lógica específica de procesamiento de cada etapa.
     * Cada clase hija debe implementar su algoritmo aquí.
     */
    protected abstract void processOrder(Order order);
}
