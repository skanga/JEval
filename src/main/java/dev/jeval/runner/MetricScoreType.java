package dev.jeval.runner;

public record MetricScoreType(String metric, double score) {
    public MetricScoreType {
        if (!Double.isFinite(score)) {
            throw new IllegalArgumentException("MetricScoreType score must be finite");
        }
    }
}
