package dev.jeval.runner;

import dev.jeval.MetricResult;
import java.util.List;

public record TestRunResult(
        String name,
        List<TestCaseResult> results,
        TestRunSummary summary,
        List<MetricAggregate> aggregates) {
    public TestRunResult {
        results = List.copyOf(results);
        aggregates = List.copyOf(aggregates);
    }

    public boolean success() {
        return summary.failed() == 0;
    }

    public record TestCaseResult(
            String name,
            boolean success,
            List<MetricResult> metricResults,
            String input,
            String actualOutput,
            String expectedOutput,
            List<String> context,
            List<Object> retrievalContext,
            List<String> tags) {
        public TestCaseResult(String name, boolean success, List<MetricResult> metricResults) {
            this(name, success, metricResults, null, null, null, null, null, null);
        }

        public TestCaseResult {
            metricResults = List.copyOf(metricResults);
            context = context == null ? null : List.copyOf(context);
            retrievalContext = retrievalContext == null ? null : List.copyOf(retrievalContext);
            tags = tags == null ? null : List.copyOf(tags);
        }
    }

    public record TestRunSummary(int total, int passed, int failed, double averageScore, double passRate) {
    }

    public record MetricAggregate(String name, double averageScore, double passRate, int total) {
    }
}
