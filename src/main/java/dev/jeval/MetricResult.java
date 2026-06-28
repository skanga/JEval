package dev.jeval;

public record MetricResult(String name, double score, double threshold, boolean success, String reason) {
}
