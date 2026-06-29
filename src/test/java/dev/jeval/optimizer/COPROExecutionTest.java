package dev.jeval.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        var copro = new COPRO(1, 1, 1, 123, promptText -> responses.removeFirst());
        var optimizer = new PromptOptimizer(
                (callbackPrompt, golden) -> callbackPrompt.textTemplate().contains("citations")
                        ? ((Golden) golden).expectedOutput()
                        : "wrong",
                List.of(metric),
                copro);

        var bestPrompt = optimizer.optimize(prompt, goldens);
        var report = optimizer.optimizationReport();

        assertEquals("Answer with citations", bestPrompt.textTemplate());
        assertEquals(List.of(1.0, 1.0), report.paretoScores().get(report.bestId()));
        assertNull(report.parents().get(report.bestId()));
        assertEquals(List.of(), report.acceptedIterations());
        assertEquals(1, copro.iterationLog().size());
        var log = copro.iterationLog().getFirst();
        assertEquals(1, log.iteration());
        assertEquals("evaluated", log.outcome());
        assertEquals(0.0, log.before());
        assertEquals(1.0, log.after());
        assertTrue(log.reason().contains("Best Minibatch Candidate ID:"));
        assertTrue(log.elapsed() > 0.0);
    }

    @Test
    void executeUsesHistoryToImproveAfterBootstrapDepth() {
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
                "{\"revised_prompt\":\"Answer with citations\"}",
                "{\"guidelines\":[\"Require source URLs\"]}",
                "{\"revised_prompt\":\"Answer with citations and source URLs\"}"));
        var optimizer = new PromptOptimizer(
                (callbackPrompt, golden) -> {
                    var text = callbackPrompt.textTemplate();
                    if (text.contains("source URLs")) {
                        return ((Golden) golden).expectedOutput();
                    }
                    if (text.contains("citations")) {
                        return ((Golden) golden).input().equals("q1")
                                ? ((Golden) golden).expectedOutput()
                                : "wrong";
                    }
                    return "wrong";
                },
                List.of(metric),
                new COPRO(2, 1, 1, 123, promptText -> {
                    if (responses.size() == 2) {
                        assertTrue(promptText.contains("[PAST ATTEMPTS, SCORES, & EVALUATION FEEDBACK]"));
                        assertTrue(promptText.contains("Answer with citations"));
                    }
                    return responses.removeFirst();
                }));

        var bestPrompt = optimizer.optimize(prompt, goldens);
        var report = optimizer.optimizationReport();

        assertEquals("Answer with citations and source URLs", bestPrompt.textTemplate());
        assertEquals(List.of(1.0, 1.0), report.paretoScores().get(report.bestId()));
        assertEquals(1, report.acceptedIterations().size());
        var accepted = report.acceptedIterations().getFirst();
        assertEquals(report.bestId(), accepted.child());
        assertEquals(OptimizerScorer.DEFAULT_MODULE_ID, accepted.module());
        assertEquals(0.5, accepted.before());
        assertEquals(1.0, accepted.after());
        assertEquals(accepted.parent(), report.parents().get(report.bestId()));
    }

    @Test
    void iterationLogIsClearedBetweenRunsAndReturnedAsImmutableCopy() {
        var prompt = new Prompt("answer", "Answer briefly");
        var firstGoldens = List.of(Golden.builder("q1").expectedOutput("a").build());
        var secondGoldens = List.of(Golden.builder("q2").expectedOutput("a").build());
        Metric metric = testCase -> new MetricResult("exact", 1.0, 1.0, true, null);
        var responses = new ArrayDeque<>(List.of(
                "{\"guidelines\":[\"Keep wording\"]}",
                "{\"revised_prompt\":\"Answer briefly\"}",
                "{\"guidelines\":[\"Keep wording\"]}",
                "{\"revised_prompt\":\"Answer briefly\"}"));
        var copro = new COPRO(1, 1, 1, 123, promptText -> responses.removeFirst());
        var optimizer = new PromptOptimizer(
                (callbackPrompt, golden) -> "a",
                List.of(metric),
                copro);

        optimizer.optimize(prompt, firstGoldens);
        assertEquals(1, copro.iterationLog().size());
        assertThrows(UnsupportedOperationException.class, () -> copro.iterationLog().clear());

        optimizer.optimize(prompt, secondGoldens);

        assertEquals(1, copro.iterationLog().size());
        assertEquals(1, copro.iterationLog().getFirst().iteration());
    }
}
