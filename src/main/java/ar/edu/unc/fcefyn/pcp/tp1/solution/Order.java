package ar.edu.unc.fcefyn.pcp.tp1.solution;

import ar.edu.unc.fcefyn.pcp.tp1.api.OrderSnapshot;
import ar.edu.unc.fcefyn.pcp.tp1.api.OrderState;

public final class Order {

    private final int id;
    private OrderState state = OrderState.CREATED;
    private Printer printer;
    private int assignmentCount;
    private int validationCount;
    private int printingCount;
    private int qualityControlCount;

    /**
     * Constructor. Asignamos una id a la orden.
     * Tiramos una "IllegalArgumentException" en caso de que el id no sea valido.
     *
     *      -Arranca en estado "CREATED"
     * */
    public Order(int id) {
        if (id <= 0) {
            throw new IllegalArgumentException("El id de la orden debe ser mayor que cero");
        }
        this.id = id;
    }

    public int id() {
        return id;
    }

    public OrderState state() {
        return state;
    }

    //Retorna la impresora asociada a la orden
    public Printer printer() {
        return printer;
    }

    //Identificamos la impresora asignada. En caso de no haber, retornamos null
    public String printerId() {
        if(printer == null){
            return null;
        } else {
            return printer.id();
        }
    }

    /**
     * Cambiamos el estado de la orden. Debemos chequear que sea una transicion valida.
     * Guardamos:
     *      - Orden anterior
     *      - Orden nueva (actual)
     *
     *      -En caso de que la transicion sea invalida, lanzamos "IllegalStateException"
     */
    public OrderState moveTo(OrderState next) {
        if (!isValidTransition(state, next)) {
            throw new IllegalStateException(
                    "Orden " + id + ": transición inválida " + state + " -> " + next);
        }
        OrderState previous = state;
        state = next;
        return previous;
    }

    /**
     * Asignamos una impresora a la orden
     *
     * Condiciones:
     *      - La orden no tuvo que haber sido creada
     *      - La orden no debe tener impresora asociada
     *
     * En caso de que la impresora sea null, lanzamos "IllegalArgumentException"
     * En caso de que la orden ya fue creada o ya tenga impresora asignada: "IllegalStateException"
     */
    public void assignPrinter(Printer assigned) {
        if (assigned == null) {
            throw new IllegalArgumentException("La impresora no puede ser null");
        }

        if (state != OrderState.CREATED || printer != null) {
            throw new IllegalStateException("Orden " + id + ": ya fue asignada o no está en CREATED");
        }

        printer = assigned;
        assignmentCount++;
    }

    //Aumentamos contador de validaciones. Requiere que la orden se encuentre en estado VALIDATION.
    public void recordValidation() {
        requireState(OrderState.WAITING_VALIDATION);
        validationCount++;
    }

    //Aumentamos contador de impresiones. Requiere que la orden se encuentre en estado READY TO PRINT.
    public void recordPrinting() {
        requireState(OrderState.READY_TO_PRINT);
        printingCount++;
    }

    //Aumentamos contador de controles de calidad. Requiere que la orden se encuentre en estado PRINTED.
    public void recordQualityControl() {
        requireState(OrderState.PRINTED);
        qualityControlCount++;
    }

    //Retornamos el estado actual de la orden en un tiepo dado
    public OrderSnapshot snapshot() {
        return new OrderSnapshot(
                id, state, printerId(),
                assignmentCount, validationCount, printingCount, qualityControlCount);
    }

    //Valida que el estado esperado de la orden sea el correcto. En caso contratrio lanza "IllegalStateException"
    private void requireState(OrderState expected) {
        if (state != expected) {
            throw new IllegalStateException(
                    "Orden " + id + ": se esperaba " + expected + " pero está en " + state);
        }
    }

    //Definimos las transiciones validas. Cualquier transicion que no se encuentre aca, no es valida.
    private static boolean isValidTransition(OrderState from, OrderState to) {
        return switch (from) {
            //Si nos encontramos en estado CREATED, debemos pasar a estado WAITING_VALIDATION
            case CREATED -> to == OrderState.WAITING_VALIDATION;
            /*
            * Si nos encontramos en estado WAITING_VALIDATION, podemos:
            *   1) READY_TO_PRINT si el modelo es valido
            *   2) REJECTED si el modelo es invalido
            */
            case WAITING_VALIDATION -> to == OrderState.READY_TO_PRINT
                    || to == OrderState.REJECTED;
            case READY_TO_PRINT -> to == OrderState.PRINTED
                    || to == OrderState.PRINT_FAILED;
            case PRINTED -> to == OrderState.APPROVED
                    || to == OrderState.DEFECTIVE;
            case APPROVED, REJECTED, PRINT_FAILED, DEFECTIVE -> false;
        };
    }
}
