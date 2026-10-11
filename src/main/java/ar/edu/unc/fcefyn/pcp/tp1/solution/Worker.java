package ar.edu.unc.fcefyn.pcp.tp1.solution;

public abstract class Worker implements Runnable {
    protected WorkQueue inputQueue;
    protected WorkQueue outputQueue;
    protected EventLogger eventLogger;
    protected String StageName;

    public Worker(WorkQueue inputQueue,WorkQueue outputQueue ,EventLogger eventLogger, String stageName) {
        this.inputQueue = inputQueue;
        this.outputQueue = outputQueue ;
        this.eventLogger = eventLogger;
        this.StageName = stageName;
    }

    /**
     * Bucle principal de ejecución del hilo.
     * Consume órdenes de la cola de entrada de forma bloqueante.
     */
    @Override
    public void run() {
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

        // Si esta etapa tiene una cola de salida, la cierra para propagar la señal al siguiente worker
        if (outputQueue != null) {
            outputQueue.close();
        }
    }

    /**
     * Lógica específica de procesamiento de cada etapa.
     * Cada clase hija debe implementar su algoritmo aquí.
     */
    protected abstract void processOrder(Order order);
}
