package ar.edu.unc.fcefyn.pcp.tp1.solution.workers;

import ar.edu.unc.fcefyn.pcp.tp1.api.OrderState;
import ar.edu.unc.fcefyn.pcp.tp1.api.SimulationConfig;
import ar.edu.unc.fcefyn.pcp.tp1.solution.log.EventLog;
import ar.edu.unc.fcefyn.pcp.tp1.solution.model.Order;
import ar.edu.unc.fcefyn.pcp.tp1.solution.model.Stage;
import ar.edu.unc.fcefyn.pcp.tp1.solution.monitor.OrderQueue;
import ar.edu.unc.fcefyn.pcp.tp1.api.OutcomeDecider;

public class QualityControlWorker implements Runnable {

    private final OrderQueue input;        // órdenes PRINTED
    private final EventLog log;
    private final SimulationConfig config;

    public QualityControlWorker(OrderQueue input, EventLog log, SimulationConfig config){
        this.input = input;
        this.log = log;
        this.config = config;
    }

    @Override 
    public void run(){
        try{
            Order order;
            while((order=input.take())!=null){
                Thread.sleep(config.qualityControlDelayMillis());
                order.setQualityControlCount();
                if(OutcomeDecider.isQualityApproved(order.getId(), config)){
                    log.changed(order, Stage.QUALITY_CONTROL, OrderState.PRINTED, OrderState.APPROVED);
                }
                else{
                    log.changed(order, Stage.QUALITY_CONTROL, OrderState.PRINTED, OrderState.DEFECTIVE);
                }
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
