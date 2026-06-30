package dev.jeval.runner;

import dev.jeval.ConversationalApiTestCase;
import dev.jeval.LlmApiTestCase;
import dev.jeval.MetricData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record TestRun(
        String testFile,
        List<LlmApiTestCase> testCases,
        List<ConversationalApiTestCase> conversationalTestCases,
        List<MetricScores> metricsScores,
        TraceMetricScores traceMetricsScores,
        String identifier,
        Map<String, Object> hyperparameters,
        List<PromptData> prompts,
        Integer testPassed,
        Integer testFailed,
        double runDuration,
        Double evaluationCost,
        String datasetAlias,
        String datasetId,
        boolean official) {

    public TestRun() {
        this(null, null, null, null, null, null, null, null, null, null, 0.0, null, null, null, false);
    }

    public TestRun {
        testCases = testCases == null ? List.of() : List.copyOf(testCases);
        conversationalTestCases =
                conversationalTestCases == null ? List.of() : List.copyOf(conversationalTestCases);
        metricsScores = metricsScores == null ? List.of() : List.copyOf(metricsScores);
        hyperparameters = hyperparameters == null
                ? null
                : Collections.unmodifiableMap(new LinkedHashMap<>(hyperparameters));
        prompts = prompts == null ? null : List.copyOf(prompts);
    }

    public TestRun addTestCase(LlmApiTestCase apiTestCase) {
        var updatedTestCases = append(testCases, apiTestCase);
        return copyWith(updatedTestCases, conversationalTestCases, addEvaluationCost(apiTestCase.evaluationCost()));
    }

    public TestRun addTestCase(ConversationalApiTestCase apiTestCase) {
        var updatedConversationalTestCases = append(conversationalTestCases, apiTestCase);
        return copyWith(testCases, updatedConversationalTestCases, addEvaluationCost(apiTestCase.evaluationCost()));
    }

    public MetricsScoresAggregation constructMetricsScores() {
        var aggregators = new LinkedHashMap<String, MetricScoresAggregator>();
        var validScores = 0;

        for (var testCase : testCases) {
            validScores += aggregateMetricData(testCase.metricsData(), aggregators);
        }
        for (var testCase : conversationalTestCases) {
            validScores += aggregateMetricData(testCase.metricsData(), aggregators);
        }

        var updatedMetricsScores = aggregators.values().stream()
                .map(MetricScoresAggregator::toMetricScores)
                .toList();
        return new MetricsScoresAggregation(copyWithMetricsScores(updatedMetricsScores), validScores);
    }

    private Double addEvaluationCost(Double additional) {
        if (additional == null) {
            return evaluationCost;
        }
        return evaluationCost == null ? additional : evaluationCost + additional;
    }

    private TestRun copyWith(
            List<LlmApiTestCase> testCases,
            List<ConversationalApiTestCase> conversationalTestCases,
            Double evaluationCost) {
        return new TestRun(
                testFile,
                testCases,
                conversationalTestCases,
                metricsScores,
                traceMetricsScores,
                identifier,
                hyperparameters,
                prompts,
                testPassed,
                testFailed,
                runDuration,
                evaluationCost,
                datasetAlias,
                datasetId,
                official);
    }

    private TestRun copyWithMetricsScores(List<MetricScores> metricsScores) {
        return new TestRun(
                testFile,
                testCases,
                conversationalTestCases,
                metricsScores,
                traceMetricsScores,
                identifier,
                hyperparameters,
                prompts,
                testPassed,
                testFailed,
                runDuration,
                evaluationCost,
                datasetAlias,
                datasetId,
                official);
    }

    private static int aggregateMetricData(List<Object> metricsData, Map<String, MetricScoresAggregator> aggregators) {
        var validScores = 0;
        if (metricsData == null) {
            return validScores;
        }
        for (var data : metricsData) {
            if (data instanceof MetricData metricData) {
                var aggregator = aggregators.computeIfAbsent(metricData.name(), MetricScoresAggregator::new);
                if (metricData.score() == null) {
                    aggregator.errors++;
                } else {
                    validScores++;
                    aggregator.scores.add(metricData.score());
                    if (metricData.success()) {
                        aggregator.passes++;
                    } else {
                        aggregator.fails++;
                    }
                }
            }
        }
        return validScores;
    }

    private static <T> List<T> append(List<T> values, T value) {
        var updated = new java.util.ArrayList<T>(values == null ? List.of() : values);
        updated.add(value);
        return updated;
    }

    public record MetricsScoresAggregation(TestRun testRun, int validScores) {}

    private static final class MetricScoresAggregator {
        private final String name;
        private final List<Double> scores = new ArrayList<>();
        private int passes;
        private int fails;
        private int errors;

        private MetricScoresAggregator(String name) {
            this.name = name;
        }

        private MetricScores toMetricScores() {
            return new MetricScores(name, scores, passes, fails, errors);
        }
    }
}
