package ar.edu.unc.fcefyn.pcp.tp1.solution;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import ar.edu.unc.fcefyn.pcp.tp1.api.OrderSnapshot;
import ar.edu.unc.fcefyn.pcp.tp1.api.OrderState;
import ar.edu.unc.fcefyn.pcp.tp1.api.PrinterSnapshot;
import ar.edu.unc.fcefyn.pcp.tp1.api.PrinterState;
import ar.edu.unc.fcefyn.pcp.tp1.api.SimulationConfig;

/*
 * Cada recurso compartido tiene su propio lock y cada operacion toma solo el del
 * recurso que toca, por el menor tiempo posible. Ningun lock se toma dentro de otro.
 *
 *   - Cada cola: su propio objeto (take / enqueue).
 *   - printersLock: la matriz de impresoras (buscar, reservar, liberar, deshabilitar).
 *   - eventsLock: solo la escritura de una fila en eventos.csv.
 *   - completionLock: processedOrders, failure, cancelled y completed.
 *
 * El estado de cada orden lo protege la propia orden (metodos synchronized en Order):
 * cambiar una orden no frena a quien cambia otra. No hay un lock global de ordenes.
 *
 * Orden de las operaciones respecto del log:
 *   - Al tomar una impresora: reservar -> cambiar la orden -> registrar.
 *   - Al soltarla: cambiar la orden -> registrar -> liberar / deshabilitar.
 * Asi eventos.csv nunca muestra una impresora asignada a dos ordenes a la vez.
 */
public final class SimulationMonitor {

    // 1. Datos compartidos e inicializacion
    private final SimulationConfig config;
    private final List<Order> orders;
    private final Printer[][] printers;

    // Colas FIFO: offer agrega al final y poll retira la primera orden.
    // Cada cola es su propio monitor.
    private final Queue<Order> assignmentQueue = new ArrayDeque<>();
    private final Queue<Order> validationQueue = new ArrayDeque<>();
    private final Queue<Order> printingQueue = new ArrayDeque<>();
    private final Queue<Order> qualityControlQueue = new ArrayDeque<>();

    private final Object printersLock = new Object();   // matriz de impresoras
    private final Object eventsLock = new Object();     // solo escritura en eventos.csv
    private final Object completionLock = new Object(); // contador, falla, cancelacion y fin
    private final EventLogger eventLogger;

    private int processedOrders;
    private Throwable failure;
    // Se escriben con completionLock y se leen desde otros locks: volatile garantiza visibilidad.
    private volatile boolean cancelled;
    private volatile boolean completed;

