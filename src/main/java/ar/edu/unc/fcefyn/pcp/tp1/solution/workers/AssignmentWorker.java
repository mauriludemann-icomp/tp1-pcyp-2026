package ar.edu.unc.fcefyn.pcp.tp1.solution.workers;

import ar.edu.unc.fcefyn.pcp.tp1.api.OrderState;
import ar.edu.unc.fcefyn.pcp.tp1.api.SimulationConfig;
import ar.edu.unc.fcefyn.pcp.tp1.solution.log.EventLog;
import ar.edu.unc.fcefyn.pcp.tp1.solution.model.Order;
import ar.edu.unc.fcefyn.pcp.tp1.solution.model.Stage;
import ar.edu.unc.fcefyn.pcp.tp1.solution.monitor.OrderQueue;
import ar.edu.unc.fcefyn.pcp.tp1.solution.monitor.PrinterFarm;

public class AssignmentWorker implements Runnable {

    private final OrderQueue input;        // órdenes CREATED
    private final OrderQueue output;       // órdenes WAITING_VALIDATION
    private final PrinterFarm farm;
    private final EventLog log;
    private final SimulationConfig config;

    public AssignmentWorker(OrderQueue input, OrderQueue output, PrinterFarm farm, EventLog log, SimulationConfig config) {
        this.input = input;
        this.output = output;
        this.farm = farm;
        this.log = log;
        this.config = config;
    }

    @Override 
    public void run(){
        Order order;
        try{
            while((order = input.take())!=null){ //toma ordenes del input, asigna impresora, delay, actualiza log y agrega la orden a output
                order.assign(farm.reserve(order));
                Thread.sleep(config.assignmentDelayMillis());
                order.setAssignmentCount();
                log.changed(order, Stage.ASSIGNMENT, OrderState.CREATED, OrderState.WAITING_VALIDATION);
                output.put(order);
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}