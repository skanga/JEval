package dev.jeval.benchmarks;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.EvaluationModel;
import dev.jeval.Golden;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import org.junit.jupiter.api.Test;

class TruthfulQATest {

    @Test
    void mc1ScoresExactMatchLikeDeepEval() {
        var taskGoldens = new LinkedHashMap<String, List<Golden>>();
        taskGoldens.put("Health", List.of(
                Golden.builder("Which answer is true?\n1. Drink water\n2. Eat rocks").expectedOutput("1").build(),
                Golden.builder("Which answer is true?\n1. Ignore doctors\n2. Seek care").expectedOutput("2").build()));
        var benchmark = new TruthfulQA(taskGoldens, TruthfulQAMode.MC1);
        var model = new ScriptedModel("1", "1");

        var result = benchmark.evaluate(model);

        assertAll(
                () -> assertEquals(0.5, result.overallAccuracy()),
                () -> assertEquals(0.5, benchmark.overallScore()),
                () -> assertEquals(TruthfulQAMode.MC1, benchmark.mode()),
                () -> assertEquals(2, benchmark.predictions().size()),
                () -> assertEquals(1, benchmark.predictions().getFirst().correct()),
                () -> assertEquals(0, benchmark.predictions().get(1).correct()),
                () -> assertEquals(List.of(new BenchmarkTaskScore("Health", 0.5)), benchmark.taskScores()));
    }

    @Test
    void mc2ScoresTruthIdentificationPercentageLikeDeepEval() {
        var benchmark = new TruthfulQA(Map.of("Misconceptions", List.of(
                Golden.builder("Select all true statements").expectedOutput("[1,2,3]").build())),
                TruthfulQAMode.MC2);
        var model = new ScriptedModel("[1,3]");

        var result = benchmark.evaluate(model);

        assertAll(
                () -> assertEquals(67.0, result.overallAccuracy()),
                () -> assertEquals(67, benchmark.predictions().getFirst().correct()),
                () -> assertEquals(List.of(new BenchmarkTaskScore("Misconceptions", 67.0)), benchmark.taskScores()));
    }

    @Test
    void evaluateUsesBatchGenerateWhenBatchSizeIsProvided() {
        var benchmark = new TruthfulQA(Map.of("Health", List.of(
                Golden.builder("one").expectedOutput("1").build(),
                Golden.builder("two").expectedOutput("2").build(),
                Golden.builder("three").expectedOutput("3").build())),
                TruthfulQAMode.MC1);
        var model = new BatchModel(List.of("1", "2"), List.of("4"));

        var result = benchmark.evaluate(model, 2);

        assertAll(
                () -> assertEquals(2.0 / 3.0, result.overallAccuracy()),
                () -> assertEquals(List.of(List.of("one", "two"), List.of("three")), model.batches()),
                () -> assertEquals(0, benchmark.predictions().get(2).correct()));
    }

    @Test
    void constructorRejectsEmptyTaskGoldens() {
        var thrown = assertThrows(IllegalArgumentException.class,
                () -> new TruthfulQA(Map.of(), TruthfulQAMode.MC1));

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

    private static final class BatchModel implements EvaluationModel {
        private final Queue<List<String>> responses;
        private final Queue<List<String>> batches = new ArrayDeque<>();

        private BatchModel(List<String>... responses) {
            this.responses = new ArrayDeque<>(List.of(responses));
        }

        @Override
        public String generate(String prompt) {
            throw new AssertionError("generate should not be called");
        }

        @Override
        public List<String> batchGenerate(List<String> prompts) {
            batches.add(List.copyOf(prompts));
            return responses.remove();
        }

        private List<List<String>> batches() {
            return List.copyOf(batches);
        }
    }
}
