package ar.edu.unc.fcefyn.pcp.tp1.solution.monitor;

import ar.edu.unc.fcefyn.pcp.tp1.api.PrinterSnapshot;
import ar.edu.unc.fcefyn.pcp.tp1.api.PrinterState;
import ar.edu.unc.fcefyn.pcp.tp1.api.SimulationConfig;
import ar.edu.unc.fcefyn.pcp.tp1.solution.model.Order;
import ar.edu.unc.fcefyn.pcp.tp1.solution.model.Printer;

import java.util.List;
import java.util.ArrayList;
// Pool de impresoras (monitor propio). Es el unico lugar donde se llaman los metodos
// de Printer, siempre con el lock de esta clase tomado.
public class PrinterFarm {

    private List <Printer> printerFarm;

    public PrinterFarm(SimulationConfig config) {
        List<Printer> created = new ArrayList<>(config.totalPrinters());
        for (int row = 0; row < config.printerRows(); row++) {
            for (int column = 0; column < config.printerColumns(); column++) {
                created.add(new Printer(row, column));
            }
        }
        this.printerFarm = created;
    }

    public synchronized Printer reserve(Order order) throws InterruptedException {
        Printer p;
        while((p = buscarDisponible()) == null){
            wait();
        }
        p.reserve(order.getId());
        return p;
    }

    public synchronized void release(Printer printer) {
        printer.release();
        notifyAll();
    }

    public synchronized void disable(Printer printer) {
        printer.disable();
    }

    public synchronized List<PrinterSnapshot> snapshots() {
        List<PrinterSnapshot> p = new ArrayList<>();
        for(Printer x : printerFarm){
            p.add(x.toSnapshot());
        }
        return p;
    }

    private Printer buscarDisponible(){
        Printer p = null;
        for(Printer x : printerFarm){
            if(x.getState() == PrinterState.AVAILABLE){
                p = x;
                break;
            }
        }
        return p;
    }
}
