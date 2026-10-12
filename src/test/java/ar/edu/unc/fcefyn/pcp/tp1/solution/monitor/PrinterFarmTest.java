package ar.edu.unc.fcefyn.pcp.tp1.solution.monitor;

import ar.edu.unc.fcefyn.pcp.tp1.api.PrinterSnapshot;
import ar.edu.unc.fcefyn.pcp.tp1.api.PrinterState;
import ar.edu.unc.fcefyn.pcp.tp1.api.SimulationConfig;
import ar.edu.unc.fcefyn.pcp.tp1.solution.model.Order;
import ar.edu.unc.fcefyn.pcp.tp1.solution.model.Printer;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PrinterFarmTest {

    private SimulationConfig config(int rows, int columns) {
        return new SimulationConfig(1, rows, columns, 1, 1, 1, 1,
                100, 100, 100, 0, 0, 0, 0, 1L, Path.of("resultados-test"));
    }

    @RepeatedTest(20)
    void nuncaDosOrdenesConLaMismaImpresora() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            PrinterFarm farm = new PrinterFarm(config(1, 3)); // 3 impresoras, 12 hilos
            Set<String> enUso = new HashSet<>();
            int[] violaciones = {0};
            Object lockTest = new Object();

            List<Thread> threads = new ArrayList<>();
            for (int t = 0; t < 12; t++) {
                int base = t * 100;
                threads.add(new Thread(() -> {
                    try {
                        for (int i = 0; i < 50; i++) {
                            Printer p = farm.reserve(new Order(base + i));
                            synchronized (lockTest) {
                                if (!enUso.add(p.getId())) violaciones[0]++;
                            }
                            Thread.yield();
                            synchronized (lockTest) {
                                enUso.remove(p.getId());
                            }
                            farm.release(p);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }));
            }
            threads.forEach(Thread::start);
            for (Thread t : threads) t.join();

            assertEquals(0, violaciones[0]);
            for (PrinterSnapshot s : farm.snapshots()) {
                assertEquals(PrinterState.AVAILABLE, s.state());
                assertNull(s.assignedOrderId());
            }
        });
    }

    @Test
    void unaImpresoraOchoOrdenesTodasLaReutilizan() throws InterruptedException {
        PrinterFarm farm = new PrinterFarm(config(1, 1));
        for (int i = 1; i <= 8; i++) {
            Printer p = farm.reserve(new Order(i));
            farm.release(p);
        }
        PrinterSnapshot s = farm.snapshots().getFirst();
        assertEquals(8, s.usageCount());
        assertEquals(PrinterState.AVAILABLE, s.state());
    }

    @Test
    void impresoraFueraDeServicioNoVuelveAAsignarse() throws InterruptedException {
        PrinterFarm farm = new PrinterFarm(config(1, 2));
        Printer primera = farm.reserve(new Order(1));
        farm.disable(primera);

        for (int i = 2; i <= 6; i++) {
            Printer p = farm.reserve(new Order(i));
            assertNotSame(primera, p);
            farm.release(p);
        }
        PrinterSnapshot s = farm.snapshots().stream()
                .filter(x -> x.id().equals(primera.getId())).findFirst().orElseThrow();
        assertEquals(PrinterState.OUT_OF_SERVICE, s.state());
        assertEquals(1, s.usageCount());
    }

    @Test
    void reserveBloqueaHastaQueSeLibereUnaImpresora() throws InterruptedException {
        PrinterFarm farm = new PrinterFarm(config(1, 1));
        Printer unica = farm.reserve(new Order(1));
        Printer[] obtenida = {null};

        Thread esperador = new Thread(() -> {
            try {
                obtenida[0] = farm.reserve(new Order(2));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        esperador.start();

        long limite = System.currentTimeMillis() + 2000;
        while (esperador.getState() != Thread.State.WAITING && System.currentTimeMillis() < limite) {
            Thread.sleep(5);
        }
        assertEquals(Thread.State.WAITING, esperador.getState());

        farm.release(unica);
        esperador.join(2000);
        assertFalse(esperador.isAlive());
        assertSame(unica, obtenida[0]);
    }
}