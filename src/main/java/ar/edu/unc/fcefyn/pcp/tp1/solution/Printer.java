package ar.edu.unc.fcefyn.pcp.tp1.solution;

import ar.edu.unc.fcefyn.pcp.tp1.api.PrinterState;

/**
 * Representacion interna y mutable de una impresora.
 *
 * <p>Esta clase NO es thread-safe por si misma: toda la sincronizacion sobre
 * sus instancias se realiza a traves del monitor de {@link PrinterPool},
 * que es la unica clase autorizada a leer o modificar su estado. De esta
 * forma evitamos duplicar locks (uno por impresora y otro por el pool) y
 * hacemos explicito que el recurso compartido real es el pool completo.</p>
 */
final class Printer {

    private final String id;
    private final int row;
    private final int column;

    private PrinterState state = PrinterState.AVAILABLE;
    private int usageCount = 0;
    private Integer assignedOrderId = null;

    Printer(String id, int row, int column) {
        this.id = id;
        this.row = row;
        this.column = column;
    }

    String getId() {
        return id;
    }

    int getRow() {
        return row;
    }

    int getColumn() {
        return column;
    }

    PrinterState getState() {
        return state;
    }

    void setState(PrinterState state) {
        this.state = state;
    }

    int getUsageCount() {
        return usageCount;
    }

    void incrementUsageCount() {
        usageCount++;
    }

    Integer getAssignedOrderId() {
        return assignedOrderId;
    }

    void setAssignedOrderId(Integer assignedOrderId) {
        this.assignedOrderId = assignedOrderId;
    }
}
