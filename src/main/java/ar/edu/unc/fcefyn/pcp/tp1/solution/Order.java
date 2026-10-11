package ar.edu.unc.fcefyn.pcp.tp1.solution;

import ar.edu.unc.fcefyn.pcp.tp1.api.OrderState;

/**
 * Representacion interna y mutable de una orden de fabricacion.
 *
 * <p>Una orden es tomada por exactamente un hilo a la vez: circula de una
 * etapa a la siguiente exclusivamente a traves de instancias de
 * {@link WorkQueue}. La entrega de la orden dentro de {@code WorkQueue}
 * (bloque {@code synchronized} en {@code put}/{@code take}) establece una
 * relacion happens-before segun el modelo de memoria de Java entre quien
 * escribe los campos de la orden y quien los lee a continuacion, por lo que
 * esta clase no necesita sincronizacion propia: nunca hay dos hilos
 * accediendo a la misma orden de forma concurrente sin pasar antes por el
 * monitor de una cola.</p>
 */
final class Order {

    private final int id;
    private OrderState state = OrderState.CREATED;
    private Printer printer;

    private int assignmentCount = 0;
    private int validationCount = 0;
    private int printingCount = 0;
    private int qualityControlCount = 0;

    Order(int id) {
        this.id = id;
    }

    int getId() {
        return id;
    }

    OrderState getState() {
        return state;
    }

    void setState(OrderState state) {
        this.state = state;
    }

    Printer getPrinter() {
        return printer;
    }

    void setPrinter(Printer printer) {
        this.printer = printer;
    }

    /** Identificador historico de la impresora usada por la orden (o null). */
    String getPrinterId() {
        return printer == null ? null : printer.getId();
    }

    int getAssignmentCount() {
        return assignmentCount;
    }

    void incrementAssignmentCount() {
        assignmentCount++;
    }

    int getValidationCount() {
        return validationCount;
    }

    void incrementValidationCount() {
        validationCount++;
    }

    int getPrintingCount() {
        return printingCount;
    }

    void incrementPrintingCount() {
        printingCount++;
    }

    int getQualityControlCount() {
        return qualityControlCount;
    }

    void incrementQualityControlCount() {
        qualityControlCount++;
    }
}
