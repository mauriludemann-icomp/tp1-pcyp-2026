package ar.edu.unc.fcefyn.pcp.tp1.solution.output;

import ar.edu.unc.fcefyn.pcp.tp1.api.OrderSnapshot;
import ar.edu.unc.fcefyn.pcp.tp1.api.OrderState;
import ar.edu.unc.fcefyn.pcp.tp1.api.SimulationResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;


public final class ResultWriter {

    static final String EVENT_HEADER =
            "sequence;elapsedMs;thread;orderId;stage;event;fromState;toState;printer";
    static final String ORDER_HEADER =
            "orderId;finalState;printer;assignmentCount;validationCount;printingCount;qualityControlCount";

    private ResultWriter() {
    }

    public static void write(Path outputDirectory, List<String> events, SimulationResult result)
            throws IOException {
        Files.createDirectories(outputDirectory);
        writeEvents(outputDirectory.resolve("eventos.csv"), events);
        writeOrders(outputDirectory.resolve("elementos.csv"), result.orders());
        writeSummary(outputDirectory.resolve("resumen.properties"), result);
    }

    private static void writeEvents(Path file, List<String> events) throws IOException {
        List<String> lines = new ArrayList<>(events.size() + 1);
        lines.add(EVENT_HEADER);
        lines.addAll(events);
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    private static void writeOrders(Path file, List<OrderSnapshot> orders) throws IOException {
        List<OrderSnapshot> sorted = new ArrayList<>(orders);
        sorted.sort(Comparator.comparingInt(OrderSnapshot::id));

        List<String> lines = new ArrayList<>(sorted.size() + 1);
        lines.add(ORDER_HEADER);
        for (OrderSnapshot order : sorted) {
            lines.add(order.id() + ";" + order.state() + ";"
                    + (order.assignedPrinterId() == null ? "" : order.assignedPrinterId()) + ";"
                    + order.assignmentCount() + ";" + order.validationCount() + ";"
                    + order.printingCount() + ";" + order.qualityControlCount());
        }
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    private static void writeSummary(Path file, SimulationResult result) throws IOException {
        int intermediate = result.totalOrders() - result.processedOrders();
        List<String> lines = List.of(
                "totalOrders=" + result.totalOrders(),
                "processedOrders=" + result.processedOrders(),
                "approvedOrders=" + count(result, OrderState.APPROVED),
                "rejectedOrders=" + count(result, OrderState.REJECTED),
                "printFailedOrders=" + count(result, OrderState.PRINT_FAILED),
                "defectiveOrders=" + count(result, OrderState.DEFECTIVE),
                "durationMillis=" + result.durationMillis(),
                "allThreadsTerminated=" + result.allThreadsTerminated(),
                "remainingIntermediateOrders=" + intermediate
        );
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    private static int count(SimulationResult result, OrderState state) {
        return result.totalsByState().getOrDefault(state, 0);
    }
}
