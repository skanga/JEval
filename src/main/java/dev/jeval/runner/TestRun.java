package dev.jeval.runner;

import dev.jeval.ConversationalApiTestCase;
import dev.jeval.LlmApiTestCase;
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

    private static <T> List<T> append(List<T> values, T value) {
        var updated = new java.util.ArrayList<T>(values == null ? List.of() : values);
        updated.add(value);
        return updated;
    }
}
