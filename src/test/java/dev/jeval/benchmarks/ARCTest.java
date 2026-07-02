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

class ARCTest {

    @Test
    void modeValuesMatchDeepEval() {
        assertEquals("ARC-Easy", ARCMode.EASY.value());
        assertEquals("ARC-Challenge", ARCMode.CHALLENGE.value());
    }

    @Test
    void evaluateScoresSuppliedGoldensAndStoresPredictionsLikeDeepEval() {
        var benchmark = new ARC(List.of(
                Golden.builder("What is 2+2?\nA. 3\nB. 4\nC. 5\nD. 6").expectedOutput("B").build(),
                Golden.builder("What color is grass?\nA. blue\nB. green\nC. red\nD. black").expectedOutput("B").build()));
        var model = new ScriptedModel("B", "A");

        var result = benchmark.evaluate(model);

        assertAll(
                () -> assertEquals(0.5, result.overallAccuracy()),
                () -> assertEquals(0.5, benchmark.overallScore()),
                () -> assertEquals(ARCMode.EASY, benchmark.mode()),
                () -> assertEquals(2, benchmark.predictions().size()),
                () -> assertEquals("B", benchmark.predictions().getFirst().prediction()),
                () -> assertEquals(1, benchmark.predictions().getFirst().correct()),
                () -> assertEquals(0, benchmark.predictions().get(1).correct()));
    }

    @Test
    void evaluateHonorsProblemLimitAndMode() {
        var benchmark = new ARC(List.of(
                Golden.builder("one").expectedOutput("A").build(),
                Golden.builder("two").expectedOutput("B").build()),
                1,
                ARCMode.CHALLENGE);
        var model = new ScriptedModel("A", "B");

        var result = benchmark.evaluate(model);

        assertAll(
                () -> assertEquals(1.0, result.overallAccuracy()),
                () -> assertEquals(ARCMode.CHALLENGE, benchmark.mode()),
                () -> assertEquals(1, benchmark.predictions().size()),
                () -> assertEquals(List.of("one"), model.prompts()));
    }

    @Test
    void constructorRejectsEmptyGoldens() {
        var thrown = assertThrows(IllegalArgumentException.class, () -> new ARC(List.of()));

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
