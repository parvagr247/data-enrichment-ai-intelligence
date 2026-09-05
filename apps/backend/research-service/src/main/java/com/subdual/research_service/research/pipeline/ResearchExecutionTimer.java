package com.subdual.research_service.research.pipeline;

/**
 * Clean timer abstraction for tracking pipeline execution durations
 * without scattering System.currentTimeMillis() across business logic.
 */
public class ResearchExecutionTimer {

    private final long startNanos;

    public ResearchExecutionTimer() {
        this.startNanos = System.nanoTime();
    }

    public static ResearchExecutionTimer start() {
        return new ResearchExecutionTimer();
    }

    public long elapsedMillis() {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
