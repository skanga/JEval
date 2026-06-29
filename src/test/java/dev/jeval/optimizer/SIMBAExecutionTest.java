package dev.jeval.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.Golden;
import dev.jeval.Metric;
import dev.jeval.MetricResult;
import dev.jeval.optimizer.algorithms.SIMBA;
import dev.jeval.prompt.Prompt;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SIMBAExecutionTest {

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
                new SIMBA(1, 1, 1, 1, 1, 123));

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
    void executeAcceptsIntrospectionRewriteCandidateWhenCallbackConfigured() {
        var prompt = new Prompt("answer", "Answer {{input}}");
        var goldens = List.of(
                Golden.builder("q1").expectedOutput("a").build(),
                Golden.builder("q2").expectedOutput("b").build());
        Metric metric = testCase -> new MetricResult(
                "exact",
                testCase.expectedOutput().equals(testCase.actualOutput()) ? 1.0 : 0.0,
                1.0,
                testCase.expectedOutput().equals(testCase.actualOutput()),
                null);
        var responses = new ArrayDeque<>(List.of("""
                {
                  "discussion": "The rewritten prompt asks for supporting evidence before answering.",
                  "revised_prompt": "Answer {{input}} with evidence"
                }
                """));
        var simba = new SIMBA(1, 2, 1, 1, 1, 123, promptText -> {
            if (!promptText.contains("[WORSE TRAJECTORY (The Failure)]")) {
                throw new AssertionError("Expected SIMBA introspection template");
            }
            return responses.removeFirst();
        });
        var optimizer = new PromptOptimizer(
                (callbackPrompt, golden) -> callbackPrompt.textTemplate().contains("evidence")
                        ? ((Golden) golden).expectedOutput()
                        : "wrong",
                List.of(metric),
                simba);

        var bestPrompt = optimizer.optimize(prompt, goldens);
        var report = optimizer.optimizationReport();

        assertEquals("Answer {{input}} with evidence", bestPrompt.textTemplate());
        assertEquals(List.of(1.0, 1.0), report.paretoScores().get(report.bestId()));
        assertEquals(1, report.acceptedIterations().size());
        var accepted = report.acceptedIterations().getFirst();
        assertEquals(report.bestId(), accepted.child());
        assertEquals(OptimizerScorer.DEFAULT_MODULE_ID, accepted.module());
        assertEquals(0.0, accepted.before());
        assertEquals(1.0, accepted.after());
        assertEquals(accepted.parent(), report.parents().get(report.bestId()));
        assertEquals(1, simba.iterationLog().size());
        var log = simba.iterationLog().getFirst();
        assertEquals(1, log.iteration());
        assertEquals("accepted", log.outcome());
        assertEquals("Evaluated on full dataset.", log.reason());
        assertTrue(log.elapsed() > 0.0);
        assertEquals(0.0, log.before());
        assertEquals(1.0, log.after());
    }

    @Test
    void executeTreatsZeroFullEvalStepAsFinalIterationOnly() {
        var prompt = new Prompt("answer", "Answer {{input}}");
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
                "{\"discussion\":\"ok\",\"revised_prompt\":\"Answer {{input}} with evidence\"}"));
        var simba = new SIMBA(1, 2, 1, 1, 0, 123, promptText -> responses.removeFirst());
        var optimizer = new PromptOptimizer(
                (callbackPrompt, golden) -> callbackPrompt.textTemplate().contains("evidence")
                        ? ((Golden) golden).expectedOutput()
                        : "wrong",
                List.of(metric),
                simba);

        var bestPrompt = optimizer.optimize(prompt, goldens);

        assertEquals("Answer {{input}} with evidence", bestPrompt.textTemplate());
        assertEquals(1, simba.iterationLog().size());
        assertEquals("accepted", simba.iterationLog().getFirst().outcome());
    }

    @Test
    void iterationLogIsClearedBetweenRunsAndReturnedAsImmutableCopy() {
        var prompt = new Prompt("answer", "Answer {{input}}");
        var firstGoldens = List.of(Golden.builder("q1").expectedOutput("a").build());
        var secondGoldens = List.of(Golden.builder("q2").expectedOutput("a").build());
        Metric metric = testCase -> new MetricResult("exact", 1.0, 1.0, true, null);
        var responses = new ArrayDeque<>(List.of(
                "{\"discussion\":\"ok\",\"revised_prompt\":\"Answer {{input}} with evidence\"}",
                "{\"discussion\":\"ok\",\"revised_prompt\":\"Answer {{input}} with evidence\"}"));
        var simba = new SIMBA(1, 1, 1, 1, 1, 123, promptText -> responses.removeFirst());
        var optimizer = new PromptOptimizer(
                (callbackPrompt, golden) -> "a",
                List.of(metric),
                simba);

        optimizer.optimize(prompt, firstGoldens);
        assertEquals(1, simba.iterationLog().size());
        assertThrows(UnsupportedOperationException.class, () -> simba.iterationLog().clear());

        optimizer.optimize(prompt, secondGoldens);

        assertEquals(1, simba.iterationLog().size());
        assertEquals(1, simba.iterationLog().getFirst().iteration());
    }
}
