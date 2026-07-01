package dev.jeval.optimizer;

import dev.jeval.MetricResult;
import java.util.List;
import java.util.Objects;

public record ScorerEvaluationResult(
        String actual,
        List<MetricResult> metricResults,
        double score,
        boolean success) {

    public ScorerEvaluationResult {
        if (!Double.isFinite(score)) {
            throw new IllegalArgumentException("ScorerEvaluationResult score must be finite");
        }
        metricResults = List.copyOf(Objects.requireNonNull(metricResults, "metricResults"));
    }
}
