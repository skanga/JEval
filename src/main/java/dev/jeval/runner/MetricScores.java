package dev.jeval.runner;

import java.util.List;

public record MetricScores(String metric, List<Double> scores, int passes, int fails, int errors) {

    public MetricScores {
        if (metric == null || metric.isBlank()) {
            throw new IllegalArgumentException("MetricScores metric is required");
        }
        scores = scores == null ? List.of() : List.copyOf(scores);
        for (var score : scores) {
            if (score == null || !Double.isFinite(score)) {
                throw new IllegalArgumentException("MetricScores scores must be finite");
            }
        }
        if (passes < 0 || fails < 0 || errors < 0) {
            throw new IllegalArgumentException("MetricScores counts must be non-negative");
        }
    }
}
