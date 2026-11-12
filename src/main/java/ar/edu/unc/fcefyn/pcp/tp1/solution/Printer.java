package ar.edu.unc.fcefyn.pcp.tp1.solution;

import ar.edu.unc.fcefyn.pcp.tp1.api.PrinterSnapshot;
import ar.edu.unc.fcefyn.pcp.tp1.api.PrinterState;

public final class Printer {

    //Datos fijos
    private final String id;
    private final int row;
    private final int column;

    //Datos mutables
    private PrinterState state = PrinterState.AVAILABLE;
    private int usageCount;
    private Order assignedOrder; // reserva actual: null si no esta reservada

    Printer(String id, int row, int column) {
        this.id = id;
        this.row = row;
        this.column = column;
    }

    public String getId() {
        return id;
    }

    PrinterState getState() {
        return state;
    }

    Order getAssignedOrder() {
        return assignedOrder;
    }

    //AVAILABLE -> RESERVED
    void reserve(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("La orden no puede ser null");
        }
        if (state != PrinterState.AVAILABLE) {
            throw new IllegalStateException("La impresora " + id + " no esta disponible: su estado es " + state);
        }
        state = PrinterState.RESERVED;
        usageCount++;
        assignedOrder = order;
    }

    //RESERVED -> AVAILABLE
    void release(Order order) {
        requireReservedBy(order);
        state = PrinterState.AVAILABLE;
        assignedOrder = null;
    }

    //RESERVED -> OUT_OF_SERVICE
    void disable(Order order) {
        requireReservedBy(order);
        state = PrinterState.OUT_OF_SERVICE;
        assignedOrder = null;
    }

    PrinterSnapshot toSnapshot() {
        Integer orderId = (assignedOrder == null) ? null : assignedOrder.getId();
        return new PrinterSnapshot(id, state, usageCount, orderId);
    }

    //Valida que la impresora este reservada y que sea por ESTA orden
    private void requireReservedBy(Order order) {
        if (state != PrinterState.RESERVED) {
            throw new IllegalStateException("La impresora " + id + " no esta reservada: su estado es " + state);
        }
        if (assignedOrder != order) {
            throw new IllegalStateException("La impresora " + id + " no esta reservada por la orden " + order.getId());
        }
    }
}