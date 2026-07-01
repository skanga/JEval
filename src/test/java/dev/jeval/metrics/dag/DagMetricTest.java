package dev.jeval.metrics.dag;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.EvaluationModel;
import dev.jeval.LlmTestCase;
import dev.jeval.Metric;
import dev.jeval.MetricResult;
import dev.jeval.MissingTestCaseParamsException;
import dev.jeval.SingleTurnParam;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DagMetricTest {

    @Test
    void constructorCopiesGraphAndUsesDagNameSuffix() {
        var dag = dag();
        var metric = new DagMetric("Quality", dag);

        assertEquals("Quality [DAG]", metric.name());
        assertEquals(0.5, metric.threshold());
        assertNotSame(dag, metric.dag());
        assertNotSame(dag.rootNodes().getFirst(), metric.dag().rootNodes().getFirst());
        assertEquals(Set.of(SingleTurnParam.INPUT, SingleTurnParam.ACTUAL_OUTPUT), metric.requiredParams());
    }

    @Test
    void strictModeForcesThresholdToOneAndSuffixCanBeDisabled() {
        var metric = new DagMetric("Quality", dag(), 0.2, true, false);

        assertEquals("Quality", metric.name());
        assertEquals(1.0, metric.threshold());
        assertEquals(true, metric.strictMode());
    }

    @Test
    void constructorRejectsNonFiniteThresholds() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new DagMetric("Quality", dag(), Double.NaN, false, true)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new DagMetric("Quality", dag(), Double.POSITIVE_INFINITY, false, true)));
    }

    @Test
    void measureChecksRequiredParamsBeforeExecutionBoundary() {
        var metric = new DagMetric("Quality", dag());

        assertThrows(MissingTestCaseParamsException.class, () -> metric.measure(LlmTestCase.builder("input").build()));
        var error = assertThrows(UnsupportedOperationException.class, () -> metric.measure(LlmTestCase.builder("input")
                .actualOutput("answer")
                .build()));

        assertEquals("DAG metric generation requires a model provider", error.getMessage());
    }

    @Test
    void measureExecutesScoreOnlyVerdictRoot() {
        var metric = new DagMetric("Quality", new DeepAcyclicGraph(List.of(new VerdictNode(true, 7))));

        var result = metric.measure(LlmTestCase.builder("input").build());

        assertEquals("Quality [DAG]", result.name());
        assertEquals(0.7, result.score());
        assertEquals(0.5, result.threshold());
        assertEquals(true, result.success());
    }

    @Test
    void measureGeneratesReasonForScoreOnlyVerdictWhenIncluded() {
        var model = new ScriptedModel(List.of("{\"reason\":\"Deterministic path.\"}"));
        var metric = new DagMetric(
                "Quality",
                new DeepAcyclicGraph(List.of(new VerdictNode(true, 7))),
                model,
                0.5,
                false,
                true,
                true);

        var result = metric.measure(LlmTestCase.builder("input").build());

        assertEquals(0.7, result.score());
        assertEquals("Deterministic path.", result.reason());
        assertEquals(1, model.prompts().size());
        assertTrue(model.prompts().getFirst().contains("0.7"));
        assertTrue(model.prompts().getFirst().contains("Quality [DAG]"));
    }

    @Test
    void measureDelegatesVerdictRootToScoreOnlyChild() {
        var child = new VerdictNode("leaf", 4);
        var metric = new DagMetric("Quality", new DeepAcyclicGraph(List.of(new VerdictNode("route", null, child))));

        var result = metric.measure(LlmTestCase.builder("input").build());

        assertEquals(0.4, result.score());
        assertEquals(false, result.success());
    }

    @Test
    void measureDelegatesVerdictRootToMetricChild() {
        var metric = new DagMetric(
                "Quality",
                new DeepAcyclicGraph(List.of(new VerdictNode("route", new FixedMetric(0.8, "child reason")))),
                null,
                0.5,
                false,
                true,
                true);

        var result = metric.measure(LlmTestCase.builder("input").build());

        assertEquals(0.8, result.score());
        assertEquals("child reason", result.reason());
        assertEquals(true, result.success());
    }

    @Test
    void measureExecutesMultipleRootsAndUsesLastRootScore() {
        var metric = new DagMetric(
                "Quality",
                new DeepAcyclicGraph(List.of(new VerdictNode("first", 2), new VerdictNode("second", 9))));

        var result = metric.measure(LlmTestCase.builder("input").build());

        assertEquals(0.9, result.score());
        assertEquals(true, result.success());
    }

    @Test
    void modelBackedMeasureExecutesTaskThenBinaryJudgementToDeterministicVerdict() {
        var model = new ScriptedModel(List.of(
                "{\"output\":\"short answer\"}",
                "{\"verdict\":true,\"reason\":\"It passes.\"}"));
        var judge = new BinaryJudgementNode(
                "Pass?",
                List.of(new VerdictNode(false, 0), new VerdictNode(true, 8)));
        var root = new TaskNode(
                "Extract",
                "Answer",
                List.of(judge),
                List.of(SingleTurnParam.INPUT),
                "extract");
        var metric = new DagMetric("Quality", new DeepAcyclicGraph(List.of(root)), model);

        var result = metric.measure(LlmTestCase.builder("full input").build());

        assertEquals(0.8, result.score());
        assertEquals(true, result.success());
        assertEquals(2, model.prompts().size());
        assertTrue(model.prompts().get(0).contains("full input"));
        assertTrue(model.prompts().get(0).contains("Input:\nfull input"));
        assertTrue(model.prompts().get(0).contains("--- MULTIMODAL INPUT RULES ---"));
        assertFalse(model.prompts().get(0).contains("input:\nfull input"));
        assertTrue(model.prompts().get(1).contains("short answer"));
        assertTrue(model.prompts().get(1).contains("--- MULTIMODAL INPUT RULES ---"));
    }

    @Test
    void modelBackedMeasureExecutesTaskThenNonBinaryJudgementToDeterministicVerdict() {
        var model = new ScriptedModel(List.of(
                "{\"output\":\"formatted answer\"}",
                "{\"verdict\":\"partial\",\"reason\":\"Mixed.\"}"));
        var judge = new NonBinaryJudgementNode(
                "Classify quality",
                List.of(new VerdictNode("bad", 0), new VerdictNode("partial", 6), new VerdictNode("good", 10)));
        var root = new TaskNode(
                "Extract",
                "Answer",
                List.of(judge),
                List.of(SingleTurnParam.INPUT),
                "extract");
        var metric = new DagMetric("Quality", new DeepAcyclicGraph(List.of(root)), model);

        var result = metric.measure(LlmTestCase.builder("full input").build());

        assertEquals(0.6, result.score());
        assertEquals(true, result.success());
        assertEquals(2, model.prompts().size());
        assertTrue(model.prompts().get(1).contains("formatted answer"));
    }

    @Test
    void modelBackedMeasureWaitsForSharedChildTaskParents() {
        var model = new ScriptedModel(List.of(
                "{\"output\":\"first output\"}",
                "{\"output\":\"second output\"}",
                "{\"output\":\"combined output\"}",
                "{\"verdict\":true,\"reason\":\"Combined.\"}"));
        var judge = new BinaryJudgementNode(
                "Pass?",
                List.of(new VerdictNode(false, 0), new VerdictNode(true, 9)));
        var shared = new TaskNode("Combine parent outputs", "Combined", List.of(judge));
        var first = new TaskNode(
                "Extract first",
                "First",
                List.of(shared),
                List.of(SingleTurnParam.INPUT),
                "first");
        var second = new TaskNode(
                "Extract second",
                "Second",
                List.of(shared),
                List.of(SingleTurnParam.ACTUAL_OUTPUT),
                "second");
        var metric = new DagMetric("Quality", new DeepAcyclicGraph(List.of(first, second)), model);

        var result = metric.measure(LlmTestCase.builder("input").actualOutput("answer").build());

        assertEquals(0.9, result.score());
        assertEquals(4, model.prompts().size());
        assertTrue(model.prompts().get(2).contains("first output"));
        assertTrue(model.prompts().get(2).contains("second output"));
        assertTrue(model.prompts().get(3).contains("combined output"));
    }

    @Test
    void modelBackedMeasureRejectsRootTaskWithoutEvaluationParams() {
        var judge = new BinaryJudgementNode(
                "Pass?",
                List.of(new VerdictNode(false, 0), new VerdictNode(true, 9)));
        var metric = new DagMetric(
                "Quality",
                new DeepAcyclicGraph(List.of(new TaskNode("Extract", "Answer", List.of(judge)))),
                new ScriptedModel(List.of("{\"output\":\"answer\"}")));

        var error = assertThrows(
                IllegalArgumentException.class,
                () -> metric.measure(LlmTestCase.builder("input").build()));

        assertEquals("TaskNode must have either evaluationParams or parent node(s).", error.getMessage());
    }

    @Test
    void modelBackedMeasureRejectsJudgementVerdictWithoutMatchingChild() {
        var model = new ScriptedModel(List.of(
                "{\"output\":\"formatted answer\"}",
                "{\"verdict\":\"missing\",\"reason\":\"No branch.\"}"));
        var judge = new NonBinaryJudgementNode(
                "Classify quality",
                List.of(new VerdictNode("bad", 0), new VerdictNode("good", 10)));
        var root = new TaskNode(
                "Extract",
                "Answer",
                List.of(judge),
                List.of(SingleTurnParam.INPUT),
                "extract");
        var metric = new DagMetric("Quality", new DeepAcyclicGraph(List.of(root)), model);

        var error = assertThrows(IllegalArgumentException.class, () -> metric.measure(LlmTestCase.builder("full input").build()));

        assertEquals("No DAG verdict child matched model verdict: missing", error.getMessage());
    }

    @Test
    void modelBackedMeasureRejectsTaskNodeWithoutChildren() {
        var metric = new DagMetric(
                "Quality",
                new DeepAcyclicGraph(List.of(new TaskNode(
                        "Extract",
                        "Answer",
                        List.of(),
                        List.of(SingleTurnParam.INPUT),
                        "extract"))),
                new ScriptedModel(List.of("{\"output\":\"formatted answer\"}")));

        var error = assertThrows(IllegalArgumentException.class, () -> metric.measure(LlmTestCase.builder("full input").build()));

        assertEquals("TaskNode must have at least one child to execute.", error.getMessage());
    }

    private static DeepAcyclicGraph dag() {
        var judge = new BinaryJudgementNode(
                "Pass?",
                List.of(new VerdictNode(false, 0), new VerdictNode(true, 10)),
                List.of(SingleTurnParam.ACTUAL_OUTPUT));
        var root = new TaskNode(
                "Extract",
                "X",
                List.of(judge),
                List.of(SingleTurnParam.INPUT),
                "extract");
        return new DeepAcyclicGraph(List.of(root));
    }

    private static final class ScriptedModel implements EvaluationModel {
        private final List<String> responses;
        private final List<String> prompts = new ArrayList<>();
        private int index;

        ScriptedModel(List<String> responses) {
            this.responses = responses;
        }

        @Override
        public String generate(String prompt) {
            prompts.add(prompt);
            return responses.get(index++);
        }

        List<String> prompts() {
            return prompts;
        }
    }

    private record FixedMetric(double score, String reason) implements Metric {
        @Override
        public MetricResult measure(LlmTestCase testCase) {
            return new MetricResult("fixed", score, 0.5, score >= 0.5, reason);
        }
    }
}
