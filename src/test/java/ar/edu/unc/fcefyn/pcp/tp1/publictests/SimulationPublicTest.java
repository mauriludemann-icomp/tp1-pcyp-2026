package ar.edu.unc.fcefyn.pcp.tp1.publictests;

import ar.edu.unc.fcefyn.pcp.tp1.api.OrderSnapshot;
import ar.edu.unc.fcefyn.pcp.tp1.api.OrderState;
import ar.edu.unc.fcefyn.pcp.tp1.api.OutcomeDecider;
import ar.edu.unc.fcefyn.pcp.tp1.api.PrinterSnapshot;
import ar.edu.unc.fcefyn.pcp.tp1.api.PrinterState;
import ar.edu.unc.fcefyn.pcp.tp1.api.Simulation;
import ar.edu.unc.fcefyn.pcp.tp1.api.SimulationConfig;
import ar.edu.unc.fcefyn.pcp.tp1.api.SimulationResult;
import ar.edu.unc.fcefyn.pcp.tp1.solution.ConcurrentSimulation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationPublicTest {

    @TempDir
    Path outputDirectory;

    @Test
    void processesEveryOrderAndProducesConsistentResults() {
        SimulationConfig config = smallConfiguration();
        Simulation simulation = new ConcurrentSimulation();

        SimulationResult result = assertTimeoutPreemptively(
                Duration.ofSeconds(5),
                () -> simulation.execute(config)
        );

        assertNotNull(result);
        assertEquals(config.totalOrders(), result.totalOrders());
        assertEquals(config.totalOrders(), result.processedOrders());
        assertTrue(result.allThreadsTerminated());
        assertEquals(config.totalOrders(), result.orders().size());
        assertEquals(config.totalPrinters(), result.printers().size());

        Set<Integer> expectedIds = IntStream.rangeClosed(1, config.totalOrders())
                .boxed()
                .collect(Collectors.toSet());
        Set<Integer> actualIds = result.orders().stream()
                .map(OrderSnapshot::id)
                .collect(Collectors.toSet());
        assertEquals(expectedIds, actualIds);
        assertEquals(result.orders().size(), new HashSet<>(actualIds).size());

        Map<Integer, OrderSnapshot> byId = result.orders().stream()
                .collect(Collectors.toMap(OrderSnapshot::id, Function.identity()));

        for (int orderId = 1; orderId <= config.totalOrders(); orderId++) {
            OrderSnapshot order = byId.get(orderId);
            assertNotNull(order);
            assertEquals(expectedState(orderId, config), order.state());
            assertTrue(order.state().isTerminal());
            assertEquals(1, order.assignmentCount());
            assertEquals(1, order.validationCount());

            if (order.state() == OrderState.REJECTED) {
                assertEquals(0, order.printingCount());
                assertEquals(0, order.qualityControlCount());
            } else if (order.state() == OrderState.PRINT_FAILED) {
                assertEquals(1, order.printingCount());
                assertEquals(0, order.qualityControlCount());
            } else {
                assertEquals(1, order.printingCount());
                assertEquals(1, order.qualityControlCount());
            }
        }

        int terminalTotal = result.totalsByState().entrySet().stream()
                .filter(entry -> entry.getKey().isTerminal())
                .mapToInt(Map.Entry::getValue)
                .sum();
        assertEquals(config.totalOrders(), terminalTotal);

        int totalPrinterUses = result.printers().stream()
                .mapToInt(PrinterSnapshot::usageCount)
                .sum();
        assertEquals(config.totalOrders(), totalPrinterUses);

        for (PrinterSnapshot printer : result.printers()) {
            assertFalse(printer.state() == PrinterState.RESERVED);
            assertNull(printer.assignedOrderId());
        }

        assertTrue(Files.isRegularFile(outputDirectory.resolve("eventos.csv")));
        assertTrue(Files.isRegularFile(outputDirectory.resolve("elementos.csv")));
        assertTrue(Files.isRegularFile(outputDirectory.resolve("resumen.properties")));
    }

    @Test
    void reusesOnePrinterWhenNoPrintingFails() {
        SimulationConfig config = new SimulationConfig(
                8,
                1,
                1,
                2,
                2,
                2,
                2,
                100,
                100,
                100,
                0,
                0,
                0,
                0,
                20261002L,
                outputDirectory
        );

        SimulationResult result = assertTimeoutPreemptively(
                Duration.ofSeconds(5),
                () -> new ConcurrentSimulation().execute(config)
        );

        assertEquals(8, result.processedOrders());
        assertTrue(result.orders().stream().allMatch(order -> order.state() == OrderState.APPROVED));
        assertEquals(1, result.printers().size());
        PrinterSnapshot printer = result.printers().getFirst();
        assertEquals(8, printer.usageCount());
        assertEquals(PrinterState.AVAILABLE, printer.state());
        assertNull(printer.assignedOrderId());
    }

    private OrderState expectedState(int orderId, SimulationConfig config) {
        if (!OutcomeDecider.isModelValid(orderId, config)) {
            return OrderState.REJECTED;
        }
        if (!OutcomeDecider.isPrintSuccessful(orderId, config)) {
            return OrderState.PRINT_FAILED;
        }
        if (!OutcomeDecider.isQualityApproved(orderId, config)) {
            return OrderState.DEFECTIVE;
        }
        return OrderState.APPROVED;
    }

    private SimulationConfig smallConfiguration() {
        return new SimulationConfig(
                40,
                2,
                3,
                2,
                2,
                2,
                1,
                85,
                90,
                95,
                0,
                0,
                0,
                0,
                20261001L,
                outputDirectory
        );
    }
}
