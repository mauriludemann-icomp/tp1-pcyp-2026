package ar.edu.unc.fcefyn.pcp.tp1.solution;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import ar.edu.unc.fcefyn.pcp.tp1.api.OrderSnapshot;
import ar.edu.unc.fcefyn.pcp.tp1.api.OrderState;
import ar.edu.unc.fcefyn.pcp.tp1.api.PrinterSnapshot;
import ar.edu.unc.fcefyn.pcp.tp1.api.PrinterState;
import ar.edu.unc.fcefyn.pcp.tp1.api.SimulationConfig;

//Monitor central: un solo lock protege ordenes, impresoras, colas, contador y logger
public final class SimulationMonitor {

    private final SimulationConfig config;
    private final List<Order> orders;
    private final Printer[][] printers;

    private final Deque<Order> assignmentQueue = new ArrayDeque<>();
    private final Deque<Order> validationQueue = new ArrayDeque<>();
    private final Deque<Order> printingQueue = new ArrayDeque<>();
    private final Deque<Order> qualityControlQueue = new ArrayDeque<>();

    private int processedOrders; //ordenes en estado terminal
    private final EventLogger eventLogger;
    private boolean cancelled;
    private Throwable failure; //primera causa de cancelacion

    //No hace falta synchronized: los hilos arrancan despues de construir el monitor
    public SimulationMonitor(SimulationConfig config, EventLogger eventLogger) throws IOException {
        this.config = config;
        this.eventLogger = eventLogger;

        this.printers = new Printer[config.printerRows()][config.printerColumns()];
        for (int row = 0; row < config.printerRows(); row++) {
            for (int column = 0; column < config.printerColumns(); column++) {
                printers[row][column] = new Printer("P-" + row + "-" + column, row, column);
            }
        }

        this.orders = new ArrayList<>(config.totalOrders());
        for (int id = 1; id <= config.totalOrders(); id++) {
            Order order = new Order(id);
            orders.add(order);
            assignmentQueue.addLast(order);
            eventLogger.logCreation(order);
        }
    }

    public synchronized Order takeForAssignment() throws InterruptedException {
        return takeFrom(assignmentQueue);
    }

    public synchronized Order takeForValidation() throws InterruptedException {
        return takeFrom(validationQueue);
    }

    public synchronized Order takeForPrinting() throws InterruptedException {
        return takeFrom(printingQueue);
    }

    public synchronized Order takeForQualityControl() throws InterruptedException {
        return takeFrom(qualityControlQueue);
    }

    //Devuelve null si se cancelo o si ya terminaron todas las ordenes
    private Order takeFrom(Deque<Order> queue) throws InterruptedException {
        while (queue.isEmpty() && processedOrders < config.totalOrders() && !cancelled) {
            wait();
        }
        if (cancelled) {
            return null;
        }
        if (processedOrders == config.totalOrders()) {
            return null;
        }
        return queue.pollFirst();
    }

    //Espera una impresora libre. Devuelve false si se cancelo
    public synchronized boolean assignPrinter(Order order) throws InterruptedException {
    if (cancelled) {
        return false;
    }
    if (order == null) {
        throw new IllegalArgumentException("La orden no puede ser null");
    }
    if (order.getState() != OrderState.CREATED
            || order.getAssignedPrinter() != null) {
        throw new IllegalStateException(
                "La orden " + order.getId() + " no esta pendiente de asignacion");
    }

    Printer printer = findAvailablePrinter();
    while (printer == null && !cancelled) {
        wait();
        printer = findAvailablePrinter();
    }
    if (cancelled) {
        return false;
    }

    // wait() libera el monitor: revalidar antes de modificar las entidades.
    if (order.getState() != OrderState.CREATED
            || order.getAssignedPrinter() != null) {
        throw new IllegalStateException(
                "La orden " + order.getId() + " no esta pendiente de asignacion");
    }

    printer.reserve(order);
    order.assign(printer);
    log(order, Stage.ASSIGNMENT, OrderState.CREATED, OrderState.WAITING_VALIDATION);

    validationQueue.addLast(order);
    notifyAll();
    return true;
    }

