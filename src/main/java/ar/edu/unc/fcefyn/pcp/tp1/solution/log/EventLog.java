package ar.edu.unc.fcefyn.pcp.tp1.solution.log;

import ar.edu.unc.fcefyn.pcp.tp1.api.OrderState;
import ar.edu.unc.fcefyn.pcp.tp1.solution.model.Order;
import ar.edu.unc.fcefyn.pcp.tp1.solution.model.Stage;

import java.util.ArrayList;
import java.util.List;


public class EventLog {

    private final long startNanos;
    private final List<String> lines = new ArrayList<>();
    private long sequence;

    public EventLog(long startNanos) {
        this.startNanos = startNanos;
    }

    public synchronized void created(Order order) {
        append(order, Stage.INITIALIZATION, "ORDER_CREATED", null, OrderState.CREATED, null);
    }

    public synchronized void changed(Order order, Stage stage, OrderState from, OrderState to) {
        order.transition(from, to);
        append(order, stage, "ORDER_STATE_CHANGED", from, to, order.toSnapshot().assignedPrinterId());
    }

    public synchronized List<String> lines() {
        return List.copyOf(lines);
    }

    private void append(Order order, Stage stage, String event,
                        OrderState from, OrderState to, String printerId) {
        sequence++;
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        lines.add(sequence + ";" + elapsedMs + ";" + Thread.currentThread().getName() + ";"
                + order.getId() + ";" + stage + ";" + event + ";"
                + (from == null ? "" : from) + ";" + to + ";"
                + (printerId == null ? "" : printerId));
    }
}
