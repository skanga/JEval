package dev.jeval;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

class EvaluatorTest {

    @Test
    void evaluateReturnsOneResultPerMetric() {
        var testCase = new LlmTestCase("What is 2+2?", "4", "4");
        var metric = new StaticMetric("exact match", 1.0, 0.5, "outputs match");

        var result = Evaluator.evaluate(testCase, List.of(metric));

        assertAll(
                () -> assertTrue(result.success()),
                () -> assertEquals(1, result.metricResults().size()),
                () -> assertEquals("exact match", result.metricResults().getFirst().name()),
                () -> assertEquals(1.0, result.metricResults().getFirst().score()),
                () -> assertEquals("outputs match", result.metricResults().getFirst().reason()));
    }

    @Test
    void assertTestThrowsWhenAnyMetricFails() {
        var testCase = new LlmTestCase("What is 2+2?", "5", "4");
        var metric = new StaticMetric("exact match", 0.0, 0.5, "outputs differ");

        var error = assertThrows(EvaluationAssertionError.class,
                () -> Evaluator.assertTest(testCase, List.of(metric)));

        assertAll(
                () -> assertFalse(error.result().success()),
                () -> assertEquals("exact match", error.result().metricResults().getFirst().name()),
                () -> assertEquals("outputs differ", error.result().metricResults().getFirst().reason()));
    }

    @Test
    void evaluateReturnsOneResultPerTestCase() {
        var passing = new LlmTestCase("What is 2+2?", "4", "4");
        var failing = new LlmTestCase("What is 2+2?", "5", "4");
        var metric = new ActualEqualsExpectedMetric();

        var results = Evaluator.evaluate(List.of(passing, failing), List.of(metric));

        assertAll(
                () -> assertEquals(2, results.size()),
                () -> assertTrue(results.getFirst().success()),
                () -> assertFalse(results.getLast().success()));
    }

    @Test
    void aEvaluateReturnsFutureResult() {
        var testCase = new LlmTestCase("What is 2+2?", "4", "4");
        var metric = new StaticMetric("exact match", 1.0, 0.5, "outputs match");

        var result = Evaluator.aEvaluate(testCase, List.of(metric)).join();

        assertAll(
                () -> assertTrue(result.success()),
                () -> assertEquals("exact match", result.metricResults().getFirst().name()));
    }

    @Test
    void aEvaluateReturnsFutureResultsForTestCaseList() {
        var passing = new LlmTestCase("What is 2+2?", "4", "4");
        var failing = new LlmTestCase("What is 2+2?", "5", "4");

        var results = Evaluator.aEvaluate(List.of(passing, failing), List.of(new ActualEqualsExpectedMetric())).join();

        assertAll(
                () -> assertEquals(2, results.size()),
                () -> assertTrue(results.getFirst().success()),
                () -> assertFalse(results.getLast().success()));
    }

    @Test
    void aAssertTestReturnsFutureResultAndPropagatesAssertionErrors() {
        var passing = new LlmTestCase("What is 2+2?", "4", "4");
        var failing = new LlmTestCase("What is 2+2?", "5", "4");
        var metric = new ActualEqualsExpectedMetric();

        var result = Evaluator.aAssertTest(passing, List.of(metric)).join();
        var error = assertThrows(CompletionException.class,
                () -> Evaluator.aAssertTest(failing, List.of(metric)).join());

        assertAll(
                () -> assertTrue(result.success()),
                () -> assertEquals(EvaluationAssertionError.class, error.getCause().getClass()));
    }

    private record StaticMetric(String name, double score, double threshold, String reason) implements Metric {
        @Override
        public MetricResult measure(LlmTestCase testCase) {
            return new MetricResult(name, score, threshold, score >= threshold, reason);
        }
    }

    private static final class ActualEqualsExpectedMetric implements Metric {
        @Override
        public MetricResult measure(LlmTestCase testCase) {
            var success = testCase.actualOutput().equals(testCase.expectedOutput());
            return new MetricResult("actual equals expected", success ? 1.0 : 0.0, 1.0, success, "");
        }
    }
}
