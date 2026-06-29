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

class GSM8KTest {

    @Test
    void evaluateScoresSuppliedGoldensAndStoresPredictionsLikeDeepEval() {
        var benchmark = new GSM8K(List.of(
                Golden.builder("If Sam has 2 apples and buys 3 more, how many apples does Sam have?")
                        .expectedOutput("5")
                        .build(),
                Golden.builder("A box has 10 pencils. Lee gives away 4. How many remain?")
                        .expectedOutput("6")
                        .build()));
        var model = new ScriptedModel("5", "7");

        var result = benchmark.evaluate(model);

        assertAll(
                () -> assertEquals(0.5, result.overallAccuracy()),
                () -> assertEquals(0.5, benchmark.overallScore()),
                () -> assertEquals(2, benchmark.predictions().size()),
                () -> assertEquals("5", benchmark.predictions().getFirst().prediction()),
                () -> assertEquals(1, benchmark.predictions().getFirst().correct()),
                () -> assertEquals(0, benchmark.predictions().get(1).correct()),
                () -> assertTrue(model.prompts().getFirst().contains("apples")));
    }

    @Test
    void evaluateHonorsProblemLimit() {
        var benchmark = new GSM8K(List.of(
                Golden.builder("one").expectedOutput("1").build(),
                Golden.builder("two").expectedOutput("2").build()),
                1);
        var model = new ScriptedModel("1", "2");

        var result = benchmark.evaluate(model);

        assertAll(
                () -> assertEquals(1.0, result.overallAccuracy()),
                () -> assertEquals(1, benchmark.predictions().size()),
                () -> assertEquals(List.of("one"), model.prompts()));
    }

    @Test
    void constructorRejectsEmptyGoldens() {
        var thrown = assertThrows(IllegalArgumentException.class, () -> new GSM8K(List.of()));

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
