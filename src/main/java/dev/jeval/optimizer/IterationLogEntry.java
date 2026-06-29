package dev.jeval.optimizer;

public record IterationLogEntry(
        int iteration,
        String outcome,
        String reason,
        double elapsed,
        Double before,
        Double after) {
}
