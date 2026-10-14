package ar.edu.unc.fcefyn.pcp.tp1.solution;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import ar.edu.unc.fcefyn.pcp.tp1.api.OrderState;

/**
 * Escribe eventos.csv.
 * Hicimos que no sea Thread Safe a proposito ya que lo llama el Monitor con su Lock tomado. De esta forma 
 * El orden de las filas coiniciden con el roden real.
 */
public final class EventLogger {

    private static final String HEADER =
            "sequence;elapsedMs;thread;orderId;stage;event;fromState;toState;printer";

    private final BufferedWriter writer;
    private long nextSequence = 1;
    private final long startNanos;

    public EventLogger(Path outputDirectory, long startNanos) throws IOException {
        this.startNanos = startNanos;
        Files.createDirectories(outputDirectory);
        this.writer = Files.newBufferedWriter(outputDirectory.resolve("eventos.csv"), StandardCharsets.UTF_8);
        try {
        writer.write(HEADER);
        writer.newLine();
    } catch (IOException error) {
        try {
            writer.close();
        } catch (IOException closeError) {
            error.addSuppressed(closeError);
        }
        throw error;
        }
    }

    //Fila inicial de cada orden: ORDER_CREATED, sin fromState ni printer
    public void logCreation(Order order) throws IOException {
        writeRow(order, Stage.INITIALIZATION, EventType.ORDER_CREATED, null, OrderState.CREATED); 
    }

    //Fila de cada cambio de estado posterior
    public void logTransition(Order order, Stage stage, OrderState fromState, OrderState toState) throws IOException {
        writeRow(order, stage, EventType.ORDER_STATE_CHANGED, fromState, toState);  
    }

    public void close() throws IOException {
        writer.close();
    }

    //Arma y escribe una fila con los 9 campos en el orden de la cabecera
    private void writeRow(Order order, Stage stage, EventType event,
                          OrderState fromState, OrderState toState) throws IOException {
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        String thread = Thread.currentThread().getName();
        String from = (fromState == null) ? "" : fromState.name();
        String printer = (order.getAssignedPrinter() == null) ? "" : order.getAssignedPrinter().getId();

        writer.write(nextSequence + ";" + elapsedMs + ";" + thread + ";" + order.getId() + ";"
                + stage + ";" + event + ";" + from + ";" + toState + ";" + printer);
        writer.newLine();
        nextSequence++;
    }
}