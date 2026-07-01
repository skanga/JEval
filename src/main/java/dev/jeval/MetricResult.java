package dev.jeval;

public record MetricResult(String name, double score, double threshold, boolean success, String reason) {
    public MetricResult {
        if (!Double.isFinite(score)) {
            throw new IllegalArgumentException("MetricResult score must be finite");
        }
        if (!Double.isFinite(threshold)) {
            throw new IllegalArgumentException("MetricResult threshold must be finite");
        }
    }
}
