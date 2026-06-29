package dev.jeval.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.jeval.Golden;
import dev.jeval.Metric;
import dev.jeval.MetricResult;
import dev.jeval.optimizer.algorithms.COPRO;
import dev.jeval.prompt.Prompt;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class COPROExecutionTest {

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
                new COPRO(1, 1, 1, 123));

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
    void executeAcceptsBestBootstrapCandidateWhenCallbackConfigured() {
        var prompt = new Prompt("answer", "Answer briefly");
        var goldens = List.of(
                Golden.builder("q1").expectedOutput("a").build(),
                Golden.builder("q2").expectedOutput("b").build());
        Metric metric = testCase -> new MetricResult(
                "exact",
                testCase.expectedOutput().equals(testCase.actualOutput()) ? 1.0 : 0.0,
                1.0,
                testCase.expectedOutput().equals(testCase.actualOutput()),
                null);
        var responses = new ArrayDeque<>(List.of(
                "{\"guidelines\":[\"Add citations\"]}",
                "{\"revised_prompt\":\"Answer with citations\"}"));
        var optimizer = new PromptOptimizer(
                (callbackPrompt, golden) -> callbackPrompt.textTemplate().contains("citations")
                        ? ((Golden) golden).expectedOutput()
                        : "wrong",
                List.of(metric),
                new COPRO(1, 1, 1, 123, promptText -> responses.removeFirst()));

        var bestPrompt = optimizer.optimize(prompt, goldens);
        var report = optimizer.optimizationReport();

        assertEquals("Answer with citations", bestPrompt.textTemplate());
        assertEquals(List.of(1.0, 1.0), report.paretoScores().get(report.bestId()));
        assertNull(report.parents().get(report.bestId()));
        assertEquals(List.of(), report.acceptedIterations());
    }
}
