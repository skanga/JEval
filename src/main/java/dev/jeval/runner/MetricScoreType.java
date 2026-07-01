package dev.jeval.runner;

public record MetricScoreType(String metric, double score) {
    public MetricScoreType {
        if (metric == null || metric.isBlank()) {
            throw new IllegalArgumentException("MetricScoreType metric is required");
        }
        if (!Double.isFinite(score)) {
            throw new IllegalArgumentException("MetricScoreType score must be finite");
        }
    }
}
