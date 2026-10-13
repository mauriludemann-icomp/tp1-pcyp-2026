package ar.edu.unc.fcefyn.pcp.tp1.solution;

import java.util.ArrayList;
import java.util.List;

public class OrderFactory {
    private int countId;

    public OrderFactory() {
        this.countId = 1;
    }

    public List<Order> createOrders(int totalOrders) {
        if  (totalOrders < 0) {
            throw new IllegalArgumentException("EL total de ordenes no puede ser negativo");
        }

        List<Order> orders = new ArrayList<Order>();

        for (int i = 0; i < totalOrders; i++) {
            Order order = createOrder();
            orders.add(order);
        }

        return orders;
    }

    private Order createOrder(){
        Order order = new Order(countId);
        countId++;
        return order;
    }
}
