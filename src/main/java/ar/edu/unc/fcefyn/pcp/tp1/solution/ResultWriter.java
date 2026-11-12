package ar.edu.unc.fcefyn.pcp.tp1.solution;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import ar.edu.unc.fcefyn.pcp.tp1.api.OrderSnapshot;
import ar.edu.unc.fcefyn.pcp.tp1.api.OrderState;
import ar.edu.unc.fcefyn.pcp.tp1.api.SimulationResult;

/** Escribe los resultados finales una vez terminados todos los workers. */
public final class ResultWriter {
    private static final String HEADER =
            "orderId;finalState;printer;assignmentCount;validationCount;printingCount;qualityControlCount";

    private final Path outputDirectory;

    public ResultWriter(Path outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    public void write(SimulationResult result) throws IOException {
        Files.createDirectories(outputDirectory);
        List<OrderSnapshot> orders = new ArrayList<>(result.orders());
        orders.sort(Comparator.comparingInt(OrderSnapshot::id));

        try (BufferedWriter writer = Files.newBufferedWriter(
                outputDirectory.resolve("elementos.csv"), StandardCharsets.UTF_8)) {
            writer.write(HEADER);
            writer.newLine();
            for (OrderSnapshot order : orders) {
                String printer = order.assignedPrinterId() == null ? "" : order.assignedPrinterId();
                writer.write(order.id() + ";" + order.state() + ";" + printer + ";"
                        + order.assignmentCount() + ";" + order.validationCount() + ";"
                        + order.printingCount() + ";" + order.qualityControlCount());
                writer.newLine();
            }
        }

        int remaining = 0;
        for (OrderSnapshot order : orders) {
            if (!order.state().isTerminal()) {
                remaining++;
            }
        }
        try (BufferedWriter writer = Files.newBufferedWriter(
                outputDirectory.resolve("resumen.properties"), StandardCharsets.UTF_8)) {
            writeProperty(writer, "totalOrders", result.totalOrders());
            writeProperty(writer, "processedOrders", result.processedOrders());
            writeProperty(writer, "approvedOrders", result.totalsByState().getOrDefault(OrderState.APPROVED, 0));
            writeProperty(writer, "rejectedOrders", result.totalsByState().getOrDefault(OrderState.REJECTED, 0));
            writeProperty(writer, "printFailedOrders", result.totalsByState().getOrDefault(OrderState.PRINT_FAILED, 0));
            writeProperty(writer, "defectiveOrders", result.totalsByState().getOrDefault(OrderState.DEFECTIVE, 0));
            writeProperty(writer, "durationMillis", result.durationMillis());
            writeProperty(writer, "allThreadsTerminated", result.allThreadsTerminated());
            writeProperty(writer, "remainingIntermediateOrders", remaining);
        }
    }

    private static void writeProperty(BufferedWriter writer, String key, Object value) throws IOException {
        writer.write(key + "=" + value);
        writer.newLine();
    }
}
