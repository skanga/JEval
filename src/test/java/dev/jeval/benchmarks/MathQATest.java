package dev.jeval.benchmarks;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.EvaluationModel;
import dev.jeval.Golden;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import org.junit.jupiter.api.Test;

class MathQATest {

    @Test
    void evaluateScoresTaskGoldensAndStoresPredictionsLikeDeepEval() {
        var benchmark = new MathQA(Map.of("general", List.of(
                Golden.builder("Question: 2 + 2?\na ) 4\nb ) 5\nAnswer:")
                        .expectedOutput("a")
                        .build(),
                Golden.builder("Question: 3 + 3?\na ) 5\nb ) 6\nAnswer:")
                        .expectedOutput("b")
                        .build())));
        var model = new ScriptedModel("a", "a");

        var result = benchmark.evaluate(model);

        assertAll(
                () -> assertEquals(0.5, result.overallAccuracy()),
                () -> assertEquals(0.5, benchmark.overallScore()),
                () -> assertEquals(2, benchmark.predictions().size()),
                () -> assertEquals("general", benchmark.predictions().getFirst().task()),
                () -> assertEquals("a", benchmark.predictions().getFirst().prediction()),
                () -> assertEquals(1, benchmark.predictions().getFirst().correct()),
                () -> assertEquals(0, benchmark.predictions().get(1).correct()),
                () -> assertEquals(List.of(new BenchmarkTaskScore("general", 0.5)), benchmark.taskScores()));
    }

    @Test
    void evaluateUsesBatchGenerateWhenBatchSizeIsProvided() {
        var benchmark = new MathQA(Map.of("gain", List.of(
                Golden.builder("one").expectedOutput("a").build(),
                Golden.builder("two").expectedOutput("b").build(),
                Golden.builder("three").expectedOutput("c").build())));
        var model = new BatchModel(List.of("a", "b"), List.of("d"));

        var result = benchmark.evaluate(model, 2);

        assertAll(
                () -> assertEquals(2.0 / 3.0, result.overallAccuracy()),
                () -> assertEquals(List.of(List.of("one", "two"), List.of("three")), model.batches()),
                () -> assertEquals(0, benchmark.predictions().get(2).correct()));
    }

    @Test
    void constructorRejectsEmptyTaskGoldens() {
        var thrown = assertThrows(IllegalArgumentException.class, () -> new MathQA(Map.of()));

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
