package ar.edu.unc.fcefyn.pcp.tp1.api;

public interface Simulation {

    SimulationResult execute(SimulationConfig config) throws InterruptedException;
}

