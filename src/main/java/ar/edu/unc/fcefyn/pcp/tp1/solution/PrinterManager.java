package ar.edu.unc.fcefyn.pcp.tp1.solution;

import ar.edu.unc.fcefyn.pcp.tp1.api.PrinterSnapshot;
import ar.edu.unc.fcefyn.pcp.tp1.api.PrinterState;

import java.util.ArrayList;
import java.util.List;

public class PrinterManager {

    private final Printer[][] matrix;
    private final List<Printer> printerList;

    public PrinterManager(int rows, int cols) {
        this.matrix = new Printer[rows][cols];
        this.printerList = new ArrayList<>(rows * cols);

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                Printer printer = new Printer(String.format("P-%d-%d", r, c), r, c);
                matrix[r][c] = printer;
                printerList.add(printer);
            }
        }
    }

    /**
     * Busca la primera impresora disponible, la reserva para la orden e incrementa su
     * contador de usos.
     * Bloquea el hilo si no hay impresoras disponibles temporalmente.
     */
    public synchronized Printer reserveAvailablePrinter(int orderId) throws InterruptedException {
        Printer availablePrinter = findAvailablePrinter();

        while (availablePrinter == null) {
            wait();
            availablePrinter = findAvailablePrinter();
        }

        availablePrinter.assignOrder(orderId);
        return availablePrinter;
    }

    /**
     * Libera la impresora asignada.
     * Si printFailed es true, la impresora pasa a OUT_OF_SERVICE.
     */
    public synchronized void releasePrinter(Printer printer, boolean printFailed) {
        if (printFailed) {
            printer.markOutOfService();
        } else {
            printer.release();
        }
        notifyAll();
    }

    private Printer findAvailablePrinter() {
        for (Printer printer : printerList) {
            if (printer.getState() == PrinterState.AVAILABLE) {
                return printer;
            }
        }
        return null;
    }

    /** Devuelve una lista con las instantáneas actuales de todas las impresoras. */
    public synchronized List<PrinterSnapshot> getSnapshots() {
        List<PrinterSnapshot> snapshots = new ArrayList<>(printerList.size());
        for (Printer printer : printerList) {
            snapshots.add(printer.toSnapshot());
        }
        return snapshots;
    }

    public synchronized Printer getPrinterById(String printerId) {
        for (Printer printer : printerList) {
            if (printer.getId().equals(printerId)) {
                return printer;
            }
        }
        return null;
    }

    public boolean allPrintersReleased(){
        for (Printer printer : printerList) {
            if (printer.getState() == PrinterState.RESERVED || printer.getAssignedOrderId() != null) {
                return false;
            }
        }
        return true;
    }
}