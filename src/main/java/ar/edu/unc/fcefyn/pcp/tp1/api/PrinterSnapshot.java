package ar.edu.unc.fcefyn.pcp.tp1.api;

public record PrinterSnapshot(
        String id,
        PrinterState state,
        int usageCount,
        Integer assignedOrderId
) {
}

