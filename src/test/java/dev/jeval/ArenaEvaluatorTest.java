package dev.jeval;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ArenaEvaluatorTest {

    @Test
    void evaluateReturnsOneResultPerArenaMetric() {
        var arena = arena();
        var metric = new StaticArenaMetric("preference", "model-a", "clearer", true);

        var result = Evaluator.evaluate(arena, List.of(metric));

        assertAll(
                () -> assertTrue(result.success()),
                () -> assertEquals(arena, result.testCase()),
                () -> assertEquals(1, result.metricResults().size()),
                () -> assertEquals("preference", result.metricResults().getFirst().name()),
                () -> assertEquals("model-a", result.metricResults().getFirst().winner()),
                () -> assertEquals("clearer", result.metricResults().getFirst().reason()));
    }

    @Test
    void evaluateArenasReturnsOneResultPerArena() {
        var passing = arena();
        var failing = new ArenaTestCase(List.of(
                new Contestant("model-a", new LlmTestCase("input", "bad", "expected")),
                new Contestant("model-b", new LlmTestCase("input", "good", "expected"))));
        var metric = new WinnerIsModelAMetric();

        var results = Evaluator.evaluateArenas(List.of(passing, failing), List.of(metric));

        assertAll(
                () -> assertEquals(2, results.size()),
                () -> assertTrue(results.getFirst().success()),
                () -> assertFalse(results.getLast().success()));
    }

    @Test
    void assertTestThrowsWhenAnyArenaMetricFails() {
        var error = assertThrows(ArenaEvaluationAssertionError.class,
                () -> Evaluator.assertTest(arena(), List.of(new StaticArenaMetric("preference", "model-b", "wrong winner", false))));

        assertAll(
                () -> assertFalse(error.result().success()),
                () -> assertEquals("preference failed: wrong winner", error.result().failureMessage()));
    }

    private static ArenaTestCase arena() {
        return new ArenaTestCase(List.of(
                new Contestant("model-a", new LlmTestCase("input", "good", "expected")),
                new Contestant("model-b", new LlmTestCase("input", "bad", "expected"))));
    }

    private static final class StaticArenaMetric implements ArenaMetric {
        private final String name;
        private final String winner;
        private final String reason;
        private final boolean success;

        StaticArenaMetric(String name, String winner, String reason, boolean success) {
            this.name = name;
            this.winner = winner;
            this.reason = reason;
            this.success = success;
        }

        @Override
        public String measure(ArenaTestCase testCase) {
            return winner;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String reason() {
            return reason;
        }

        @Override
        public boolean success() {
            return success;
        }
    }

    private static final class WinnerIsModelAMetric implements ArenaMetric {
        private boolean success;

        @Override
        public String measure(ArenaTestCase testCase) {
            var winner = testCase.contestants().getFirst().testCase().actualOutput().equals("good")
                    ? "model-a"
                    : "model-b";
            success = "model-a".equals(winner);
            return winner;
        }

        @Override
        public String name() {
            return "winner is model-a";
        }

        @Override
        public String reason() {
            return success ? "correct winner" : "wrong winner";
        }

        @Override
        public boolean success() {
            return success;
        }
    }
}
