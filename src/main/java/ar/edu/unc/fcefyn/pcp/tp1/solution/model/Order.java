package ar.edu.unc.fcefyn.pcp.tp1.solution.model;

import ar.edu.unc.fcefyn.pcp.tp1.api.OrderSnapshot;
import ar.edu.unc.fcefyn.pcp.tp1.api.OrderState;

public class Order {

    private final int id;
    private OrderState state = OrderState.CREATED;
    private Printer printer;
    private int assignmentCount;
    private int validationCount;
    private int printingCount;
    private int qualityControlCount;

    public Order(int id){
        this.id = id;
    }

    public int getId(){
        return id;
    }

    public synchronized OrderState getState(){
        return state;
    }

    public synchronized Printer getPrinter(){ return printer; }

    public synchronized void assign(Printer printer){
        if (this.printer != null) {
            throw new IllegalStateException(
                    "Orden " + id + " ya tiene asignada a " + this.printer.getId()
            );
        }
        this.printer = printer;
    }

    public synchronized void transition(OrderState from, OrderState to){
        if (state != from) {
            throw new IllegalStateException(
                    "Orden " + id + " debería estar " + from + " pero está " + state);
        }
        state = to;
    }

    public synchronized void setAssignmentCount(){
        assignmentCount ++;
    }

    public synchronized void setValidationCount(){
        validationCount ++;
    }

    public synchronized void setPrintingCount(){
        printingCount ++;
    }

    public synchronized void setQualityControlCount(){
        qualityControlCount ++;
    }

    public synchronized OrderSnapshot toSnapshot(){
        return new OrderSnapshot(
                id, state,
                printer ==null ? null : printer.getId(),
                assignmentCount, validationCount, printingCount, qualityControlCount
        );
    }
}
