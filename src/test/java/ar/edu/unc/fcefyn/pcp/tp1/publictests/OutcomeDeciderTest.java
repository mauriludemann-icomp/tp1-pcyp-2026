package ar.edu.unc.fcefyn.pcp.tp1.publictests;

import ar.edu.unc.fcefyn.pcp.tp1.api.OutcomeDecider;
import ar.edu.unc.fcefyn.pcp.tp1.api.SimulationConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutcomeDeciderTest {

    @TempDir
    Path outputDirectory;

    @Test
    void decisionsAreDeterministicForAnOrderAndSeed() {
        SimulationConfig config = config(85, 90, 95);

        for (int orderId = 1; orderId <= 100; orderId++) {
            assertEquals(
                    OutcomeDecider.isModelValid(orderId, config),
                    OutcomeDecider.isModelValid(orderId, config)
            );
            assertEquals(
                    OutcomeDecider.isPrintSuccessful(orderId, config),
                    OutcomeDecider.isPrintSuccessful(orderId, config)
            );
            assertEquals(
                    OutcomeDecider.isQualityApproved(orderId, config),
                    OutcomeDecider.isQualityApproved(orderId, config)
            );
        }
    }

    @Test
    void zeroAndOneHundredPercentAreRespected() {
        SimulationConfig never = config(0, 0, 0);
        SimulationConfig always = config(100, 100, 100);

        for (int orderId = 1; orderId <= 100; orderId++) {
            assertFalse(OutcomeDecider.isModelValid(orderId, never));
            assertFalse(OutcomeDecider.isPrintSuccessful(orderId, never));
            assertFalse(OutcomeDecider.isQualityApproved(orderId, never));

            assertTrue(OutcomeDecider.isModelValid(orderId, always));
            assertTrue(OutcomeDecider.isPrintSuccessful(orderId, always));
            assertTrue(OutcomeDecider.isQualityApproved(orderId, always));
        }
    }

    private SimulationConfig config(int valid, int print, int quality) {
        return new SimulationConfig(
                100,
                2,
                3,
                2,
                2,
                2,
                1,
                valid,
                print,
                quality,
                0,
                0,
                0,
                0,
                123456L,
                outputDirectory
        );
    }
}

