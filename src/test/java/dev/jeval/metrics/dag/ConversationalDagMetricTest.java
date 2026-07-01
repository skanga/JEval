package dev.jeval.metrics.dag;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.ConversationalMetric;
import dev.jeval.ConversationalTestCase;
import dev.jeval.EvaluationModel;
import dev.jeval.MetricResult;
import dev.jeval.MissingTestCaseParamsException;
import dev.jeval.MultiTurnParam;
import dev.jeval.Turn;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConversationalDagMetricTest {

    @Test
    void constructorCopiesGraphAndUsesConversationalDagNameSuffix() {
        var dag = dag();
        var metric = new ConversationalDagMetric("Quality", dag);

        assertEquals("Quality [ConversationalDAG]", metric.name());
        assertEquals(0.5, metric.threshold());
        assertNotSame(dag, metric.dag());
        assertNotSame(dag.rootNodes().getFirst(), metric.dag().rootNodes().getFirst());
        assertEquals(Set.of(MultiTurnParam.CONTENT, MultiTurnParam.EXPECTED_OUTCOME), metric.requiredParams());
    }

    @Test
    void strictModeForcesThresholdToOneAndSuffixCanBeDisabled() {
        var metric = new ConversationalDagMetric("Quality", dag(), 0.2, true, false);

        assertEquals("Quality", metric.name());
        assertEquals(1.0, metric.threshold());
        assertEquals(true, metric.strictMode());
    }

    @Test
    void constructorRejectsNonFiniteThresholds() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ConversationalDagMetric("Quality", dag(), Double.NaN, false, true)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ConversationalDagMetric("Quality", dag(), Double.POSITIVE_INFINITY, false, true)));
    }

    @Test
    void measureChecksRequiredParamsBeforeExecutionBoundary() {
        var metric = new ConversationalDagMetric("Quality", dag());

        assertThrows(
                MissingTestCaseParamsException.class,
                () -> metric.measure(ConversationalTestCase.builder(turns()).build()));
        var error = assertThrows(
                UnsupportedOperationException.class,
                () -> metric.measure(ConversationalTestCase.builder(turns())
                        .expectedOutcome("Be concise")
                        .build()));

        assertEquals("Conversational DAG metric generation requires a model provider", error.getMessage());
    }

    @Test
    void measureValidatesTurnWindowsBeforeExecutionBoundary() {
        var judge = new ConversationalBinaryJudgementNode(
                "Pass?",
                List.of(new ConversationalVerdictNode(false, 0), new ConversationalVerdictNode(true, 10)),
                List.of(),
                new TurnWindow(1, 1),
                null);
        var metric = new ConversationalDagMetric("Quality", new ConversationalDeepAcyclicGraph(List.of(judge)));

        assertThrows(
                IllegalArgumentException.class,
                () -> metric.measure(ConversationalTestCase.builder(turns()).build()));
    }

    @Test
    void turnWindowRejectsRangesOutsideConversation() {
        var turns = turns();

        assertThrows(IllegalArgumentException.class, () -> new TurnWindow(-1, 1).validateAgainst(turns));
        assertThrows(IllegalArgumentException.class, () -> new TurnWindow(1, 0).validateAgainst(turns));
        assertThrows(IllegalArgumentException.class, () -> new TurnWindow(0, 0).validateAgainst(turns));
        assertThrows(IllegalArgumentException.class, () -> new TurnWindow(0, 2).validateAgainst(turns));
    }

    @Test
    void measureExecutesScoreOnlyVerdictRoot() {
        var metric = new ConversationalDagMetric(
                "Quality",
                new ConversationalDeepAcyclicGraph(List.of(new ConversationalVerdictNode(true, 7))));

        var result = metric.measure(ConversationalTestCase.builder(turns()).build());

        assertEquals("Quality [ConversationalDAG]", result.name());
        assertEquals(0.7, result.score());
        assertEquals(0.5, result.threshold());
        assertEquals(true, result.success());
    }

    @Test
    void measureGeneratesReasonForScoreOnlyVerdictWhenIncluded() {
        var model = new ScriptedModel(List.of("{\"reason\":\"Conversation path.\"}"));
        var metric = new ConversationalDagMetric(
                "Quality",
                new ConversationalDeepAcyclicGraph(List.of(new ConversationalVerdictNode(true, 7))),
                model,
                0.5,
                false,
                true,
                true);

        var result = metric.measure(ConversationalTestCase.builder(turns()).build());

        assertEquals(0.7, result.score());
        assertEquals("Conversation path.", result.reason());
        assertEquals(1, model.prompts().size());
        assertTrue(model.prompts().getFirst().contains("0.7"));
        assertTrue(model.prompts().getFirst().contains("Quality [ConversationalDAG]"));
    }

    @Test
    void measureDelegatesVerdictRootToScoreOnlyChild() {
        var child = new ConversationalVerdictNode("leaf", 4);
        var metric = new ConversationalDagMetric(
                "Quality",
                new ConversationalDeepAcyclicGraph(List.of(new ConversationalVerdictNode("route", null, child))));

        var result = metric.measure(ConversationalTestCase.builder(turns()).build());

        assertEquals(0.4, result.score());
        assertEquals(false, result.success());
    }

    @Test
    void measureDelegatesVerdictRootToMetricChild() {
        var metric = new ConversationalDagMetric(
                "Quality",
                new ConversationalDeepAcyclicGraph(List.of(new ConversationalVerdictNode(
                        "route",
                        new FixedConversationalMetric(0.8, "child reason")))),
                null,
                0.5,
                false,
                true,
                true);

        var result = metric.measure(ConversationalTestCase.builder(turns()).build());

        assertEquals(0.8, result.score());
        assertEquals("child reason", result.reason());
        assertEquals(true, result.success());
    }

    @Test
    void measureExecutesMultipleRootsAndUsesLastRootScore() {
        var metric = new ConversationalDagMetric(
                "Quality",
                new ConversationalDeepAcyclicGraph(List.of(
                        new ConversationalVerdictNode("first", 2),
                        new ConversationalVerdictNode("second", 9))));

        var result = metric.measure(ConversationalTestCase.builder(turns()).build());

        assertEquals(0.9, result.score());
        assertEquals(true, result.success());
    }

    @Test
    void modelBackedMeasureExecutesTaskThenBinaryJudgementToDeterministicVerdict() {
        var model = new ScriptedModel(List.of(
                "{\"output\":\"assistant summary\"}",
                "{\"verdict\":true,\"reason\":\"It passes.\"}"));
        var judge = new ConversationalBinaryJudgementNode(
                "Pass?",
                List.of(new ConversationalVerdictNode(false, 0), new ConversationalVerdictNode(true, 8)));
        var root = new ConversationalTaskNode(
                "Extract",
                "Answer",
                List.of(judge),
                List.of(MultiTurnParam.CONTENT),
                null,
                "extract");
        var metric = new ConversationalDagMetric(
                "Quality",
                new ConversationalDeepAcyclicGraph(List.of(root)),
                model);

        var result = metric.measure(ConversationalTestCase.builder(turns()).build());

        assertEquals(0.8, result.score());
        assertEquals(true, result.success());
        assertEquals(2, model.prompts().size());
        assertTrue(model.prompts().get(0).contains("Hi"));
        assertTrue(model.prompts().get(0).contains("Full Conversation:"));
        assertTrue(model.prompts().get(0).contains("Content:\nHi"));
        assertTrue(model.prompts().get(0).contains("--- MULTIMODAL INPUT RULES ---"));
        assertFalse(model.prompts().get(0).contains("{content=Hi}"));
        assertTrue(model.prompts().get(1).contains("assistant summary"));
        assertTrue(model.prompts().get(1).contains("--- MULTIMODAL INPUT RULES ---"));
    }

    @Test
    void modelBackedMeasureExecutesTaskThenNonBinaryJudgementToDeterministicVerdict() {
        var model = new ScriptedModel(List.of(
                "{\"output\":\"assistant summary\"}",
                "{\"verdict\":\"partial\",\"reason\":\"Mixed.\"}"));
        var judge = new ConversationalNonBinaryJudgementNode(
                "Classify quality",
                List.of(
                        new ConversationalVerdictNode("bad", 0),
                        new ConversationalVerdictNode("partial", 6),
                        new ConversationalVerdictNode("good", 10)));
        var root = new ConversationalTaskNode(
                "Extract",
                "Answer",
                List.of(judge),
                List.of(MultiTurnParam.CONTENT),
                null,
                "extract");
        var metric = new ConversationalDagMetric(
                "Quality",
                new ConversationalDeepAcyclicGraph(List.of(root)),
                model);

        var result = metric.measure(ConversationalTestCase.builder(turns()).build());

        assertEquals(0.6, result.score());
        assertEquals(true, result.success());
        assertEquals(2, model.prompts().size());
        assertTrue(model.prompts().get(1).contains("assistant summary"));
    }

    @Test
    void modelBackedMeasureWaitsForSharedChildTaskParents() {
        var model = new ScriptedModel(List.of(
                "{\"output\":\"first summary\"}",
                "{\"output\":\"second summary\"}",
                "{\"output\":\"combined summary\"}",
                "{\"verdict\":true,\"reason\":\"Combined.\"}"));
        var judge = new ConversationalBinaryJudgementNode(
                "Pass?",
                List.of(new ConversationalVerdictNode(false, 0), new ConversationalVerdictNode(true, 9)));
        var shared = new ConversationalTaskNode("Combine parent outputs", "Combined", List.of(judge));
        var first = new ConversationalTaskNode(
                "Extract first",
                "First",
                List.of(shared),
                List.of(MultiTurnParam.CONTENT),
                null,
                "first");
        var second = new ConversationalTaskNode(
                "Extract second",
                "Second",
                List.of(shared),
                List.of(MultiTurnParam.ROLE),
                null,
                "second");
        var metric = new ConversationalDagMetric(
                "Quality",
                new ConversationalDeepAcyclicGraph(List.of(first, second)),
                model);

        var result = metric.measure(ConversationalTestCase.builder(turns()).build());

        assertEquals(0.9, result.score());
        assertEquals(4, model.prompts().size());
        assertTrue(model.prompts().get(2).contains("first summary"));
        assertTrue(model.prompts().get(2).contains("second summary"));
        assertFalse(model.prompts().get(2).contains("{}"));
        assertTrue(model.prompts().get(3).contains("combined summary"));
    }

    @Test
    void modelBackedMeasureRejectsRootTaskWithoutEvaluationParams() {
        var judge = new ConversationalBinaryJudgementNode(
                "Pass?",
                List.of(new ConversationalVerdictNode(false, 0), new ConversationalVerdictNode(true, 9)));
        var metric = new ConversationalDagMetric(
                "Quality",
                new ConversationalDeepAcyclicGraph(List.of(new ConversationalTaskNode(
                        "Extract",
                        "Answer",
                        List.of(judge)))),
                new ScriptedModel(List.of("{\"output\":\"answer\"}")));

        var error = assertThrows(
                IllegalArgumentException.class,
                () -> metric.measure(ConversationalTestCase.builder(turns()).build()));

        assertEquals("ConversationalTaskNode must have either evaluationParams or parent node(s).", error.getMessage());
    }

    @Test
    void modelBackedMeasureRejectsJudgementVerdictWithoutMatchingChild() {
        var model = new ScriptedModel(List.of(
                "{\"output\":\"assistant summary\"}",
                "{\"verdict\":\"missing\",\"reason\":\"No branch.\"}"));
        var judge = new ConversationalNonBinaryJudgementNode(
                "Classify quality",
                List.of(new ConversationalVerdictNode("bad", 0), new ConversationalVerdictNode("good", 10)));
        var root = new ConversationalTaskNode(
                "Extract",
                "Answer",
                List.of(judge),
                List.of(MultiTurnParam.CONTENT),
                null,
                "extract");
        var metric = new ConversationalDagMetric(
                "Quality",
                new ConversationalDeepAcyclicGraph(List.of(root)),
                model);

        var error = assertThrows(
                IllegalArgumentException.class,
                () -> metric.measure(ConversationalTestCase.builder(turns()).build()));

        assertEquals("No conversational DAG verdict child matched model verdict: missing", error.getMessage());
    }

    @Test
    void modelBackedMeasureRejectsTaskNodeWithoutChildren() {
        var metric = new ConversationalDagMetric(
                "Quality",
                new ConversationalDeepAcyclicGraph(List.of(new ConversationalTaskNode(
                        "Extract",
                        "Answer",
                        List.of(),
                        List.of(MultiTurnParam.CONTENT),
                        null,
                        "extract"))),
                new ScriptedModel(List.of("{\"output\":\"assistant summary\"}")));

        var error = assertThrows(
                IllegalArgumentException.class,
                () -> metric.measure(ConversationalTestCase.builder(turns()).build()));

        assertEquals("ConversationalTaskNode must have at least one child to execute.", error.getMessage());
    }

    private static ConversationalDeepAcyclicGraph dag() {
        var judge = new ConversationalBinaryJudgementNode(
                "Pass?",
                List.of(new ConversationalVerdictNode(false, 0), new ConversationalVerdictNode(true, 10)),
                List.of(MultiTurnParam.EXPECTED_OUTCOME),
                null,
                null);
        var root = new ConversationalTaskNode(
                "Extract",
                "X",
                List.of(judge),
                List.of(MultiTurnParam.CONTENT),
                null,
                "extract");
        return new ConversationalDeepAcyclicGraph(List.of(root));
    }

    private static List<Turn> turns() {
        return List.of(new Turn("user", "Hi"), new Turn("assistant", "Hello"));
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

    private record FixedConversationalMetric(double score, String reason) implements ConversationalMetric {
        @Override
        public MetricResult measure(ConversationalTestCase testCase) {
            return new MetricResult("fixed", score, 0.5, score >= 0.5, reason);
        }
    }
}
