package ar.edu.unc.fcefyn.pcp.tp1.solution.workers;

import ar.edu.unc.fcefyn.pcp.tp1.api.OrderState;
import ar.edu.unc.fcefyn.pcp.tp1.api.SimulationConfig;
import ar.edu.unc.fcefyn.pcp.tp1.solution.log.EventLog;
import ar.edu.unc.fcefyn.pcp.tp1.solution.model.Order;
import ar.edu.unc.fcefyn.pcp.tp1.solution.model.Stage;
import ar.edu.unc.fcefyn.pcp.tp1.solution.monitor.OrderQueue;
import ar.edu.unc.fcefyn.pcp.tp1.solution.monitor.PrinterFarm;
import ar.edu.unc.fcefyn.pcp.tp1.api.OutcomeDecider;

public class PrintingWorker implements Runnable {

    private final OrderQueue input;        // órdenes READY_TO_PRINT
    private final OrderQueue output;       // órdenes PRINTED
    private final PrinterFarm farm;
    private final EventLog log;
    private final SimulationConfig config;

    public PrintingWorker(OrderQueue input, OrderQueue output, PrinterFarm farm, EventLog log, SimulationConfig config){
        this.input = input;
        this.output = output;
        this.farm = farm;
        this.log = log;
        this.config = config;
    }

    @Override 
    public void run(){
        try{
            Order order;
            while((order=input.take())!=null){
                Thread.sleep(config.printingDelayMillis());
                order.setPrintingCount();
                if(OutcomeDecider.isPrintSuccessful(order.getId(), config)){
                    log.changed(order, Stage.PRINTING, OrderState.READY_TO_PRINT, OrderState.PRINTED);
                    farm.release(order.getPrinter());
                    output.put(order);
                }
                else{
                    log.changed(order, Stage.PRINTING, OrderState.READY_TO_PRINT, OrderState.PRINT_FAILED);
                    farm.disable(order.getPrinter());
                }
                
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
