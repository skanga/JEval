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

class MMLUTest {

    @Test
    void evaluateScoresTasksAndOverallAccuracyLikeDeepEval() {
        var taskGoldens = new LinkedHashMap<String, List<Golden>>();
        taskGoldens.put("abstract_algebra", List.of(
                Golden.builder("2 + 2 = ?\nA. 3\nB. 4\nC. 5\nD. 6").expectedOutput("B").build(),
                Golden.builder("1 + 1 = ?\nA. 2\nB. 3\nC. 4\nD. 5").expectedOutput("A").build()));
        taskGoldens.put("anatomy", List.of(
                Golden.builder("The heart pumps?\nA. air\nB. blood\nC. water\nD. bile").expectedOutput("B").build()));
        var benchmark = new MMLU(taskGoldens);
        var model = new ScriptedModel("B", "B", "B");

        var result = benchmark.evaluate(model);

        assertAll(
                () -> assertEquals(2.0 / 3.0, result.overallAccuracy()),
                () -> assertEquals(2.0 / 3.0, benchmark.overallScore()),
                () -> assertEquals(3, benchmark.predictions().size()),
                () -> assertEquals("abstract_algebra", benchmark.predictions().getFirst().task()),
                () -> assertEquals(1, benchmark.predictions().getFirst().correct()),
                () -> assertEquals(0, benchmark.predictions().get(1).correct()),
                () -> assertEquals(List.of(
                        new BenchmarkTaskScore("abstract_algebra", 0.5),
                        new BenchmarkTaskScore("anatomy", 1.0)),
                        benchmark.taskScores()));
    }

    @Test
    void evaluateHonorsProblemsPerTaskLimit() {
        var benchmark = new MMLU(Map.of("math", List.of(
                Golden.builder("one").expectedOutput("A").build(),
                Golden.builder("two").expectedOutput("B").build())),
                1);
        var model = new ScriptedModel("A", "B");

        var result = benchmark.evaluate(model);

        assertAll(
                () -> assertEquals(1.0, result.overallAccuracy()),
                () -> assertEquals(1, benchmark.predictions().size()),
                () -> assertEquals(1, model.prompts().size()),
                () -> assertTrue(model.prompts().getFirst().contains("one")));
    }

    @Test
    void evaluateUsesBatchGenerateWhenBatchSizeIsProvided() {
        var benchmark = new MMLU(Map.of("math", List.of(
                Golden.builder("one").expectedOutput("A").build(),
                Golden.builder("two").expectedOutput("B").build(),
                Golden.builder("three").expectedOutput("C").build())),
                3);
        var model = new BatchModel(List.of("A", "B"), List.of("D"));

        var result = benchmark.evaluate(model, 2);

        assertAll(
                () -> assertEquals(2.0 / 3.0, result.overallAccuracy()),
                () -> assertEquals(2, model.batches().size()),
                () -> assertEquals(2, model.batches().getFirst().size()),
                () -> assertTrue(model.batches().getFirst().getFirst().contains("one")),
                () -> assertTrue(model.batches().getFirst().get(1).contains("two")),
                () -> assertEquals(1, model.batches().get(1).size()),
                () -> assertTrue(model.batches().get(1).getFirst().contains("three")),
                () -> assertEquals(3, benchmark.predictions().size()),
                () -> assertEquals(0, benchmark.predictions().get(2).correct()));
    }

    @Test
    void evaluateUsesDeepEvalPromptTemplateShotsAndConfinement() {
        var shots = List.of(
                Golden.builder("What is 1 + 1?\nA. 1\nB. 2\nC. 3\nD. 4\nAnswer:")
                        .expectedOutput("B")
                        .build());
        var benchmark = new MMLU(Map.of("abstract_algebra", List.of(
                Golden.builder("What is 2 + 2?\nA. 3\nB. 4\nC. 5\nD. 6\nAnswer:")
                        .expectedOutput("B")
                        .build())),
                null,
                shots,
                1);
        var model = new ScriptedModel("B");

        benchmark.evaluate(model);

        var prompt = model.prompts().getFirst();
        assertAll(
                () -> assertTrue(prompt.startsWith(
                        "The following are multiple choice questions (with answers) about abstract algebra.\n\n")),
                () -> assertTrue(prompt.contains("What is 1 + 1?\nA. 1\nB. 2\nC. 3\nD. 4\nAnswer: B\n\n")),
                () -> assertTrue(prompt.contains("What is 2 + 2?\nA. 3\nB. 4\nC. 5\nD. 6\nAnswer:")),
                () -> assertTrue(prompt.endsWith("Output 'A', 'B', 'C', or 'D'. Full answer not needed.")));
    }

    @Test
    void evaluateRejectsNonPositiveBatchSize() {
        var benchmark = new MMLU(Map.of("math", List.of(Golden.builder("one").expectedOutput("A").build())));

        var thrown = assertThrows(IllegalArgumentException.class, () -> benchmark.evaluate(prompt -> "A", 0));

        assertTrue(thrown.getMessage().contains("batchSize"));
    }

    @Test
    void evaluateRejectsBatchResponseCountMismatch() {
        var benchmark = new MMLU(Map.of("math", List.of(
                Golden.builder("one").expectedOutput("A").build(),
                Golden.builder("two").expectedOutput("B").build())));
        var model = new BatchModel(List.of("A"));

        var thrown = assertThrows(IllegalArgumentException.class, () -> benchmark.evaluate(model, 2));

        assertTrue(thrown.getMessage().contains("one response per prompt"));
    }

    @Test
    void constructorRejectsEmptyTaskGoldens() {
        var thrown = assertThrows(IllegalArgumentException.class, () -> new MMLU(Map.of()));

        assertTrue(thrown.getMessage().contains("taskGoldens"));
    }

    private static final class ScriptedModel implements EvaluationModel {
        private final Queue<String> responses;
        private final Queue<String> prompts = new ArrayDeque<>();

        private ScriptedModel(String... responses) {
            this.responses = new ArrayDeque<>(List.of(responses));
        }

        @Override
        public String generate(String prompt) {
            prompts.add(prompt);
            return responses.remove();
        }

        private List<String> prompts() {
            return List.copyOf(prompts);
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
