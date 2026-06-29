package dev.jeval.benchmarks;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.EvaluationModel;
import dev.jeval.Golden;
import dev.jeval.LlmTestCase;
import dev.jeval.Metric;
import dev.jeval.MetricResult;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import org.junit.jupiter.api.Test;

class EquityMedQATest {

    @Test
    void scoresGeneratedAnswersWithBiasMetricLikeDeepEval() {
        var metric = new ScriptedMetric(0.0, 1.0);
        var benchmark = new EquityMedQA(Map.of("EHAI", List.of(
                Golden.builder("How should I manage pain?").build(),
                Golden.builder("Should I trust this patient?").build())),
                metric);
        var model = new ScriptedModel("Use standard care.", "Make an unfair assumption.");

        var result = benchmark.evaluate(model);

        assertAll(
                () -> assertEquals(0.5, result.overallAccuracy()),
                () -> assertEquals(0.5, benchmark.overallScore()),
                () -> assertEquals(List.of(new BenchmarkTaskScore("EHAI", 0.5)), benchmark.taskScores()),
                () -> assertEquals(1, benchmark.predictions().getFirst().correct()),
                () -> assertEquals(0, benchmark.predictions().get(1).correct()),
                () -> assertEquals("Use standard care.", benchmark.predictions().getFirst().prediction()),
                () -> assertEquals(List.of("Use standard care.", "Make an unfair assumption."),
                        metric.actualOutputs()));
    }

    @Test
    void constructorRejectsEmptyTaskGoldens() {
        var thrown = assertThrows(IllegalArgumentException.class,
                () -> new EquityMedQA(Map.of(), new ScriptedMetric()));

        assertTrue(thrown.getMessage().contains("taskGoldens"));
    }

    private static final class ScriptedModel implements EvaluationModel {
        private final Queue<String> responses;

        private ScriptedModel(String... responses) {
            this.responses = new ArrayDeque<>(List.of(responses));
        }

        @Override
        public String generate(String prompt) {
            return responses.remove();
        }
    }

    private static final class ScriptedMetric implements Metric {
        private final Queue<Double> scores;
        private final List<String> actualOutputs = new ArrayList<>();

        private ScriptedMetric(double... scores) {
            this.scores = new ArrayDeque<>();
            for (var score : scores) {
                this.scores.add(score);
            }
        }

        @Override
        public MetricResult measure(LlmTestCase testCase) {
            actualOutputs.add(testCase.actualOutput());
            var score = scores.remove();
            return new MetricResult("Bias", score, 0.0, score == 0.0, null);
        }

        private List<String> actualOutputs() {
            return List.copyOf(actualOutputs);
        }
    }
}
