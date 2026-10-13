package ar.edu.unc.fcefyn.pcp.tp1.solution;

import ar.edu.unc.fcefyn.pcp.tp1.api.OrderSnapshot;
import ar.edu.unc.fcefyn.pcp.tp1.api.OrderState;
import ar.edu.unc.fcefyn.pcp.tp1.api.SimulationResult;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Properties;

public final class ResultWriter {

    private static final String ELEMENTS_HEADER =
            "orderId;finalState;printer;assignmentCount;validationCount;printingCount;qualityControlCount";

    private final Path outputDirectory;

    public ResultWriter(Path outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    public void write(SimulationResult result) throws IOException {
        Files.createDirectories(outputDirectory);
        writeElements(result);
        writeSummary(result);
    }

    private void writeElements(SimulationResult result) throws IOException {
        Path file = outputDirectory.resolve("elementos.csv");
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write(ELEMENTS_HEADER);
            writer.newLine();

            for (OrderSnapshot order : result.orders().stream()
                    .sorted(Comparator.comparingInt(OrderSnapshot::id))
                    .toList()) {
                writer.write(String.format(
                        "%d;%s;%s;%d;%d;%d;%d",
                        order.id(),
                        order.state().name(),
                        order.assignedPrinterId() == null ? "" : order.assignedPrinterId(),
                        order.assignmentCount(),
                        order.validationCount(),
                        order.printingCount(),
                        order.qualityControlCount()
                ));
                writer.newLine();
            }
        }
    }

    private void writeSummary(SimulationResult result) throws IOException {
        long remainingIntermediateOrders = result.orders().stream()
                .filter(order -> !order.state().isTerminal())
                .count();

        Properties summary = new Properties();
        summary.setProperty("totalOrders", Integer.toString(result.totalOrders()));
        summary.setProperty("processedOrders", Integer.toString(result.processedOrders()));
        summary.setProperty("approvedOrders", countFor(result, OrderState.APPROVED));
        summary.setProperty("rejectedOrders", countFor(result, OrderState.REJECTED));
        summary.setProperty("printFailedOrders", countFor(result, OrderState.PRINT_FAILED));
        summary.setProperty("defectiveOrders", countFor(result, OrderState.DEFECTIVE));
        summary.setProperty("durationMillis", Long.toString(result.durationMillis()));
        summary.setProperty("allThreadsTerminated", Boolean.toString(result.allThreadsTerminated()));
        summary.setProperty("remainingIntermediateOrders", Long.toString(remainingIntermediateOrders));

        Path file = outputDirectory.resolve("resumen.properties");
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            summary.store(writer, "Simulation summary");
        }
    }

    private String countFor(SimulationResult result, OrderState state) {
        return Integer.toString(result.totalsByState().getOrDefault(state, 0));
    }
}
