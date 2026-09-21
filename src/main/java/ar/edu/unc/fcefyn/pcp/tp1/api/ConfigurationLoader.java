package ar.edu.unc.fcefyn.pcp.tp1.api;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class ConfigurationLoader {

    private ConfigurationLoader() {
    }

    public static SimulationConfig load(Path path) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        Path parent = path.toAbsolutePath().getParent();
        Path configuredOutput = Path.of(required(properties, "output.directory"));
        Path outputDirectory = configuredOutput.isAbsolute()
                ? configuredOutput.normalize()
                : parent.resolve("..").resolve(configuredOutput).normalize();

        return new SimulationConfig(
                integer(properties, "simulation.total-orders"),
                integer(properties, "printers.rows"),
                integer(properties, "printers.columns"),
                integer(properties, "threads.assignment"),
                integer(properties, "threads.validation"),
                integer(properties, "threads.printing"),
                integer(properties, "threads.quality-control"),
                integer(properties, "probability.valid-model"),
                integer(properties, "probability.print-success"),
                integer(properties, "probability.quality-approved"),
                longValue(properties, "delay.assignment.ms"),
                longValue(properties, "delay.validation.ms"),
                longValue(properties, "delay.printing.ms"),
                longValue(properties, "delay.quality-control.ms"),
                longValue(properties, "simulation.random-seed"),
                outputDirectory
        );
    }

    private static int integer(Properties properties, String key) {
        String value = required(properties, key);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("La propiedad " + key + " debe ser un entero", exception);
        }
    }

    private static long longValue(Properties properties, String key) {
        String value = required(properties, key);
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("La propiedad " + key + " debe ser un entero", exception);
        }
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Falta la propiedad obligatoria " + key);
        }
        return value.trim();
    }
}

