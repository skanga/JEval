package dev.jeval.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.jeval.Golden;
import dev.jeval.Metric;
import dev.jeval.MetricResult;
import dev.jeval.prompt.Prompt;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OptimizerScorerTest {

    @Test
    void generateUsesDefaultModulePromptWhenPresent() {
        var expectedPrompt = new Prompt("default", "Default");
        var otherPrompt = new Prompt("other", "Other");
        var prompts = new LinkedHashMap<String, Prompt>();
        prompts.put("other", otherPrompt);
        prompts.put(OptimizerScorer.DEFAULT_MODULE_ID, expectedPrompt);
        var golden = Golden.builder("question").build();
        var scorer = new OptimizerScorer((prompt, callbackGolden) -> {
            assertSame(expectedPrompt, prompt);
            assertSame(golden, callbackGolden);
            return "answer";
        }, List.of(scoringMetric(1.0)));

        assertEquals("answer", scorer.generate(prompts, golden));
    }

    @Test
    void generateUsesFirstPromptWhenDefaultModuleMissing() {
        var firstPrompt = new Prompt("first", "First");
        var prompts = new LinkedHashMap<String, Prompt>();
        prompts.put("first", firstPrompt);
        prompts.put("second", new Prompt("second", "Second"));
        var scorer = new OptimizerScorer((prompt, golden) -> {
            assertSame(firstPrompt, prompt);
            return "answer";
        }, List.of(scoringMetric(1.0)));

        assertEquals("answer", scorer.generate(prompts, Golden.builder("question").build()));
    }

    @Test
    void generateRejectsEmptyPromptMap() {
        var scorer = new OptimizerScorer((prompt, golden) -> "answer", List.of(scoringMetric(1.0)));

        assertThrows(IllegalArgumentException.class,
                () -> scorer.generate(new LinkedHashMap<>(), Golden.builder("question").build()));
    }

    @Test
    void scoreMinibatchAveragesMetricScores() {
        var prompt = new Prompt("answer", "Answer");
        var config = new PromptConfiguration("root", null, new LinkedHashMap<>(Map.of("module", prompt)));
        var scorer = new OptimizerScorer((ignored, golden) -> ((Golden) golden).expectedOutput(), List.of(
                actualEqualsExpectedMetric(),
                scoringMetric(0.5)));

        var score = scorer.scoreMinibatch(config, List.of(
                Golden.builder("q1").expectedOutput("a").build(),
                Golden.builder("q2").expectedOutput("b").build()));

        assertEquals(0.75, score);
    }

    @Test
    void scoreParetoReturnsOneScorePerGolden() {
        var prompt = new Prompt("answer", "Answer");
        var config = new PromptConfiguration("root", null, new LinkedHashMap<>(Map.of("module", prompt)));
        var scorer = new OptimizerScorer((ignored, golden) -> ((Golden) golden).expectedOutput(), List.of(actualEqualsExpectedMetric()));

        assertEquals(List.of(1.0, 1.0), scorer.scorePareto(config, List.of(
                Golden.builder("q1").expectedOutput("a").build(),
                Golden.builder("q2").expectedOutput("b").build())));
    }

    @Test
    void scoreMinibatchReturnsZeroForEmptyInput() {
        var scorer = new OptimizerScorer((prompt, golden) -> "answer", List.of(scoringMetric(1.0)));
        var config = new PromptConfiguration("root", null, new LinkedHashMap<>(
                Map.of(OptimizerScorer.DEFAULT_MODULE_ID, new Prompt("answer", "Answer"))));

        assertEquals(0.0, scorer.scoreMinibatch(config, List.of()));
    }

    private static Metric actualEqualsExpectedMetric() {
        return testCase -> new MetricResult(
                "actual-equals-expected",
                testCase.expectedOutput().equals(testCase.actualOutput()) ? 1.0 : 0.0,
                0.5,
                testCase.expectedOutput().equals(testCase.actualOutput()),
                null);
    }

    private static Metric scoringMetric(double score) {
        return ignored -> new MetricResult("score", score, 0.5, score >= 0.5, null);
    }
}
