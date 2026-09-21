package ar.edu.unc.fcefyn.pcp.tp1.api;

public record OrderSnapshot(
        int id,
        OrderState state,
        String assignedPrinterId,
        int assignmentCount,
        int validationCount,
        int printingCount,
        int qualityControlCount
) {
}

