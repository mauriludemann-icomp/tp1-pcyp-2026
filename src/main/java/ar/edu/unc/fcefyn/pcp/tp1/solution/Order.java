package ar.edu.unc.fcefyn.pcp.tp1.solution;

import ar.edu.unc.fcefyn.pcp.tp1.api.OrderSnapshot;
import ar.edu.unc.fcefyn.pcp.tp1.api.OrderState;

public class Order {

    private final int id;
    private OrderState state;
    private String assignedPrinterId;

    // Contadores de cada etapa exigidos por OrderSnapshot y elementos.csv
    private int assignmentCount = 0;
    private int validationCount = 0;
    private int printingCount = 0;
    private int qualityControlCount = 0;

    public Order(int id) {
        if (id <= 0) {
            throw new IllegalArgumentException("orderId debe ser mayor que cero");
        }
        this.id = id;
        this.state = OrderState.CREATED;
        this.assignedPrinterId = null;
    }

    public synchronized int getId() {
        return id;
    }

    public synchronized OrderState getState() {
        return state;
    }

    public synchronized String getAssignedPrinterId() {
        return assignedPrinterId;
    }

    public synchronized int getAssignmentCount() {
        return assignmentCount;
    }

    public synchronized int getValidationCount() {
        return validationCount;
    }

    public synchronized int getPrintingCount() {
        return printingCount;
    }

    public synchronized int getQualityControlCount() {
        return qualityControlCount;
    }

    /**
     * Etapa 1: Asigna impresora y cambia estado a WAITING_VALIDATION.
     */
    public synchronized void onAssigned(String printerId) {
        if (state != OrderState.CREATED) {
            throw new IllegalStateException("La orden " + id + " no está en estado CREATED");
        }
        if (printerId == null || printerId.isBlank()) {
            throw new IllegalArgumentException("El identificador de la impresora no puede ser nulo ni vacío");
        }
        this.assignedPrinterId = printerId;
        this.state = OrderState.WAITING_VALIDATION;
        this.assignmentCount++;
    }


    /**
     * Etapa 2: Aplica el resultado de la validación del modelo.
     */
    public synchronized void onValidationResult(boolean isValid) {
        if (state != OrderState.WAITING_VALIDATION) {
            throw new IllegalStateException("La orden " + id + " no está en estado WAITING_VALIDATION");
        }
        this.validationCount++;
        if (isValid) {
            this.state = OrderState.READY_TO_PRINT;
        } else {
            this.state = OrderState.REJECTED;
        }
    }

    /**
     * Etapa 3: Aplica el resultado del proceso de impresión.
     */
    public synchronized void onPrintResult(boolean isSuccess) {
        if (state != OrderState.READY_TO_PRINT) {
            throw new IllegalStateException("La orden " + id + " no está en estado READY_TO_PRINT");
        }
        this.printingCount++;
        if (isSuccess) {
            this.state = OrderState.PRINTED;
        } else {
            this.state = OrderState.PRINT_FAILED;
        }
    }

    /**
     * Etapa 4: Aplica el resultado del control de calidad.
     */
    public synchronized void onQualityResult(boolean isApproved) {
        if (state != OrderState.PRINTED) {
            throw new IllegalStateException("La orden " + id + " no está en estado PRINTED");
        }
        this.qualityControlCount++;
        if (isApproved) {
            this.state = OrderState.APPROVED;
        } else {
            this.state = OrderState.DEFECTIVE;
        }
    }

    /** Devuelve una instantánea con el estado actual de la orden (requerido por la API). */
    public synchronized OrderSnapshot toSnapshot() {
        return new OrderSnapshot(
                id,
                state,
                assignedPrinterId,
                assignmentCount,
                validationCount,
                printingCount,
                qualityControlCount
        );
    }
}