package ar.edu.unc.fcefyn.pcp.tp1.solution;

import ar.edu.unc.fcefyn.pcp.tp1.api.OrderState;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

public class EventLogger implements AutoCloseable {

    public enum Stage {
        INITIALIZATION,
        ASSIGNMENT,
        VALIDATION,
        PRINTING,
        QUALITY_CONTROL
    }

    public enum EventType {
        ORDER_CREATED,
        ORDER_STATE_CHANGED
    }

    private final BufferedWriter writer;
    private final long startTimeMs;
    private long sequence = 0;

    public EventLogger(Path outputDirectory) throws IOException {
        Path filePath = outputDirectory.resolve("eventos.csv");
        this.writer = new BufferedWriter(new FileWriter(filePath.toFile()));
        this.startTimeMs = System.currentTimeMillis();

        // Escribe la cabecera que pide el enunciado
        writer.write("sequence;elapsedMs;thread;orderId;stage;event;fromState;toState;printer");
        writer.newLine();
    }

    /**
     * Registra un cambio de estado. Sincronizado para asegurar la consistencia del
     * contador sequence y evitar que el texto se solape entre hilos (interleaving).
     */
    public synchronized void logEvent(
            int orderId,
            Stage stage,
            EventType eventType,
            OrderState fromState,
            OrderState toState,
            String printerId
    ) throws IOException {
        sequence++;
        long elapsedMs = System.currentTimeMillis() - startTimeMs;
        String threadName = Thread.currentThread().getName();

        String fromStr = (fromState != null) ? fromState.name() : "";
        String toStr = (toState != null) ? toState.name() : "";
        String printerStr = (printerId != null) ? printerId : "";

        String line = String.format(
                "%d;%d;%s;%d;%s;%s;%s;%s;%s",
                sequence,
                elapsedMs,
                threadName,
                orderId,
                stage.name(),
                eventType.name(),
                fromStr,
                toStr,
                printerStr
        );

        writer.write(line);
        writer.newLine();
    }

    @Override
    public synchronized void close() throws IOException {
        writer.flush();
        writer.close();
    }
}