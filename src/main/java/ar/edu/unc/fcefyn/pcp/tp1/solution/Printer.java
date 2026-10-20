package ar.edu.unc.fcefyn.pcp.tp1.solution;

import ar.edu.unc.fcefyn.pcp.tp1.api.PrinterSnapshot;
import ar.edu.unc.fcefyn.pcp.tp1.api.PrinterState;

public class Printer {

    private final String id;
    private final int[] pos; // [fila, columna]
    private PrinterState state;
    private Integer assignedOrderId; // Integer permite null cuando está libre o fuera de servicio
    private int count; // Contador total de usos / asignaciones

    public Printer(String id, int row, int col) {
        this.id = id;
        this.pos = new int[]{row, col};
        this.state = PrinterState.AVAILABLE;
        this.assignedOrderId = null;
        this.count = 0;
    }

    /**
     * Asigna una orden a la impresora, cambia su estado a RESERVED e incrementa su uso.
     * Solo es válido si la impresora está AVAILABLE.
     */
    public synchronized void assignOrder(int orderId) {
        if (orderId <= 0) {
            throw new IllegalArgumentException("orderId debe ser mayor que cero");
        }
        if (this.state != PrinterState.AVAILABLE) {
            throw new IllegalStateException("No se puede asignar la orden " + orderId +
                    " a la impresora " + id + " porque su estado es " + this.state);
        }

        this.assignedOrderId = orderId;
        this.state = PrinterState.RESERVED;
        this.count++;
    }

    /**
     * Libera la impresora para que vuelva a estar disponible (AVAILABLE).
     * Solo es válido si la impresora está RESERVED.
     */
    public synchronized void release() {
        if (this.state != PrinterState.RESERVED) {
            throw new IllegalStateException("No se puede liberar la impresora " + id +
                    " porque no está en estado RESERVED");
        }

        this.assignedOrderId = null;
        this.state = PrinterState.AVAILABLE;
    }

    /**
     * Marca la impresora fuera de servicio (OUT_OF_SERVICE) en caso de fallo de impresión.
     * Solo es válido si la impresora está RESERVED.
     */
    public synchronized void markOutOfService() {
        if (this.state != PrinterState.RESERVED) {
            throw new IllegalStateException("No se puede enviar a OUT_OF_SERVICE la impresora " + id +
                    " porque no está en estado RESERVED");
        }

        this.assignedOrderId = null;
        this.state = PrinterState.OUT_OF_SERVICE;
    }

    /**
     * Crea un snapshot de la impresora para la API.
     */
    public synchronized PrinterSnapshot toSnapshot() {
        return new PrinterSnapshot(
                this.id,
                this.state,
                this.count,
                this.assignedOrderId
        );
    }

    // --- Getters ---

    public String getId() {
        return id;
    }

    public int[] getPos() {
        return pos.clone();
    }

    public synchronized PrinterState getState() {
        return state;
    }

    public synchronized Integer getAssignedOrderId() {
        return assignedOrderId;
    }

    public synchronized int getCount() {
        return count;
    }
}