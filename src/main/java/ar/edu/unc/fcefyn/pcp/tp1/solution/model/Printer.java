package ar.edu.unc.fcefyn.pcp.tp1.solution.model;

import ar.edu.unc.fcefyn.pcp.tp1.api.PrinterSnapshot;
import ar.edu.unc.fcefyn.pcp.tp1.api.PrinterState;


public class Printer {
    private final String id;
    private final int row;
    private final int column;
    private PrinterState state = PrinterState.AVAILABLE;
    private int usageCount;
    private Integer assignedOrderId;

    public Printer(int row, int column){
        this.row = row;
        this.column = column;
        this.id = "P-" + row + "-" + column;
    }

    public String getId(){
        return id;
    }

    public PrinterState getState(){
        return state;
    }

    public void reserve(int orderId){
        if (state != PrinterState.AVAILABLE){
            throw new IllegalStateException(id + " no está disponible: " + state);
        }
        state = PrinterState.RESERVED;
        usageCount ++;
        assignedOrderId = orderId;
    }

    public void release(){
        requireState(PrinterState.RESERVED);
        state = PrinterState.AVAILABLE;
        assignedOrderId = null;
    }

    public void disable(){
        requireState(PrinterState.RESERVED);
        state = PrinterState.OUT_OF_SERVICE;
        assignedOrderId = null;
    }

    public PrinterSnapshot toSnapshot(){
        return new PrinterSnapshot(id, state, usageCount, assignedOrderId);
    }

    private void requireState(PrinterState expected) {
        if (state != expected) {
            throw new IllegalStateException(id + " debería estar " + expected + " pero está " + state);
        }
    }
}