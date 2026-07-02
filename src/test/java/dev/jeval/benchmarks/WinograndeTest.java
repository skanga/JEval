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

class WinograndeTest {

    @Test
    void evaluateScoresSuppliedGoldensAndStoresPredictionsLikeDeepEval() {
        var benchmark = new Winogrande(List.of(
                Golden.builder("Sentence: The trophy would not fit in the suitcase because _ was too large.\n"
                                + "A. trophy\nB. suitcase\nAnswer:")
                        .expectedOutput("A")
                        .build(),
                Golden.builder("Sentence: Alex gave Taylor the book because _ wanted to read it.\n"
                                + "A. Alex\nB. Taylor\nAnswer:")
                        .expectedOutput("B")
                        .build()));
        var model = new ScriptedModel("A", "A");

        var result = benchmark.evaluate(model);

        assertAll(
                () -> assertEquals(0.5, result.overallAccuracy()),
                () -> assertEquals(0.5, benchmark.overallScore()),
                () -> assertEquals(2, benchmark.predictions().size()),
                () -> assertEquals("A", benchmark.predictions().getFirst().prediction()),
                () -> assertEquals(1, benchmark.predictions().getFirst().correct()),
                () -> assertEquals(0, benchmark.predictions().get(1).correct()),
                () -> assertTrue(model.prompts().getFirst().contains("trophy")));
    }

    @Test
    void evaluateHonorsProblemLimit() {
        var benchmark = new Winogrande(List.of(
                Golden.builder("one").expectedOutput("A").build(),
                Golden.builder("two").expectedOutput("B").build()),
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
    void evaluateUsesDeepEvalFewShotPromptAndConfinement() {
        var benchmark = new Winogrande(List.of(Golden.builder("Sentence: The _ won.\nA. runner\nB. crowd\nAnswer:")
                .expectedOutput("A")
                .build()));
        var model = new ScriptedModel("A");

        benchmark.evaluate(model);

        var prompt = model.prompts().getFirst();
        assertAll(
                () -> assertTrue(prompt.startsWith("Sentence: Ian volunteered to eat Dennis's menudo")),
                () -> assertTrue(prompt.contains("Sentence: The _ won.\nA. runner\nB. crowd\nAnswer:")),
                () -> assertTrue(prompt.endsWith("Output 'A', 'B', 'C', or 'D'. Full answer not needed.")));
    }

    @Test
    void constructorRejectsEmptyGoldens() {
        var thrown = assertThrows(IllegalArgumentException.class, () -> new Winogrande(List.of()));

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
