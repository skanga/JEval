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
import java.util.Queue;
import org.junit.jupiter.api.Test;

class HumanEvalTest {

    @Test
    void scoresPassAtKOverGeneratedSamplesLikeDeepEval() {
        var goldens = new LinkedHashMap<String, Golden>();
        goldens.put("add", Golden.builder("def add(a, b):\n").expectedOutput("assert add(1, 2) == 3").build());
        var benchmark = new HumanEval(goldens, (golden, prediction) -> prediction.contains("return a + b"), 4);
        var model = new ScriptedModel(
                "def add(a, b):\n    return 0",
                "def add(a, b):\n    return a + b",
                "def add(a, b):\n    return a - b",
                "def add(a, b):\n    return a + b");

        var result = benchmark.evaluate(model, 2);

        assertAll(
                () -> assertEquals(1.0, result.overallAccuracy()),
                () -> assertEquals(1.0, benchmark.overallScore()),
                () -> assertEquals(1, benchmark.taskScores().getFirst().score()),
                () -> assertEquals(2, benchmark.predictions().getFirst().correctSamples()),
                () -> assertEquals(5.0 / 6.0, benchmark.predictions().getFirst().score(), 1e-12),
                () -> assertTrue(model.prompts().stream().allMatch(prompt -> prompt.contains("entry_point: `add`"))));
    }

    @Test
    void evaluateRejectsKGreaterThanSampleCount() {
        var benchmark = new HumanEval(
                new LinkedHashMap<>(java.util.Map.of("add",
                        Golden.builder("def add(a, b):\n").expectedOutput("assert add(1, 2) == 3").build())),
                (golden, prediction) -> true,
                1);

        var thrown = assertThrows(IllegalArgumentException.class, () -> benchmark.evaluate(prompt -> "", 2));

        assertTrue(thrown.getMessage().contains("'k'"));
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
