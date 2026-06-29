package dev.jeval.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void executeTraceReturnsModelOutputAverageScoreAndMetricFeedback() {
        var prompt = new Prompt("answer", "Answer");
        var config = new PromptConfiguration("root", null, new LinkedHashMap<>(
                Map.of(OptimizerScorer.DEFAULT_MODULE_ID, prompt)));
        var scorer = new OptimizerScorer(
                (ignored, golden) -> "actual",
                List.of(
                        (Metric) testCase -> new MetricResult("first", 1.0, 0.5, true, "matched"),
                        (Metric) testCase -> new MetricResult("second", 0.0, 0.5, false, "missed")));

        var trace = scorer.executeTrace(config, Golden.builder("q").expectedOutput("expected").build());

        assertEquals("actual", trace.output());
        assertEquals(0.5, trace.score());
        assertTrue(trace.feedback().contains("- first (1.0): matched"));
        assertTrue(trace.feedback().contains("- second (0.0): missed"));
    }

    @Test
    void evaluateGeneratedMeasuresProvidedOutputWithoutCallingModelAgain() {
        var scorer = new OptimizerScorer(
                (ignored, golden) -> {
                    throw new AssertionError("model callback should not be called");
                },
                List.of(actualEqualsExpectedMetric(), scoringMetric(1.0)));

        var evaluation = scorer.evaluateGenerated(
                Golden.builder("q").expectedOutput("expected").build(),
                "expected");

        assertEquals("expected", evaluation.actual());
        assertEquals(2, evaluation.metricResults().size());
        assertEquals(1.0, evaluation.score());
        assertTrue(evaluation.success());
    }

    @Test
    void getMinibatchFeedbackBuildsDiagnosisFromMetricResults() {
        var prompt = new Prompt("answer", "Answer {question}");
        var config = new PromptConfiguration("root", null, new LinkedHashMap<>(
                Map.of(OptimizerScorer.DEFAULT_MODULE_ID, prompt)));
        var scorer = new OptimizerScorer(
                (ignored, golden) -> ((Golden) golden).input().equals("q1") ? "wrong" : "a2",
                List.of((Metric) testCase -> {
                    var success = testCase.expectedOutput().equals(testCase.actualOutput());
                    return new MetricResult("exact", success ? 1.0 : 0.0, 0.5, success,
                            success ? "matched expected" : "did not match expected");
                }));

        var diagnosis = scorer.getMinibatchFeedback(config, OptimizerScorer.DEFAULT_MODULE_ID, List.of(
                Golden.builder("q1").expectedOutput("a1").build(),
                Golden.builder("q2").expectedOutput("a2").build()));

        assertEquals(2, diagnosis.results().size());
        assertTrue(diagnosis.failures().contains("q1"));
        assertTrue(diagnosis.failures().contains("did not match expected"));
        assertTrue(diagnosis.successes().contains("q2"));
        assertTrue(diagnosis.successes().contains("matched expected"));
        assertTrue(diagnosis.analysis().contains("1 failure"));
        assertTrue(diagnosis.analysis().contains("1 success"));
        assertTrue(diagnosis.results().getFirst().contains("[Input]: q1"));
        assertTrue(diagnosis.results().getFirst().contains("[Expected]: a1"));
        assertTrue(diagnosis.results().getFirst().contains("[Actual Model Output]: wrong"));
        assertTrue(diagnosis.results().getFirst().contains("- exact (Score: 0.0): did not match expected"));
    }

    @Test
    void getMinibatchFeedbackReturnsEmptyDiagnosisForEmptyInput() {
        var scorer = new OptimizerScorer((prompt, golden) -> "answer", List.of(scoringMetric(1.0)));
        var config = new PromptConfiguration("root", null, new LinkedHashMap<>(
                Map.of(OptimizerScorer.DEFAULT_MODULE_ID, new Prompt("answer", "Answer"))));

        assertEquals(new ScorerDiagnosisResult("", "", "", List.of()),
                scorer.getMinibatchFeedback(config, OptimizerScorer.DEFAULT_MODULE_ID, List.of()));
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
