package dev.jeval.benchmarks;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.EvaluationModel;
import dev.jeval.Golden;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import org.junit.jupiter.api.Test;

class BoolQTest {

    @Test
    void evaluateScoresSuppliedGoldensAndStoresPredictionsLikeDeepEval() {
        var benchmark = new BoolQ(List.of(
                Golden.builder("Passage: Java is statically typed.\nQuestion: Is Java statically typed?")
                        .expectedOutput("Yes")
                        .build(),
                Golden.builder("Passage: The sky is blue.\nQuestion: Is the sky green?")
                        .expectedOutput("No")
                        .build()));
        var model = new ScriptedModel("Yes", "Yes");

        var result = benchmark.evaluate(model);

        assertAll(
                () -> assertEquals(0.5, result.overallAccuracy()),
                () -> assertEquals(0.5, benchmark.overallScore()),
                () -> assertEquals(2, benchmark.predictions().size()),
                () -> assertEquals("Yes", benchmark.predictions().getFirst().prediction()),
                () -> assertEquals(1, benchmark.predictions().getFirst().correct()),
                () -> assertEquals(0, benchmark.predictions().get(1).correct()),
                () -> assertTrue(model.prompts().getFirst().contains("Is Java statically typed?")));
    }

    @Test
    void evaluateHonorsProblemLimit() {
        var benchmark = new BoolQ(List.of(
                Golden.builder("one").expectedOutput("Yes").build(),
                Golden.builder("two").expectedOutput("No").build()),
                1);
        var model = new ScriptedModel("Yes", "No");

        var result = benchmark.evaluate(model);

        assertAll(
                () -> assertEquals(1.0, result.overallAccuracy()),
                () -> assertEquals(1, benchmark.predictions().size()),
                () -> assertEquals(1, model.prompts().size()),
                () -> assertTrue(model.prompts().getFirst().contains("one")));
    }

    @Test
    void evaluateUsesDeepEvalFewShotPromptAndConfinement() {
        var benchmark = new BoolQ(List.of(Golden.builder("Q: Is Java typed?\nP: Java is typed.\nA: ")
                .expectedOutput("Yes")
                .build()));
        var model = new ScriptedModel("Yes");

        benchmark.evaluate(model);

        var prompt = model.prompts().getFirst();
        assertAll(
                () -> assertTrue(prompt.startsWith("Q: do iran and afghanistan speak the same language?")),
                () -> assertTrue(prompt.contains("Q: Is Java typed?\nP: Java is typed.\nA: ")),
                () -> assertTrue(prompt.endsWith("Make sure to output only 'Yes' or 'No'.")));
    }

    @Test
    void constructorRejectsEmptyGoldens() {
        var thrown = assertThrows(IllegalArgumentException.class, () -> new BoolQ(List.of()));

        assertTrue(thrown.getMessage().contains("goldens"));
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
}
