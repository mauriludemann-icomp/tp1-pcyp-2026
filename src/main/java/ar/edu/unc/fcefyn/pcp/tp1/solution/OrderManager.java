package ar.edu.unc.fcefyn.pcp.tp1.solution;

import ar.edu.unc.fcefyn.pcp.tp1.api.OrderSnapshot;
import java.util.HashMap;
import java.util.Map;

/**
 * Administrador y repositorio central de las órdenes de la simulación.
 * Garantiza acceso seguro desde múltiples hilos (Thread-Safe).
 */
public class OrderManager {

    private final Map<Integer, Order> orderMap = new HashMap<>();

    public OrderManager() {
    }

    /**
     * Registra una nueva orden en el sistema.
     *
     * @param order La orden a registrar.
     */
    public synchronized void addOrder(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("La orden no puede ser nula");
        }
        orderMap.put(order.getId(), order);
    }

    /**
     * Busca y devuelve una orden por su identificador único.
     *
     * @param orderId ID de la orden requerida.
     * @return La instancia de Order o null si no existe.
     */
    public synchronized Order getOrderById(int orderId) {
        return orderMap.get(orderId);
    }

    /**
     * Genera y devuelve una instantánea (OrderSnapshot) de una orden específica.
     *
     * @param orderId ID de la orden.
     * @return OrderSnapshot de la orden o null si no existe.
     */
    public synchronized OrderSnapshot getOrderSnapshot(int orderId) {
        Order order = orderMap.get(orderId);
        return (order != null) ? order.toSnapshot() : null;
    }

    /**
     * Devuelve un mapa con las instantáneas (OrderSnapshot) de todas las órdenes
     * registradas hasta el momento.
     *
     * @return Un Map que asocia el ID de la orden con su OrderSnapshot correspondiente.
     */
    public synchronized Map<Integer, OrderSnapshot> getAllSnapshots() {
        Map<Integer, OrderSnapshot> snapshots = new HashMap<>();
        for (Map.Entry<Integer, Order> entry : orderMap.entrySet()) {
            snapshots.put(entry.getKey(), entry.getValue().toSnapshot());
        }
        return snapshots;
    }

    /**
     * Devuelve la cantidad total de órdenes registradas.
     */
    public synchronized int getTotalOrders() {
        return orderMap.size();
    }
}