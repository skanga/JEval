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

class LambadaTest {

    @Test
    void evaluateScoresSuppliedGoldensAndStoresPredictionsLikeDeepEval() {
        var benchmark = new LAMBADA(List.of(
                Golden.builder("The child opened the box and found a tiny")
                        .expectedOutput("kitten")
                        .build(),
                Golden.builder("The chef sharpened the knife before cutting the")
                        .expectedOutput("onion")
                        .build()));
        var model = new ScriptedModel("kitten", "potato");

        var result = benchmark.evaluate(model);

        assertAll(
                () -> assertEquals(0.5, result.overallAccuracy()),
                () -> assertEquals(0.5, benchmark.overallScore()),
                () -> assertEquals(2, benchmark.predictions().size()),
                () -> assertEquals("kitten", benchmark.predictions().getFirst().prediction()),
                () -> assertEquals(1, benchmark.predictions().getFirst().correct()),
                () -> assertEquals(0, benchmark.predictions().get(1).correct()),
                () -> assertTrue(model.prompts().getFirst().contains("tiny")));
    }

    @Test
    void evaluateHonorsProblemLimit() {
        var benchmark = new LAMBADA(List.of(
                Golden.builder("one").expectedOutput("alpha").build(),
                Golden.builder("two").expectedOutput("beta").build()),
                1);
        var model = new ScriptedModel("alpha", "beta");

        var result = benchmark.evaluate(model);

        assertAll(
                () -> assertEquals(1.0, result.overallAccuracy()),
                () -> assertEquals(1, benchmark.predictions().size()),
                () -> assertEquals(1, model.prompts().size()),
                () -> assertTrue(model.prompts().getFirst().contains("one")));
    }

    @Test
    void evaluateUsesDeepEvalFewShotPromptAndConfinement() {
        var benchmark = new LAMBADA(List.of(
                Golden.builder("Context: The child opened the box.\nTarget Sentence: Inside was a tiny ____ \nTarget Word:")
                        .expectedOutput("kitten")
                        .build()));
        var model = new ScriptedModel("kitten");

        benchmark.evaluate(model);

        var prompt = model.prompts().getFirst();
        assertAll(
                () -> assertTrue(prompt.startsWith("Context: her pay for the evening was almost double")),
                () -> assertTrue(prompt.contains("Target Sentence: brian and max were a lot of fun")),
                () -> assertTrue(prompt.contains("Target Word: cake")),
                () -> assertTrue(prompt.contains("Context: The child opened the box.")),
                () -> assertTrue(prompt.endsWith("Output the target word! Do not include punctuations.")));
    }

    @Test
    void constructorRejectsEmptyGoldens() {
        var thrown = assertThrows(IllegalArgumentException.class, () -> new LAMBADA(List.of()));

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
