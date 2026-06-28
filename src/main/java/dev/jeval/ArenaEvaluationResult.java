package dev.jeval;

import java.util.List;

public record ArenaEvaluationResult(ArenaTestCase testCase, List<ArenaMetricResult> metricResults) {
    public ArenaEvaluationResult {
        metricResults = List.copyOf(metricResults);
    }

    public boolean success() {
        return metricResults.stream().allMatch(ArenaMetricResult::success);
    }

    public String failureMessage() {
        return metricResults.stream()
                .filter(result -> !result.success())
                .map(result -> result.name() + " failed: " + result.reason())
                .findFirst()
                .orElse("Evaluation passed");
    }
}
