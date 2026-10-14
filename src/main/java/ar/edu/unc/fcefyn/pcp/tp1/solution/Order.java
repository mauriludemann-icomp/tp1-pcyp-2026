package ar.edu.unc.fcefyn.pcp.tp1.solution;

import ar.edu.unc.fcefyn.pcp.tp1.api.OrderSnapshot;
import ar.edu.unc.fcefyn.pcp.tp1.api.OrderState;

public final class Order {

    //Datos fijos
    private final int id;

    //Datos mutables
    private OrderState state = OrderState.CREATED;
    private Printer assignedPrinter; 
    private int assignmentCount;
    private int validationCount;
    private int printingCount;
    private int qualityControlCount;

    Order(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    OrderState getState() {
        return state;
    }

    Printer getAssignedPrinter() {
        return assignedPrinter;
    }

    //Etapa 1: CREATED -> WAITING_VALIDATION y guarda la impresora en el historial
    void assign(Printer printer) {
            if (printer == null) {
        throw new IllegalArgumentException("La impresora no puede ser null");
        }
        if (assignedPrinter != null) {
            throw new IllegalStateException("La orden " + id + " ya fue asignada a " + assignedPrinter.getId());
        }
        advance(Stage.ASSIGNMENT, OrderState.CREATED, OrderState.WAITING_VALIDATION);
        assignedPrinter = printer;
    }

    //Etapa 2: WAITING_VALIDATION -> READY_TO_PRINT o REJECTED
    void finishValidation(boolean valid) {
        OrderState to = valid ? OrderState.READY_TO_PRINT : OrderState.REJECTED;
        advance(Stage.VALIDATION, OrderState.WAITING_VALIDATION, to);
    }

    //Etapa 3: READY_TO_PRINT -> PRINTED o PRINT_FAILED
    void finishPrinting(boolean successful) {
        OrderState to = successful ? OrderState.PRINTED : OrderState.PRINT_FAILED;
        advance(Stage.PRINTING, OrderState.READY_TO_PRINT, to);
    }

    //Etapa 4: PRINTED -> APPROVED o DEFECTIVE
    void finishQualityControl(boolean approved) {
        OrderState to = approved ? OrderState.APPROVED : OrderState.DEFECTIVE;
        advance(Stage.QUALITY_CONTROL, OrderState.PRINTED, to);
    }

    OrderSnapshot toSnapshot() {
        String printerId = (assignedPrinter == null) ? null : assignedPrinter.getId();
        return new OrderSnapshot(id, state, printerId,
                assignmentCount, validationCount, printingCount, qualityControlCount);
    }

    //Cambia de estado validando el origen y suma el contador de la etapa
    private void advance(Stage stage, OrderState from, OrderState to) {
        if (state != from) {
            throw new IllegalStateException("La orden " + id + " deberia estar en " + from + " pero esta en " + state);
        }
        state = to;

        switch (stage) {
            case ASSIGNMENT -> assignmentCount++;
            case VALIDATION -> validationCount++;
            case PRINTING -> printingCount++;
            case QUALITY_CONTROL -> qualityControlCount++;
            case INITIALIZATION -> throw new IllegalArgumentException("INITIALIZATION no es una transicion");
        }
    }
}