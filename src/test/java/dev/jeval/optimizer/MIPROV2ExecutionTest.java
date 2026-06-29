package dev.jeval.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.Golden;
import dev.jeval.Metric;
import dev.jeval.MetricResult;
import dev.jeval.optimizer.algorithms.MIPROV2;
import dev.jeval.prompt.Prompt;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MIPROV2ExecutionTest {

    @Test
    void executeScoresRootConfigurationAndReturnsReport() {
        var prompt = new Prompt("answer", "Answer");
        var goldens = List.of(
                Golden.builder("q1").expectedOutput("a").build(),
                Golden.builder("q2").expectedOutput("b").build());
        Metric metric = testCase -> new MetricResult(
                "exact",
                testCase.expectedOutput().equals(testCase.actualOutput()) ? 1.0 : 0.0,
                1.0,
                testCase.expectedOutput().equals(testCase.actualOutput()),
                null);
        var optimizer = new PromptOptimizer(
                (callbackPrompt, golden) -> ((Golden) golden).expectedOutput(),
                List.of(metric),
                new MIPROV2(1, 1, 0, 0, 1, 1, 1, 123));

        var bestPrompt = optimizer.optimize(prompt, goldens);
        var report = optimizer.optimizationReport();

        assertSame(prompt, bestPrompt);
        UUID.fromString(report.optimizationId());
        UUID.fromString(report.bestId());
        assertEquals(List.of(1.0, 1.0), report.paretoScores().get(report.bestId()));
        assertNull(report.parents().get(report.bestId()));
        assertSame(prompt, report.promptConfigurations()
                .get(report.bestId())
                .prompts()
                .get(OptimizerScorer.DEFAULT_MODULE_ID));
    }

    @Test
    void executeEvaluatesInstructionCandidatesAndReturnsBestPrompt() {
        var prompt = new Prompt("answer", "Answer {{input}}");
        var goldens = List.of(
                Golden.builder("q1").expectedOutput("a1").build(),
                Golden.builder("q2").expectedOutput("a2").build());
        var proposalCalls = new AtomicInteger();
        Metric metric = testCase -> {
            var success = testCase.actualOutput().equals(testCase.expectedOutput());
            return new MetricResult("exact", success ? 1.0 : 0.0, 1.0, success, null);
        };
        var optimizer = new PromptOptimizer(
                (callbackPrompt, golden) -> {
                    if (golden == null) {
                        var call = proposalCalls.incrementAndGet();
                        if (call == 1) {
                            return "{\"summary\":\"answer lookup\"}";
                        }
                        return "{\"revised_instruction\":\"Always answer from the expected output.\"}";
                    }
                    var actualPrompt = callbackPrompt.textTemplate();
                    return actualPrompt.contains("Always answer")
                            ? ((Golden) golden).expectedOutput()
                            : "wrong";
                },
                List.of(metric),
                new MIPROV2(3, 2, 0, 0, 1, 2, 1, 123));

        var bestPrompt = optimizer.optimize(prompt, goldens);
        var report = optimizer.optimizationReport();

        assertEquals("Always answer from the expected output.", bestPrompt.textTemplate());
        assertTrue(report.acceptedIterations().stream()
                .anyMatch(iteration -> iteration.before() == 0.0 && iteration.after() == 1.0));
        assertTrue(report.promptConfigurations().values().stream()
                .anyMatch(snapshot -> snapshot.prompts().get(OptimizerScorer.DEFAULT_MODULE_ID)
                        .textTemplate()
                        .equals("Always answer from the expected output.")));
        assertEquals(List.of(1.0, 1.0), report.paretoScores().get(report.bestId()));
    }

    @Test
    void executeEvaluatesBootstrappedDemonstrationPrompts() {
        var prompt = new Prompt("answer", "Answer {{input}}");
        var goldens = List.of(Golden.builder("q1").expectedOutput("a1").build());
        Metric metric = testCase -> {
            var success = testCase.actualOutput().equals(testCase.expectedOutput());
            return new MetricResult("exact", success ? 1.0 : 0.0, 1.0, success, null);
        };
        var optimizer = new PromptOptimizer(
                (callbackPrompt, golden) -> {
                    var actualPrompt = callbackPrompt.textTemplate();
                    if (actualPrompt.contains("Here are some examples:")) {
                        return ((Golden) golden).expectedOutput();
                    }
                    return "wrong";
                },
                List.of(metric),
                new MIPROV2(2, 1, 1, 1, 1, 1, 1, 7));

        var bestPrompt = optimizer.optimize(prompt, goldens);
        var report = optimizer.optimizationReport();

        assertTrue(bestPrompt.textTemplate().contains("Here are some examples:"));
        assertTrue(bestPrompt.textTemplate().contains("Input: q1"));
        assertTrue(bestPrompt.textTemplate().contains("Output: a1"));
        assertEquals(List.of(1.0), report.paretoScores().get(report.bestId()));
    }

    @Test
    void executeTreatsZeroFullEvalStepAsFinalTrialOnly() {
        var prompt = new Prompt("answer", "Answer");
        var goldens = List.of(Golden.builder("q").expectedOutput("a").build());
        Metric metric = testCase -> new MetricResult("exact", 1.0, 1.0, true, null);
        var optimizer = new PromptOptimizer(
                (callbackPrompt, golden) -> "a",
                List.of(metric),
                new MIPROV2(1, 1, 0, 0, 1, 1, 0, 123));

        var bestPrompt = optimizer.optimize(prompt, goldens);

        assertSame(prompt, bestPrompt);
    }
}
