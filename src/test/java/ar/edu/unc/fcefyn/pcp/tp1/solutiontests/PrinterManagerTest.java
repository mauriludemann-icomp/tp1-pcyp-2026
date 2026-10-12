
package ar.edu.unc.fcefyn.pcp.tp1.solutiontests;

import ar.edu.unc.fcefyn.pcp.tp1.solution.Printer;
import ar.edu.unc.fcefyn.pcp.tp1.solution.PrinterManager;
import ar.edu.unc.fcefyn.pcp.tp1.api.PrinterState;
import ar.edu.unc.fcefyn.pcp.tp1.api.PrinterSnapshot;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class PrinterManagerTest {

    // TEST 1: Reserva, liberación y reutilización
    @Test
    void reservaYLiberacionDeImpresora() throws InterruptedException {
        PrinterManager manager = new PrinterManager(1, 1);

        Printer primera = manager.reserveAvailablePrinter(1);

        assertEquals(PrinterState.RESERVED, primera.getState());
        assertEquals(1, primera.getCount());
        assertEquals(1, primera.getAssignedOrderId());

        manager.releasePrinter(primera, false);

        assertEquals(PrinterState.AVAILABLE, primera.getState());
        assertNull(primera.getAssignedOrderId());
        assertEquals(1, primera.getCount());

        Printer segunda = manager.reserveAvailablePrinter(2);

        assertSame(primera, segunda);
        assertEquals(PrinterState.RESERVED, segunda.getState());
        assertEquals(2, segunda.getCount());
        assertEquals(2, segunda.getAssignedOrderId());

        manager.releasePrinter(segunda, false);
    }

    // TEST 2: Una impresión fallida deja la impresora fuera de servicio
    @Test
    void impresoraFueraDeServicio() throws InterruptedException {
        PrinterManager manager = new PrinterManager(1, 1);

        Printer impresora = manager.reserveAvailablePrinter(1);
        manager.releasePrinter(impresora, true);

        assertEquals(PrinterState.OUT_OF_SERVICE, impresora.getState());
        assertEquals(1, impresora.getCount());
        assertNull(impresora.getAssignedOrderId());
    }

    // TEST 3: Dos hilos no pueden reservar simultáneamente la misma impresora
    @Test
    void exclusionMutuaEnReserva() throws Exception {
        PrinterManager manager = new PrinterManager(1, 1);
        Printer primera = manager.reserveAvailablePrinter(1);

        CountDownLatch inicio = new CountDownLatch(1);
        AtomicReference<Printer> segunda = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();

        Thread hilo = new Thread(() -> {
            inicio.countDown();
            try {
                segunda.set(manager.reserveAvailablePrinter(2));
            } catch (Throwable e) {
                error.set(e);
            }
        });

        hilo.setDaemon(true);
        hilo.start();

        try {
            assertTrue(inicio.await(2, TimeUnit.SECONDS));
            assertTrue(esperarEstado(hilo, Thread.State.WAITING));
            assertNull(segunda.get());
        } finally {
            manager.releasePrinter(primera, false);
        }

        hilo.join(2000);

        assertFalse(hilo.isAlive());
        assertNull(error.get());
        assertSame(primera, segunda.get());
        assertEquals(2, primera.getCount());

        manager.releasePrinter(segunda.get(), false);
    }

    // TEST 4: Un hilo bloqueado despierta cuando se libera una impresora
    @Test
    void despiertaAlLiberarImpresora() throws Exception {
        PrinterManager manager = new PrinterManager(1, 1);
        Printer primera = manager.reserveAvailablePrinter(1);

        CountDownLatch inicio = new CountDownLatch(1);
        AtomicReference<Printer> resultado = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();

        Thread hilo = new Thread(() -> {
            inicio.countDown();
            try {
                resultado.set(manager.reserveAvailablePrinter(2));
            } catch (Throwable e) {
                error.set(e);
            }
        });

        hilo.setDaemon(true);
        hilo.start();

        try {
            assertTrue(inicio.await(2, TimeUnit.SECONDS));
            assertTrue(esperarEstado(hilo, Thread.State.WAITING));
        } finally {
            manager.releasePrinter(primera, false);
        }

        hilo.join(2000);

        assertFalse(hilo.isAlive());
        assertNull(error.get());
        assertNotNull(resultado.get());
        assertEquals(2, resultado.get().getAssignedOrderId());

        manager.releasePrinter(resultado.get(), false);
    }

    // TEST 5: Un hilo que espera una impresora puede ser interrumpido
    @Test
    void interrumpeHiloEnEspera() throws Exception {
        PrinterManager manager = new PrinterManager(1, 1);
        Printer impresora = manager.reserveAvailablePrinter(1);

        AtomicReference<Throwable> error = new AtomicReference<>();

        Thread hilo = new Thread(() -> {
            try {
                manager.reserveAvailablePrinter(2);
            } catch (Throwable e) {
                error.set(e);
            }
        });

        hilo.setDaemon(true);
        hilo.start();

        try {
            assertTrue(esperarEstado(hilo, Thread.State.WAITING));
            hilo.interrupt();
            hilo.join(2000);

            assertFalse(hilo.isAlive());
            assertInstanceOf(InterruptedException.class, error.get());
        } finally {
            hilo.interrupt();
            manager.releasePrinter(impresora, false);
        }
    }

    // TEST 6: Se pueden reservar diferentes impresoras
    @Test
    void reservaDistintasImpresoras() throws InterruptedException {
        PrinterManager manager = new PrinterManager(1, 2);

        Printer primera = manager.reserveAvailablePrinter(1);
        Printer segunda = manager.reserveAvailablePrinter(2);

        assertNotSame(primera, segunda);
        assertNotEquals(primera.getId(), segunda.getId());
        assertEquals(PrinterState.RESERVED, primera.getState());
        assertEquals(PrinterState.RESERVED, segunda.getState());

        manager.releasePrinter(primera, false);
        manager.releasePrinter(segunda, false);
    }

    // TEST 7: Los snapshots muestran el estado real
    @Test
    void snapshotsDeImpresoras() throws InterruptedException {
        PrinterManager manager = new PrinterManager(1, 2);

        Printer impresora = manager.reserveAvailablePrinter(5);

        List<PrinterSnapshot> snapshots = manager.getSnapshots();

        assertEquals(2, snapshots.size());

        PrinterSnapshot reservado = snapshots.stream()
                .filter(s -> s.id().equals(impresora.getId()))
                .findFirst()
                .orElseThrow();

        assertEquals(PrinterState.RESERVED, reservado.state());
        assertEquals(5, reservado.assignedOrderId());
        assertEquals(1, reservado.usageCount());

        manager.releasePrinter(impresora, false);
    }

    // TEST 8: Una impresora dañada no se vuelve a seleccionar
    @Test
    void noReutilizaImpresoraFueraDeServicio()
            throws InterruptedException {

        PrinterManager manager = new PrinterManager(1, 2);

        Printer primera = manager.reserveAvailablePrinter(1);
        manager.releasePrinter(primera, true);

        Printer segunda = manager.reserveAvailablePrinter(2);

        assertNotSame(primera, segunda);
        assertEquals(PrinterState.OUT_OF_SERVICE, primera.getState());
        assertEquals(PrinterState.RESERVED, segunda.getState());

        manager.releasePrinter(segunda, false);
    }

    // Auxiliar: espera brevemente a que un hilo entre en WAITING
    private boolean esperarEstado(Thread hilo, Thread.State esperado)
            throws InterruptedException {

        long limite = System.nanoTime()
                + TimeUnit.SECONDS.toNanos(2);

        while (System.nanoTime() < limite) {
            if (hilo.getState() == esperado) {
                return true;
            }
            Thread.sleep(5);
        }

        return false;
    }
}