    public synchronized void completeValidation(Order order, boolean valid) {
    if (cancelled) {
        return;
    }
    if (order == null) {
        throw new IllegalArgumentException("La orden no puede ser null");
    }

    Printer printer = order.getAssignedPrinter();
    if (printer == null
            || printer.getState() != PrinterState.RESERVED
            || printer.getAssignedOrder() != order) {
        throw new IllegalStateException(
                "La orden " + order.getId() + " no tiene una reserva valida");
    }

    order.finishValidation(valid);

    OrderState to;
    if (valid) {
        to = OrderState.READY_TO_PRINT;
    } else {
        to = OrderState.REJECTED;
        printer.release(order);
        processedOrders++;
    }
    log(order, Stage.VALIDATION, OrderState.WAITING_VALIDATION, to);

    if (valid) {
        printingQueue.addLast(order);
    }
    notifyAll();
    }

    public synchronized void completePrinting(Order order, boolean successful) {
        if (cancelled) {
            return;
        }
        if (order == null) {
        throw new IllegalArgumentException("La orden no puede ser null");
    }

        Printer printer = order.getAssignedPrinter();
        if (printer == null
            || printer.getState() != PrinterState.RESERVED
            || printer.getAssignedOrder() != order) {
        throw new IllegalStateException(
                "La orden " + order.getId() + " no tiene una reserva valida");
        }
        order.finishPrinting(successful);

        OrderState to;
        if (successful) {
            to = OrderState.PRINTED;
            printer.release(order);
        } else {
            to = OrderState.PRINT_FAILED;
            printer.disable(order);
            processedOrders++;
        }
        log(order, Stage.PRINTING, OrderState.READY_TO_PRINT, to);

        if (successful) {
            qualityControlQueue.addLast(order);
        }
        notifyAll();
    }

    public synchronized void completeQualityControl(Order order, boolean approved) {
        if (cancelled) {
            return;
        }
        order.finishQualityControl(approved);
        OrderState to = approved ? OrderState.APPROVED : OrderState.DEFECTIVE;
        processedOrders++;
        log(order, Stage.QUALITY_CONTROL, OrderState.PRINTED, to);
        notifyAll();
    }

    //Se queda con la primera causa y despierta a todos
    public synchronized void cancel(Throwable cause) {
        if (failure == null) {
            failure = cause;
        }
        cancelled = true;
        notifyAll();
    }

    public synchronized void awaitCompletion() throws InterruptedException {
        while (processedOrders < config.totalOrders() && !cancelled) {
            wait();
        }
        if (!cancelled && (!assignmentQueue.isEmpty()
            || !validationQueue.isEmpty()
            || !printingQueue.isEmpty()
            || !qualityControlQueue.isEmpty())) {
        cancel(new IllegalStateException(
                "La simulacion termino con ordenes en las colas"));
        }
    }

    //Solo despues de cancelar y con los workers terminados
    public synchronized void releaseReservationsAfterCancellation() {
        for (Printer[] row : printers) {
            for (Printer printer : row) {
                if (printer.getState() == PrinterState.RESERVED) {
                    printer.release(printer.getAssignedOrder());
                }
            }
        }
    }

    public synchronized Throwable getFailure() {
        return failure;
    }

    public synchronized int getProcessedOrders() {
        return processedOrders;
    }

    public synchronized int getRemainingIntermediateOrders() {
        int remaining = 0;
        for (Order order : orders) {
            if (!order.getState().isTerminal()) {
                remaining++;
            }
        }
        return remaining;
    }

    public synchronized List<OrderSnapshot> getOrderSnapshots() {
        List<OrderSnapshot> snapshots = new ArrayList<>(orders.size());
        for (Order order : orders) {
            snapshots.add(order.toSnapshot());
        }
        return snapshots;
    }

    public synchronized List<PrinterSnapshot> getPrinterSnapshots() {
        List<PrinterSnapshot> snapshots = new ArrayList<>();
        for (Printer[] row : printers) {
            for (Printer printer : row) {
                snapshots.add(printer.toSnapshot());
            }
        }
        return snapshots;
    }

    private Printer findAvailablePrinter() {
        for (Printer[] row : printers) {
            for (Printer printer : row) {
                if (printer.getState() == PrinterState.AVAILABLE) {
                    return printer;
                }
            }
        }
        return null;
    }

    private boolean hasOperationalPrinter() {
        for (Printer[] row : printers) {
            for (Printer printer : row) {
                if (printer.getState() != PrinterState.OUT_OF_SERVICE) {
                    return true;
                }
            }
        }
        return false;
    }

    //Si falla la escritura cancela y propaga el error al worker
    private void log(Order order, Stage stage, OrderState from, OrderState to) {
        try {
            eventLogger.logTransition(order, stage, from, to);
        } catch (IOException e) {
            UncheckedIOException error = new UncheckedIOException("No se pudo escribir eventos.csv", e);
            cancel(error);
            throw error;
        }
    }
}