package ar.edu.unc.fcefyn.pcp.tp1.app;

import ar.edu.unc.fcefyn.pcp.tp1.api.ConfigurationLoader;
import ar.edu.unc.fcefyn.pcp.tp1.api.SimulationConfig;
import ar.edu.unc.fcefyn.pcp.tp1.api.SimulationResult;
import ar.edu.unc.fcefyn.pcp.tp1.solution.ConcurrentSimulation;

import java.nio.file.Path;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        SimulationConfig config = ConfigurationLoader.load(
                Path.of("config", "tp1.properties")
        );

        SimulationResult result = new ConcurrentSimulation().execute(config);

        System.out.printf(
                "Procesadas %d de %d ordenes en %d ms%n",
                result.processedOrders(),
                result.totalOrders(),
                result.durationMillis()
        );
    }
}

