package dev.jeval;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

class ConversationalEvaluatorTest {

    @Test
    void evaluateReturnsOneResultPerConversationalMetric() {
        var testCase = ConversationalTestCase.builder(List.of(new Turn("user", "hello"))).build();
        var metric = new StaticConversationalMetric("conversation completeness", 1.0, 0.5, "complete");

        var result = Evaluator.evaluate(testCase, List.of(metric));

        assertAll(
                () -> assertTrue(result.success()),
                () -> assertEquals(testCase, result.testCase()),
                () -> assertEquals(1, result.metricResults().size()),
                () -> assertEquals("conversation completeness", result.metricResults().getFirst().name()),
                () -> assertEquals("complete", result.metricResults().getFirst().reason()));
    }

    @Test
    void evaluateConversationsReturnsOneResultPerConversation() {
        var passing = ConversationalTestCase.builder(List.of(new Turn("user", "hello"))).build();
        var failing = ConversationalTestCase.builder(List.of(new Turn("user", ""))).build();
        var metric = new NonEmptyFirstTurnMetric();

        var results = Evaluator.evaluateConversations(List.of(passing, failing), List.of(metric));

        assertAll(
                () -> assertEquals(2, results.size()),
                () -> assertTrue(results.getFirst().success()),
                () -> assertFalse(results.getLast().success()));
    }

    @Test
    void assertTestThrowsWhenAnyConversationalMetricFails() {
        var testCase = ConversationalTestCase.builder(List.of(new Turn("user", ""))).build();
        var metric = new NonEmptyFirstTurnMetric();

        var error = assertThrows(ConversationalEvaluationAssertionError.class,
                () -> Evaluator.assertTest(testCase, List.of(metric)));

        assertAll(
                () -> assertFalse(error.result().success()),
                () -> assertEquals("non-empty first turn", error.result().metricResults().getFirst().name()));
    }

    @Test
    void aEvaluateReturnsFutureConversationalResult() {
        var testCase = ConversationalTestCase.builder(List.of(new Turn("user", "hello"))).build();
        var metric = new StaticConversationalMetric("conversation completeness", 1.0, 0.5, "complete");

        var result = Evaluator.aEvaluate(testCase, List.of(metric)).join();

        assertAll(
                () -> assertTrue(result.success()),
                () -> assertEquals(testCase, result.testCase()),
                () -> assertEquals("conversation completeness", result.metricResults().getFirst().name()));
    }

    @Test
    void aEvaluateConversationsReturnsFutureConversationalResults() {
        var passing = ConversationalTestCase.builder(List.of(new Turn("user", "hello"))).build();
        var failing = ConversationalTestCase.builder(List.of(new Turn("user", ""))).build();

        var results = Evaluator.aEvaluateConversations(
                List.of(passing, failing),
                List.of(new NonEmptyFirstTurnMetric()))
                .join();

        assertAll(
                () -> assertEquals(2, results.size()),
                () -> assertTrue(results.getFirst().success()),
                () -> assertFalse(results.getLast().success()));
    }

    @Test
    void aAssertTestReturnsFutureConversationalResultAndPropagatesAssertionErrors() {
        var passing = ConversationalTestCase.builder(List.of(new Turn("user", "hello"))).build();
        var failing = ConversationalTestCase.builder(List.of(new Turn("user", ""))).build();
        var metric = new NonEmptyFirstTurnMetric();

        var result = Evaluator.aAssertTest(passing, List.of(metric)).join();
        var error = assertThrows(CompletionException.class,
                () -> Evaluator.aAssertTest(failing, List.of(metric)).join());

        assertAll(
                () -> assertTrue(result.success()),
                () -> assertEquals(ConversationalEvaluationAssertionError.class, error.getCause().getClass()));
    }

    private record StaticConversationalMetric(String name, double score, double threshold, String reason)
            implements ConversationalMetric {
        @Override
        public MetricResult measure(ConversationalTestCase testCase) {
            return new MetricResult(name, score, threshold, score >= threshold, reason);
        }
    }

    private static final class NonEmptyFirstTurnMetric implements ConversationalMetric {
        @Override
        public MetricResult measure(ConversationalTestCase testCase) {
            var success = !testCase.turns().getFirst().content().isEmpty();
            return new MetricResult("non-empty first turn", success ? 1.0 : 0.0, 1.0, success, "");
        }
    }
}
