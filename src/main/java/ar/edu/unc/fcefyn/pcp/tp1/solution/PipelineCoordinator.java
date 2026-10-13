package ar.edu.unc.fcefyn.pcp.tp1.solution;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Coordinates worker startup and queue closure across pipeline stages. */
public final class PipelineCoordinator {

    private final List<List<Thread>> stages;
    private final List<WorkQueue> queues;
    private final List<Thread> allWorkers;

    /**
     * @param stages worker groups ordered by pipeline stage
     * @param queues all pipeline queues, used to wake blocked workers on cancellation
     */
    public PipelineCoordinator(List<? extends List<Thread>> stages, List<WorkQueue> queues) {
        Objects.requireNonNull(stages, "stages");
        Objects.requireNonNull(queues, "queues");

        this.stages = new ArrayList<>(stages.size());
        this.allWorkers = new ArrayList<>();
        for (List<Thread> stage : stages) {
            List<Thread> stageCopy = List.copyOf(stage);
            this.stages.add(stageCopy);
            this.allWorkers.addAll(stageCopy);
        }
        this.queues = List.copyOf(queues);
    }

    /** Starts every stage so that pipeline stages can run concurrently. */
    public void startAllWorkers() {
        for (List<Thread> stage : stages) {
            for (Thread worker : stage) {
                worker.start();
            }
        }
    }

    /**
     * Waits for every producer in a stage before closing that stage's output queue.
     * If this coordinator is interrupted, it cancels and joins all workers before
     * propagating the interruption.
     */
    public void awaitStageAndCloseOutput(int stageIndex, WorkQueue outputQueue)
            throws InterruptedException {
        Objects.requireNonNull(outputQueue, "outputQueue");
        List<Thread> stage = stages.get(stageIndex);
        try {
            for (Thread worker : stage) {
                worker.join();
            }
        } catch (InterruptedException e) {
            cancelAndAwaitTermination();
            throw e;
        }
        outputQueue.close();
    }

    public void awaitStage(int stageIndex) throws InterruptedException {
        List<Thread> stage = stages.get(stageIndex);
        try {
            for (Thread worker : stage) {
                worker.join();
            }
        } catch (InterruptedException e) {
            cancelAndAwaitTermination();
            throw e;
        }
    }

    /**
     * Closes all queues, interrupts active workers, and joins every worker. If
     * joining is itself interrupted, it keeps waiting and restores this thread's
     * interrupted status once all workers have terminated.
     */
    public void cancelAndAwaitTermination() {
        for (WorkQueue queue : queues) {
            queue.close();
        }
        for (Thread worker : allWorkers) {
            if (worker.isAlive()) {
                worker.interrupt();
            }
        }

        boolean interrupted = false;
        for (Thread worker : allWorkers) {
            boolean terminated = false;
            while (!terminated) {
                try {
                    worker.join();
                    terminated = true;
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
