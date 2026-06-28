package dev.jeval;

import java.util.List;

public record EvaluationResult(LlmTestCase testCase, List<MetricResult> metricResults) {
    public EvaluationResult {
        metricResults = List.copyOf(metricResults);
    }

    public boolean success() {
        return metricResults.stream().allMatch(MetricResult::success);
    }

    public String failureMessage() {
        return metricResults.stream()
                .filter(result -> !result.success())
                .map(result -> result.name() + " failed: " + result.reason())
                .findFirst()
                .orElse("Evaluation passed");
    }
}
