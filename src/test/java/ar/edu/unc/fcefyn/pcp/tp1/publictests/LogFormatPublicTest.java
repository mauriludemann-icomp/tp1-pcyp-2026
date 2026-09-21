package ar.edu.unc.fcefyn.pcp.tp1.publictests;

import ar.edu.unc.fcefyn.pcp.tp1.api.SimulationConfig;
import ar.edu.unc.fcefyn.pcp.tp1.solution.ConcurrentSimulation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogFormatPublicTest {

    private static final String EVENT_HEADER =
            "sequence;elapsedMs;thread;orderId;stage;event;fromState;toState;printer";
    private static final String ORDER_HEADER =
            "orderId;finalState;printer;assignmentCount;validationCount;printingCount;qualityControlCount";

    @TempDir
    Path outputDirectory;

    @Test
    void writesTheRequiredOutputFormats() throws Exception {
        SimulationConfig config = new SimulationConfig(
                12,
                2,
                2,
                2,
                1,
                2,
                1,
                80,
                80,
                80,
                0,
                0,
                0,
                0,
                99L,
                outputDirectory
        );

        assertTimeoutPreemptively(
                Duration.ofSeconds(5),
                () -> new ConcurrentSimulation().execute(config)
        );

        List<String> events = Files.readAllLines(outputDirectory.resolve("eventos.csv"));
        assertFalse(events.isEmpty());
        assertEquals(EVENT_HEADER, events.getFirst());

        long previousSequence = 0;
        for (String line : events.subList(1, events.size())) {
            String[] fields = line.split(";", -1);
            assertEquals(9, fields.length, "Linea de evento invalida: " + line);
            long sequence = Long.parseLong(fields[0]);
            assertTrue(sequence > previousSequence);
            previousSequence = sequence;
        }

        List<String> orders = Files.readAllLines(outputDirectory.resolve("elementos.csv"));
        assertEquals(13, orders.size());
        assertEquals(ORDER_HEADER, orders.getFirst());

        Properties summary = new Properties();
        try (var reader = Files.newBufferedReader(outputDirectory.resolve("resumen.properties"))) {
            summary.load(reader);
        }

        assertEquals("12", summary.getProperty("totalOrders"));
        assertEquals("12", summary.getProperty("processedOrders"));
        assertEquals("true", summary.getProperty("allThreadsTerminated"));
        assertEquals("0", summary.getProperty("remainingIntermediateOrders"));
    }
}

