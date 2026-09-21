package ar.edu.unc.fcefyn.pcp.tp1.api;

import java.util.List;
import java.util.Map;

public record SimulationResult(
        int totalOrders,
        int processedOrders,
        Map<OrderState, Integer> totalsByState,
        List<OrderSnapshot> orders,
        List<PrinterSnapshot> printers,
        long durationMillis,
        boolean allThreadsTerminated
) {
    public SimulationResult {
        totalsByState = Map.copyOf(totalsByState);
        orders = List.copyOf(orders);
        printers = List.copyOf(printers);
    }
}

