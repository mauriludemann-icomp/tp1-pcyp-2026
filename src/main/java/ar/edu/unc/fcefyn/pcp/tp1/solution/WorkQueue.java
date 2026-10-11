package ar.edu.unc.fcefyn.pcp.tp1.solution;

import java.util.ArrayDeque;
import java.util.Queue;

public class WorkQueue {

    private final Queue<Order> queue = new ArrayDeque<>();
    private final int capacity;
    private boolean closed = false;

    public WorkQueue() {
        this(Integer.MAX_VALUE);
    }

    public WorkQueue(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("La capacidad debe ser mayor a 0.");
        }
        this.capacity = capacity;
    }

    /** Inserta un elemento en la cola. Si la cola está llena, bloquea hasta que haya espacio. Luego despierta
     * a los hilos en espera.
     * @param order: La orden a agregar en la cola
     */
    public synchronized void push(Order order) {
        // Bloquea mientras la cola esté llena y no cerrada
        while (queue.size() == capacity && !closed) {
            try {
                wait();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        if (closed) {
            return;
        }

        queue.add(order);
        // Despierta a los hilos consumidores bloqueados en pop()
        notifyAll();
    }

    /**
     * Extrae un elemento. Si está vacía, bloquea hasta que haya uno o hasta que la cola se
     * cierre.
     * @return El elemento extraído o null si la cola fue cerrada y no quedan elementos.
     */
    public synchronized Order pop(){
        while (queue.isEmpty() && !closed) {
            try {
                wait();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        // Si despertó, está vacía y se cerró la cola, no hay más trabajo
        if (queue.isEmpty() && closed) {
            return null;
        }

        Order order = queue.poll();
        // Despierta a los hilos productores bloqueados en push() por capacidad
        notifyAll();
        return order;
    }

    /**
     * Cierra la cola.
     */
    public synchronized void close() {
        this.closed = true;
        notifyAll(); // Despierta a hilos atrapados en wait()
    }

    public synchronized boolean isEmpty() {
        return queue.isEmpty();
    }

    public synchronized int size() {
        return queue.size();
    }
    public synchronized boolean isClosed() {
        return closed;
    }
}
