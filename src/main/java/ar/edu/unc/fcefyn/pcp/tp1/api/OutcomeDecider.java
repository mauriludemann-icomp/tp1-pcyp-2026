package ar.edu.unc.fcefyn.pcp.tp1.api;

public final class OutcomeDecider {

    private static final long VALIDATION_SALT = 0x243F6A8885A308D3L;
    private static final long PRINTING_SALT = 0x13198A2E03707344L;
    private static final long QUALITY_SALT = 0xA4093822299F31D0L;

    private OutcomeDecider() {
    }

    public static boolean isModelValid(int orderId, SimulationConfig config) {
        return percentage(config.randomSeed(), orderId, VALIDATION_SALT)
                < config.validModelPercentage();
    }

    public static boolean isPrintSuccessful(int orderId, SimulationConfig config) {
        return percentage(config.randomSeed(), orderId, PRINTING_SALT)
                < config.printSuccessPercentage();
    }

    public static boolean isQualityApproved(int orderId, SimulationConfig config) {
        return percentage(config.randomSeed(), orderId, QUALITY_SALT)
                < config.qualityApprovedPercentage();
    }

    static int percentage(long seed, int orderId, long salt) {
        if (orderId <= 0) {
            throw new IllegalArgumentException("orderId debe ser mayor que cero");
        }

        long value = seed ^ salt ^ (0x9E3779B97F4A7C15L * orderId);
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (int) Math.floorMod(value, 100L);
    }
}

