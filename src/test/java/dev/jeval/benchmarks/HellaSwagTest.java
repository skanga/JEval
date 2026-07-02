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

class HellaSwagTest {

    @Test
    void evaluateScoresTasksAndOverallAccuracyLikeDeepEval() {
        var taskGoldens = new LinkedHashMap<String, List<Golden>>();
        taskGoldens.put("Applying sunscreen", List.of(
                Golden.builder("A person pours lotion. What happens next?\nA. applies it\nB. sleeps").expectedOutput("A").build(),
                Golden.builder("A person opens a book. What happens next?\nA. reads\nB. swims").expectedOutput("A").build()));
        taskGoldens.put("Disc dog", List.of(
                Golden.builder("A dog sees a disc. What happens next?\nA. bakes\nB. jumps").expectedOutput("B").build()));
        var benchmark = new HellaSwag(taskGoldens);
        var model = new ScriptedModel("A", "B", "B");

        var result = benchmark.evaluate(model);

        assertAll(
                () -> assertEquals(2.0 / 3.0, result.overallAccuracy()),
                () -> assertEquals(2.0 / 3.0, benchmark.overallScore()),
                () -> assertEquals(3, benchmark.predictions().size()),
                () -> assertEquals("Applying sunscreen", benchmark.predictions().getFirst().task()),
                () -> assertEquals(List.of(
                        new BenchmarkTaskScore("Applying sunscreen", 0.5),
                        new BenchmarkTaskScore("Disc dog", 1.0)),
                        benchmark.taskScores()));
    }

    @Test
    void evaluateHonorsProblemsPerTaskLimit() {
        var benchmark = new HellaSwag(Map.of("task", List.of(
                Golden.builder("one").expectedOutput("A").build(),
                Golden.builder("two").expectedOutput("B").build())),
                1);
        var model = new ScriptedModel("A", "B");

        var result = benchmark.evaluate(model);

        assertAll(
                () -> assertEquals(1.0, result.overallAccuracy()),
                () -> assertEquals(1, benchmark.predictions().size()),
                () -> assertEquals(List.of(prompt("task", "one")), model.prompts()));
    }

    @Test
    void evaluateUsesBatchGenerateWhenBatchSizeIsProvided() {
        var benchmark = new HellaSwag(Map.of("task", List.of(
                Golden.builder("one").expectedOutput("A").build(),
                Golden.builder("two").expectedOutput("B").build(),
                Golden.builder("three").expectedOutput("C").build())));
        var model = new BatchModel(List.of("A", "B"), List.of("D"));

        var result = benchmark.evaluate(model, 2);

        assertAll(
                () -> assertEquals(2.0 / 3.0, result.overallAccuracy()),
                () -> assertEquals(List.of(List.of(prompt("task", "one"), prompt("task", "two")),
                        List.of(prompt("task", "three"))), model.batches()),
                () -> assertEquals(0, benchmark.predictions().get(2).correct()));
    }

    @Test
    void evaluateUsesDeepEvalTaskPromptShotsAndConfinementForGeneration() {
        var benchmark = new HellaSwag(Map.of("Applying sunscreen", List.of(
                Golden.builder("A person pours lotion.\nA. applies it\nB. sleeps\nAnswer:")
                        .expectedOutput("A")
                        .build())),
                null,
                List.of(Golden.builder("Someone opens a bottle.\nA. drinks\nB. runs\nAnswer:")
                        .expectedOutput("A")
                        .build()),
                1);
        var model = new ScriptedModel("A");

        benchmark.evaluate(model);

        var prompt = model.prompts().getFirst();
        assertAll(
                () -> assertTrue(prompt.startsWith(
                        "The following are multiple choice questions (with answers) are sentence completion problems about Applying sunscreen.")),
                () -> assertTrue(prompt.contains("Someone opens a bottle.\nA. drinks\nB. runs\nAnswer: A")),
                () -> assertTrue(prompt.contains("A person pours lotion.\nA. applies it\nB. sleeps\nAnswer:")),
                () -> assertTrue(prompt.endsWith("Output 'A', 'B', 'C', or 'D'. Full answer not needed.")));
    }

    @Test
    void constructorRejectsEmptyTaskGoldens() {
        var thrown = assertThrows(IllegalArgumentException.class, () -> new HellaSwag(Map.of()));

        assertTrue(thrown.getMessage().contains("taskGoldens"));
    }

    private static String prompt(String task, String input) {
        return "The following are multiple choice questions (with answers) "
                + "are sentence completion problems about %s.\n\n%s\n\n"
                        .formatted(task, input)
                + "Output 'A', 'B', 'C', or 'D'. Full answer not needed.";
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
