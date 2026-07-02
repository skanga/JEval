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

class SQuADTest {

    @Test
    void evaluateScoresWithEvaluatorModelAndStoresPredictionsLikeDeepEval() {
        var benchmark = new SQuAD(Map.of("Apollo_program", List.of(
                Golden.builder("Context: Apollo 11 landed on the Moon.\nQuestion: What landed on the Moon?\nAnswer:")
                        .expectedOutput("Apollo 11")
                        .build(),
                Golden.builder("Context: Neil Armstrong walked first.\nQuestion: Who walked first?\nAnswer:")
                        .expectedOutput("Neil Armstrong")
                        .build())),
                new ScriptedModel("{\"answer\": 1}", "{\"answer\": 0}"));
        var model = new ScriptedModel("Apollo Eleven", "Buzz Aldrin");

        var result = benchmark.evaluate(model);

        assertAll(
                () -> assertEquals(0.5, result.overallAccuracy()),
                () -> assertEquals(0.5, benchmark.overallScore()),
                () -> assertEquals(2, benchmark.predictions().size()),
                () -> assertEquals("Apollo_program", benchmark.predictions().getFirst().task()),
                () -> assertEquals("Apollo Eleven", benchmark.predictions().getFirst().prediction()),
                () -> assertEquals(1, benchmark.predictions().getFirst().correct()),
                () -> assertEquals(0, benchmark.predictions().get(1).correct()),
                () -> assertEquals(List.of(new BenchmarkTaskScore("Apollo_program", 0.5)), benchmark.taskScores()));
    }

    @Test
    void evaluatorPromptIncludesInputPredictionAndExpectedOutput() {
        var evaluator = new RecordingModel("{\"answer\": 1}");
        var benchmark = new SQuAD(Map.of("Teacher", List.of(
                Golden.builder("Context: Ms. Lin teaches math.\nQuestion: Who teaches math?\nAnswer:")
                        .expectedOutput("Ms. Lin")
                        .build())),
                evaluator);

        benchmark.evaluate(prompt -> "Lin");

        assertAll(
                () -> assertTrue(evaluator.prompts().getFirst().contains("Context: Ms. Lin teaches math.")),
                () -> assertTrue(evaluator.prompts().getFirst().contains("Prediction: Lin")),
                () -> assertTrue(evaluator.prompts().getFirst().contains("Expected Output: Ms. Lin")));
    }

    @Test
    void evaluateUsesDeepEvalFewShotPromptAndConfinementForGeneration() {
        var evaluator = new ScriptedModel("{\"answer\": 1}");
        var benchmark = new SQuAD(Map.of("Apollo_program", List.of(
                Golden.builder("Context: Apollo 11 landed on the Moon.\nQuestion: What landed on the Moon?\nAnswer:")
                        .expectedOutput("Apollo 11")
                        .build())),
                evaluator);
        var model = new RecordingModel("Apollo 11");

        benchmark.evaluate(model);

        var prompt = model.prompts().getFirst();
        assertAll(
                () -> assertTrue(prompt.startsWith("Context: After Hurricane Katrina in 2005")),
                () -> assertTrue(prompt.contains("Question: What did Beyonce and Rowland found in 2005?")),
                () -> assertTrue(prompt.contains("Answer: the Survivor Foundation")),
                () -> assertTrue(prompt.contains("Context: Apollo 11 landed on the Moon.")),
                () -> assertTrue(prompt.endsWith(
                        "Output the answer, which should a text segment taken from the context.")));
    }

    @Test
    void constructorRejectsEmptyTaskGoldens() {
        var thrown = assertThrows(IllegalArgumentException.class,
                () -> new SQuAD(Map.of(), prompt -> "{\"answer\": 1}"));

        assertTrue(thrown.getMessage().contains("taskGoldens"));
    }

    private static class ScriptedModel implements EvaluationModel {
        private final Queue<String> responses;

        private ScriptedModel(String... responses) {
            this.responses = new ArrayDeque<>(List.of(responses));
        }

        @Override
        public String generate(String prompt) {
            return responses.remove();
        }
    }

    private static final class RecordingModel extends ScriptedModel {
        private final Queue<String> prompts = new ArrayDeque<>();

        private RecordingModel(String... responses) {
            super(responses);
        }

        @Override
        public String generate(String prompt) {
            prompts.add(prompt);
            return super.generate(prompt);
        }

        private List<String> prompts() {
            return List.copyOf(prompts);
        }
    }
}
