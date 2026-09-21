package ar.edu.unc.fcefyn.pcp.tp1.api;

import java.nio.file.Path;
import java.util.Objects;

public record SimulationConfig(
        int totalOrders,
        int printerRows,
        int printerColumns,
        int assignmentThreads,
        int validationThreads,
        int printingThreads,
        int qualityControlThreads,
        int validModelPercentage,
        int printSuccessPercentage,
        int qualityApprovedPercentage,
        long assignmentDelayMillis,
        long validationDelayMillis,
        long printingDelayMillis,
        long qualityControlDelayMillis,
        long randomSeed,
        Path outputDirectory
) {
    public SimulationConfig {
        requirePositive("totalOrders", totalOrders);
        requirePositive("printerRows", printerRows);
        requirePositive("printerColumns", printerColumns);
        requirePositive("assignmentThreads", assignmentThreads);
        requirePositive("validationThreads", validationThreads);
        requirePositive("printingThreads", printingThreads);
        requirePositive("qualityControlThreads", qualityControlThreads);
        requirePercentage("validModelPercentage", validModelPercentage);
        requirePercentage("printSuccessPercentage", printSuccessPercentage);
        requirePercentage("qualityApprovedPercentage", qualityApprovedPercentage);
        requireNonNegative("assignmentDelayMillis", assignmentDelayMillis);
        requireNonNegative("validationDelayMillis", validationDelayMillis);
        requireNonNegative("printingDelayMillis", printingDelayMillis);
        requireNonNegative("qualityControlDelayMillis", qualityControlDelayMillis);
        Objects.requireNonNull(outputDirectory, "outputDirectory no puede ser null");
    }

    public int totalPrinters() {
        return Math.multiplyExact(printerRows, printerColumns);
    }

    private static void requirePositive(String name, int value) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " debe ser mayor que cero");
        }
    }

    private static void requirePercentage(String name, int value) {
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException(name + " debe estar entre 0 y 100");
        }
    }

    private static void requireNonNegative(String name, long value) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " no puede ser negativo");
        }
    }
}

