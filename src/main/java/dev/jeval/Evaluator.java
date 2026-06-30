package dev.jeval;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class Evaluator {
    private Evaluator() {
    }

    public static EvaluationResult evaluate(LlmTestCase testCase, List<? extends Metric> metrics) {
        var results = metrics.stream()
                .map(metric -> metric.measure(testCase))
                .toList();
        return new EvaluationResult(testCase, results);
    }

    public static List<EvaluationResult> evaluate(List<LlmTestCase> testCases, List<? extends Metric> metrics) {
        return testCases.stream()
                .map(testCase -> evaluate(testCase, metrics))
                .toList();
    }

    public static CompletableFuture<EvaluationResult> aEvaluate(
            LlmTestCase testCase,
            List<? extends Metric> metrics) {
        return CompletableFuture.supplyAsync(() -> evaluate(testCase, metrics));
    }

    public static CompletableFuture<List<EvaluationResult>> aEvaluate(
            List<LlmTestCase> testCases,
            List<? extends Metric> metrics) {
        return CompletableFuture.supplyAsync(() -> evaluate(testCases, metrics));
    }

    public static ConversationalEvaluationResult evaluate(
            ConversationalTestCase testCase,
            List<? extends ConversationalMetric> metrics) {
        var results = metrics.stream()
                .map(metric -> metric.measure(testCase))
                .toList();
        return new ConversationalEvaluationResult(testCase, results);
    }

    public static List<ConversationalEvaluationResult> evaluateConversations(
            List<ConversationalTestCase> testCases,
            List<? extends ConversationalMetric> metrics) {
        return testCases.stream()
                .map(testCase -> evaluate(testCase, metrics))
                .toList();
    }

    public static CompletableFuture<ConversationalEvaluationResult> aEvaluate(
            ConversationalTestCase testCase,
            List<? extends ConversationalMetric> metrics) {
        return CompletableFuture.supplyAsync(() -> evaluate(testCase, metrics));
    }

    public static CompletableFuture<List<ConversationalEvaluationResult>> aEvaluateConversations(
            List<ConversationalTestCase> testCases,
            List<? extends ConversationalMetric> metrics) {
        return CompletableFuture.supplyAsync(() -> evaluateConversations(testCases, metrics));
    }

    public static ArenaEvaluationResult evaluate(ArenaTestCase testCase, List<? extends ArenaMetric> metrics) {
        var results = metrics.stream()
                .map(metric -> {
                    var winner = metric.measure(testCase);
                    return new ArenaMetricResult(metric.name(), winner, metric.success(), metric.reason());
                })
                .toList();
        return new ArenaEvaluationResult(testCase, results);
    }

    public static List<ArenaEvaluationResult> evaluateArenas(
            List<ArenaTestCase> testCases,
            List<? extends ArenaMetric> metrics) {
        return testCases.stream()
                .map(testCase -> evaluate(testCase, metrics))
                .toList();
    }

    public static CompletableFuture<ArenaEvaluationResult> aEvaluate(
            ArenaTestCase testCase,
            List<? extends ArenaMetric> metrics) {
        return CompletableFuture.supplyAsync(() -> evaluate(testCase, metrics));
    }

    public static CompletableFuture<List<ArenaEvaluationResult>> aEvaluateArenas(
            List<ArenaTestCase> testCases,
            List<? extends ArenaMetric> metrics) {
        return CompletableFuture.supplyAsync(() -> evaluateArenas(testCases, metrics));
    }

    public static EvaluationResult assertTest(LlmTestCase testCase, List<? extends Metric> metrics) {
        var result = evaluate(testCase, metrics);
        if (!result.success()) {
            throw new EvaluationAssertionError(result);
        }
        return result;
    }

    public static CompletableFuture<EvaluationResult> aAssertTest(
            LlmTestCase testCase,
            List<? extends Metric> metrics) {
        return CompletableFuture.supplyAsync(() -> assertTest(testCase, metrics));
    }

    public static ConversationalEvaluationResult assertTest(
            ConversationalTestCase testCase,
            List<? extends ConversationalMetric> metrics) {
        var result = evaluate(testCase, metrics);
        if (!result.success()) {
            throw new ConversationalEvaluationAssertionError(result);
        }
        return result;
    }

    public static CompletableFuture<ConversationalEvaluationResult> aAssertTest(
            ConversationalTestCase testCase,
            List<? extends ConversationalMetric> metrics) {
        return CompletableFuture.supplyAsync(() -> assertTest(testCase, metrics));
    }

    public static ArenaEvaluationResult assertTest(ArenaTestCase testCase, List<? extends ArenaMetric> metrics) {
        var result = evaluate(testCase, metrics);
        if (!result.success()) {
            throw new ArenaEvaluationAssertionError(result);
        }
        return result;
    }

    public static CompletableFuture<ArenaEvaluationResult> aAssertTest(
            ArenaTestCase testCase,
            List<? extends ArenaMetric> metrics) {
        return CompletableFuture.supplyAsync(() -> assertTest(testCase, metrics));
    }
}
