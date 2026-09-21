package ar.edu.unc.fcefyn.pcp.tp1.publictests;

import ar.edu.unc.fcefyn.pcp.tp1.api.ConfigurationLoader;
import ar.edu.unc.fcefyn.pcp.tp1.api.SimulationConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigurationLoaderTest {

    @Test
    void loadsTheOfficialConfiguration() throws Exception {
        SimulationConfig config = ConfigurationLoader.load(
                Path.of("config", "tp1.properties")
        );

        assertEquals(500, config.totalOrders());
        assertEquals(10, config.printerRows());
        assertEquals(20, config.printerColumns());
        assertEquals(200, config.totalPrinters());
        assertEquals(3, config.assignmentThreads());
        assertEquals(2, config.validationThreads());
        assertEquals(3, config.printingThreads());
        assertEquals(2, config.qualityControlThreads());
    }
}

