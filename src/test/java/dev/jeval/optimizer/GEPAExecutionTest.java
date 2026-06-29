package dev.jeval.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.jeval.DeepEvalException;
import dev.jeval.Golden;
import dev.jeval.Metric;
import dev.jeval.MetricResult;
import dev.jeval.optimizer.algorithms.GEPA;
import dev.jeval.optimizer.policies.TieBreaker;
import dev.jeval.prompt.Prompt;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GEPAExecutionTest {

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
                new GEPA(1, 1, 1, 123, 1, TieBreaker.PREFER_CHILD));

        var bestPrompt = optimizer.optimize(prompt, goldens);
        var report = optimizer.optimizationReport();

        assertSame(prompt, bestPrompt);
        UUID.fromString(report.optimizationId());
        UUID.fromString(report.bestId());
        assertEquals(List.of(1.0), report.paretoScores().get(report.bestId()));
        assertNull(report.parents().get(report.bestId()));
        assertSame(prompt, report.promptConfigurations()
                .get(report.bestId())
                .prompts()
                .get(OptimizerScorer.DEFAULT_MODULE_ID));
    }

    @Test
    void executeAcceptsRewrittenChildWhenItImprovesFeedbackAndParetoScores() {
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
        var gepa = new GEPA(1, 1, 1, 123, 1, TieBreaker.PREFER_CHILD,
                rewritePrompt -> "{\"revised_prompt\":\"Answer with a cited fact\"}");
        var optimizer = new PromptOptimizer(
                (callbackPrompt, golden) -> callbackPrompt.textTemplate().contains("cited")
                        ? ((Golden) golden).expectedOutput()
                        : "wrong",
                List.of(metric),
                gepa);

        var bestPrompt = optimizer.optimize(prompt, goldens);
        var report = optimizer.optimizationReport();

        assertEquals("Answer with a cited fact", bestPrompt.textTemplate());
        assertEquals(1, report.acceptedIterations().size());
        var accepted = report.acceptedIterations().getFirst();
        assertEquals(OptimizerScorer.DEFAULT_MODULE_ID, accepted.module());
        assertEquals(0.0, accepted.before());
        assertEquals(1.0, accepted.after());
        assertEquals(report.bestId(), accepted.child());
        assertEquals(1, gepa.iterationLog().size());
        var log = gepa.iterationLog().getFirst();
        assertEquals(0, log.iteration());
        assertEquals("accepted", log.outcome());
        assertEquals("Accepted by Pareto non-domination", log.reason());
        assertEquals(0.0, log.before());
        assertEquals(1.0, log.after());
    }

    @Test
    void executeStopsAfterPatienceConsecutiveParetoRejections() {
        var prompt = new Prompt("answer", "Answer briefly");
        var goldens = List.of(
                Golden.builder("q1").expectedOutput("a").build(),
                Golden.builder("q2").expectedOutput("b").build(),
                Golden.builder("q3").expectedOutput("c").build());
        var paretoGolden = OptimizerUtils.splitGoldens(goldens, 1, new Random(123)).pareto().getFirst();
        Metric metric = testCase -> new MetricResult(
                "exact",
                testCase.expectedOutput().equals(testCase.actualOutput()) ? 1.0 : 0.0,
                1.0,
                testCase.expectedOutput().equals(testCase.actualOutput()),
                null);
        var rewrites = new AtomicInteger();
        var optimizer = new PromptOptimizer(
                (callbackPrompt, golden) -> {
                    var isPareto = golden == paretoGolden;
                    var isChild = callbackPrompt.textTemplate().contains("cited");
                    return isPareto == isChild ? "wrong" : ((Golden) golden).expectedOutput();
                },
                List.of(metric),
                new GEPA(5, 1, 1, 123, 2, TieBreaker.PREFER_CHILD,
                        rewritePrompt -> "{\"revised_prompt\":\"Answer with a cited fact "
                                + rewrites.incrementAndGet() + "\"}"));

        var bestPrompt = optimizer.optimize(prompt, goldens);
        var report = optimizer.optimizationReport();

        assertSame(prompt, bestPrompt);
        assertEquals(2, rewrites.get());
        assertEquals(List.of(), report.acceptedIterations());
    }

    @Test
    void executeRequiresAtLeastTwoGoldens() {
        var prompt = new Prompt("answer", "Answer");
        var golden = Golden.builder("q1").expectedOutput("a").build();
        var optimizer = new PromptOptimizer(
                (callbackPrompt, callbackGolden) -> "wrong",
                List.of((Metric) testCase -> new MetricResult("exact", 0.0, 1.0, false, null)),
                new GEPA(1, 1, 1, 7, 1, TieBreaker.PREFER_CHILD));

        var error = assertThrows(DeepEvalException.class, () -> optimizer.optimize(prompt, List.of(golden)));

        assertEquals("GEPA requires at least 2 goldens to optimize.", error.getMessage());
    }
}