    // Los workers arrancan despues de construir e inicializar el monitor.
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
            assignmentQueue.offer(order);
            eventLogger.logCreation(order);
        }
    }

    // 2. Acceso a las colas
    public Order takeForAssignment() throws InterruptedException {
        return takeFrom(assignmentQueue);
    }

    public Order takeForValidation() throws InterruptedException {
        return takeFrom(validationQueue);
    }

    public Order takeForPrinting() throws InterruptedException {
        return takeFrom(printingQueue);
    }

    public Order takeForQualityControl() throws InterruptedException {
        return takeFrom(qualityControlQueue);
    }

    // El mismo metodo usa un monitor diferente segun la cola recibida.
    private Order takeFrom(Queue<Order> queue) throws InterruptedException {
        synchronized (queue) {
            while (queue.isEmpty() && !completed && !cancelled) {
                queue.wait();
            }
            if (cancelled || completed) {
                return null;
            }
            return queue.poll();
        }
    }

    // Se invoca sin ningun otro lock tomado.
    private void enqueue(Queue<Order> queue, Order order) {
        synchronized (queue) {
            if (!cancelled) {
                queue.offer(order);
                queue.notifyAll();
            }
        }
    }

    private static boolean isEmpty(Queue<Order> queue) {
        synchronized (queue) {
            return queue.isEmpty();
        }
    }

    // 3. Etapas
    // Tomar impresora: reservar -> cambiar la orden -> registrar -> encolar.
    public boolean assignPrinter(Order order) throws InterruptedException {
        if (cancelled) {
            return false;
        }
        requirePendingAssignment(order);

        Printer printer;
        synchronized (printersLock) {
            // Buscar y reservar en la misma seccion critica: nadie mas toma esta impresora.
            printer = findAvailablePrinter();
            while (printer == null && !cancelled) {
                printersLock.wait(); // no se retiene ningun otro lock durante la espera
                printer = findAvailablePrinter();
            }
            if (cancelled) {
                return false;
            }
            printer.reserve(order);
        }
        // Si se cancela desde aca, la reserva la limpia releaseReservationsAfterCancellation.
        if (cancelled) {
            return false;
        }

        order.assign(printer);
        logTransition(order, Stage.ASSIGNMENT, OrderState.CREATED, OrderState.WAITING_VALIDATION);
        enqueue(validationQueue, order);
        return !cancelled;
    }

    // Valida: cambiar la orden -> registrar -> encolar.
    // Rechaza: cambiar la orden -> registrar -> liberar impresora -> contar.
    public void completeValidation(Order order, boolean valid) {
        if (cancelled) {
            return;
        }
        Printer printer = requireAssignedPrinter(order);

        order.finishValidation(valid);
        logTransition(order, Stage.VALIDATION, OrderState.WAITING_VALIDATION,
                valid ? OrderState.READY_TO_PRINT : OrderState.REJECTED);

        if (valid) {
            // La impresora sigue reservada para esta orden: no se toca printersLock.
            enqueue(printingQueue, order);
        } else {
            synchronized (printersLock) {
                printer.release(order); // valida que este reservada por esta orden
                printersLock.notifyAll();
            }
            registerProcessedOrder();
        }
    }

    // Exito: cambiar la orden -> registrar -> liberar impresora -> encolar.
    // Falla: cambiar la orden -> registrar -> deshabilitar impresora -> contar.
    public void completePrinting(Order order, boolean successful) {
        if (cancelled) {
            return;
        }
        Printer printer = requireAssignedPrinter(order);

        order.finishPrinting(successful);
        logTransition(order, Stage.PRINTING, OrderState.READY_TO_PRINT,
                successful ? OrderState.PRINTED : OrderState.PRINT_FAILED);

        synchronized (printersLock) {
            if (successful) {
                printer.release(order);
                printersLock.notifyAll();
            } else {
                // Una impresora fuera de servicio no se vuelve a usar: no hay a quien avisar.
                printer.disable(order);
            }
        }

        if (successful) {
            enqueue(qualityControlQueue, order);
        } else {
            registerProcessedOrder();
        }
    }

    // Control de calidad no usa impresoras: cambiar la orden -> registrar -> contar.
    public void completeQualityControl(Order order, boolean approved) {
        if (cancelled) {
            return;
        }
        if (order == null) {
            throw new IllegalArgumentException("La orden no puede ser null");
        }
        order.finishQualityControl(approved);
        logTransition(order, Stage.QUALITY_CONTROL, OrderState.PRINTED,
                approved ? OrderState.APPROVED : OrderState.DEFECTIVE);
        registerProcessedOrder();
    }

    // 4. Finalizacion y cancelacion
    private void registerProcessedOrder() {
        synchronized (completionLock) {
            // Dentro del lock: "si no esta cancelada, contar y quizas completar" debe ser
            // atomico respecto de cancel(), que tambien escribe con completionLock.
            if (cancelled) {
                return;
            }
            processedOrders++;
            if (processedOrders != config.totalOrders()) {
                return;
            }
            completed = true;
            completionLock.notifyAll();
        }
        // Solo se llega aca con la ultima orden: despertar a todos para que vean completed.
        wakeAllWaiters();
    }

    public void cancel(Throwable cause) {
        synchronized (completionLock) {
            if (failure == null) {
                failure = cause;
            }
            cancelled = true;
            completionLock.notifyAll();
        }
        wakeAllWaiters();
    }

    public void awaitCompletion() throws InterruptedException {
        synchronized (completionLock) {
            while (!completed && !cancelled) {
                completionLock.wait();
            }
        }
        if (!cancelled && (!isEmpty(assignmentQueue) || !isEmpty(validationQueue)
                || !isEmpty(printingQueue) || !isEmpty(qualityControlQueue))) {
            cancel(new IllegalStateException("La simulacion termino con ordenes en las colas"));
        }
    }

    // Se llama sin otros locks tomados. Se adquiere y libera uno por vez.
    private void wakeAllWaiters() {
        for (Object lock : List.of(assignmentQueue, validationQueue, printingQueue,
                qualityControlQueue, printersLock)) {
            synchronized (lock) {
                lock.notifyAll();
            }
        }
    }

    // Solo despues de cancelar y con todos los workers terminados.
    public void releaseReservationsAfterCancellation() {
        synchronized (printersLock) {
            for (Printer[] row : printers) {
                for (Printer printer : row) {
                    if (printer.getState() == PrinterState.RESERVED) {
                        printer.release(printer.getAssignedOrder());
                    }
                }
            }
        }
    }

    // 5. Consultas y resultados
    public Throwable getFailure() {
        synchronized (completionLock) {
            return failure;
        }
    }

    public int getProcessedOrders() {
        synchronized (completionLock) {
            return processedOrders;
        }
    }

    // La lista orders no se modifica despues del constructor; cada orden se lee con
    // su propio lock.
    public int getRemainingIntermediateOrders() {
        int remaining = 0;
        for (Order order : orders) {
            if (!order.getState().isTerminal()) {
                remaining++;
            }
        }
        return remaining;
    }

    public List<OrderSnapshot> getOrderSnapshots() {
        List<OrderSnapshot> snapshots = new ArrayList<>(orders.size());
        for (Order order : orders) {
            snapshots.add(order.toSnapshot());
        }
        return snapshots;
    }

    public List<PrinterSnapshot> getPrinterSnapshots() {
        synchronized (printersLock) {
            List<PrinterSnapshot> snapshots = new ArrayList<>();
            for (Printer[] row : printers) {
                for (Printer printer : row) {
                    snapshots.add(printer.toSnapshot());
                }
            }
            return snapshots;
        }
    }

    // 6. Auxiliares
    // Llamado con printersLock tomado: buscar y reservar no pueden intercalarse.
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

    // Lee la orden con su propio lock (metodos synchronized de Order).
    private void requirePendingAssignment(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("La orden no puede ser null");
        }
        if (order.getState() != OrderState.CREATED || order.getAssignedPrinter() != null) {
            throw new IllegalStateException(
                    "La orden " + order.getId() + " no esta pendiente de asignacion");
        }
    }

    // Lee la orden con su propio lock. Que la impresora este reservada por esta orden
    // lo validan release() y disable() con printersLock tomado.
    private Printer requireAssignedPrinter(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("La orden no puede ser null");
        }
        Printer printer = order.getAssignedPrinter();
        if (printer == null) {
            throw new IllegalStateException("La orden " + order.getId() + " no tiene impresora asignada");
        }
        return printer;
    }

    // eventsLock cubre solo la escritura de la fila: el numero de secuencia y el orden
    // de las filas coinciden con el orden en que se confirman las transiciones.
    // El id de la impresora se lee antes, con el lock de la orden, para no tomar el
    // lock de la orden dentro de eventsLock.
    // Si la escritura falla, se cancela la simulacion fuera de eventsLock.
    private void logTransition(Order order, Stage stage, OrderState from, OrderState to) {
        Printer printer = order.getAssignedPrinter();
        String printerId = (printer == null) ? "" : printer.getId();
        try {
            synchronized (eventsLock) {
                eventLogger.logTransition(order, printerId, stage, from, to);
            }
        } catch (IOException error) {
            UncheckedIOException writeError = new UncheckedIOException("No se pudo escribir eventos.csv", error);
            cancel(writeError);
            throw writeError;
        }
    }
}