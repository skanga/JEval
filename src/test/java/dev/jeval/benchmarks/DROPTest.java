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

class DROPTest {

    @Test
    void evaluateScoresAcceptedAnswersAndStoresPredictionsLikeDeepEval() {
        var benchmark = new DROP(Map.of("history", List.of(
                Golden.builder("Passage: The game was in 1999.\nQuestion: What year?")
                        .expectedOutput("1999,nineteen ninety nine")
                        .build(),
                Golden.builder("Passage: Paris hosted the event.\nQuestion: Which city?")
                        .expectedOutput("Paris")
                        .build())));
        var model = new ScriptedModel("nineteen ninety nine", "London");

        var result = benchmark.evaluate(model);

        assertAll(
                () -> assertEquals(0.5, result.overallAccuracy()),
                () -> assertEquals(0.5, benchmark.overallScore()),
                () -> assertEquals(2, benchmark.predictions().size()),
                () -> assertEquals("history", benchmark.predictions().getFirst().task()),
                () -> assertEquals("nineteen ninety nine", benchmark.predictions().getFirst().prediction()),
                () -> assertEquals(1, benchmark.predictions().getFirst().correct()),
                () -> assertEquals(0, benchmark.predictions().get(1).correct()),
                () -> assertEquals(List.of(new BenchmarkTaskScore("history", 0.5)), benchmark.taskScores()));
    }

    @Test
    void evaluateUsesBatchGenerateWhenBatchSizeIsProvided() {
        var benchmark = new DROP(Map.of("sports", List.of(
                Golden.builder("one").expectedOutput("first").build(),
                Golden.builder("two").expectedOutput("second").build(),
                Golden.builder("three").expectedOutput("third").build())));
        var model = new BatchModel(List.of("first", "second"), List.of("wrong"));

        var result = benchmark.evaluate(model, 2);

        assertAll(
                () -> assertEquals(2.0 / 3.0, result.overallAccuracy()),
                () -> assertEquals(List.of(List.of("one", "two"), List.of("three")), model.batches()),
                () -> assertEquals(0, benchmark.predictions().get(2).correct()));
    }

    @Test
    void constructorRejectsEmptyTaskGoldens() {
        var thrown = assertThrows(IllegalArgumentException.class, () -> new DROP(Map.of()));

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
