package dev.jeval.optimizer;

public record SimbaTraceRecord(Object output, double score, String feedback) {
    public SimbaTraceRecord {
        if (!Double.isFinite(score)) {
            throw new IllegalArgumentException("SimbaTraceRecord score must be finite");
        }
    }
}
