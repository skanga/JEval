package dev.jeval.metrics.dag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.MetricResult;
import dev.jeval.SingleTurnParam;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DagSerializationTest {

    @Test
    void nodeAndChildTypeEnumsUseDeepEvalValues() {
        assertEquals("TaskNode", NodeType.TASK.value());
        assertEquals("BinaryJudgementNode", NodeType.BINARY_JUDGEMENT.value());
        assertEquals("NonBinaryJudgementNode", NodeType.NON_BINARY_JUDGEMENT.value());
        assertEquals("VerdictNode", NodeType.VERDICT.value());
        assertEquals("node", ChildType.NODE.value());
        assertEquals("geval", ChildType.GEVAL.value());
        assertEquals("metric", ChildType.METRIC.value());
    }

    @Test
    void graphSerializesToDeepEvalNodeMapShape() {
        var data = simpleDag().toMap();
        var nodes = nodes(data);

        assertEquals(Set.of("nodes"), data.keySet());
        assertEquals(4, nodes.size());
        assertTrue(nodes.values().stream().anyMatch(node -> "TaskNode".equals(node.get("type"))));
        assertTrue(nodes.values().stream().anyMatch(node -> "BinaryJudgementNode".equals(node.get("type"))));
        assertEquals(2, nodes.values().stream().filter(node -> "VerdictNode".equals(node.get("type"))).count());

        var task = nodes.values().stream()
                .filter(node -> "TaskNode".equals(node.get("type")))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of("actual_output"), task.get("evaluation_params"));
    }

    @Test
    void graphRoundTripsThroughJson() {
        var rebuilt = DeepAcyclicGraph.fromJson(simpleDag().toJson());

        assertFalse(rebuilt.multiturn());
        var root = assertInstanceOf(TaskNode.class, rebuilt.rootNodes().getFirst());
        assertEquals("Extract the summary.", root.instructions());
        assertEquals("Summary", root.outputLabel());
        assertEquals("extract", root.label());
        assertEquals(List.of(SingleTurnParam.ACTUAL_OUTPUT), root.evaluationParams());

        var judge = assertInstanceOf(BinaryJudgementNode.class, root.children().getFirst());
        assertEquals("Is the output a summary?", judge.criteria());
        assertEquals(List.of(SingleTurnParam.INPUT, SingleTurnParam.ACTUAL_OUTPUT), judge.evaluationParams());
        assertEquals(Set.of(true, false), Set.of(judge.children().get(0).verdict(), judge.children().get(1).verdict()));
        assertEquals(Set.of(0, 10), Set.of(judge.children().get(0).score(), judge.children().get(1).score()));
    }

    @Test
    void judgementLabelsRoundTrip() {
        var binary = new BinaryJudgementNode(
                "binary?",
                List.of(new VerdictNode(true, 10), new VerdictNode(false, 0)),
                List.of(),
                "binary_label");
        var nonBinary = new NonBinaryJudgementNode(
                "format?",
                List.of(new VerdictNode("bullets", 8), new VerdictNode("none", 0)),
                List.of(SingleTurnParam.ACTUAL_OUTPUT),
                "format_label");

        var data = new DeepAcyclicGraph(List.of(new TaskNode("route", "Route", List.of(binary, nonBinary)))).toMap();
        var nodes = nodes(data);

        assertTrue(nodes.values().stream().anyMatch(node -> "binary_label".equals(node.get("label"))));
        assertTrue(nodes.values().stream().anyMatch(node -> "format_label".equals(node.get("label"))));

        var root = (TaskNode) DeepAcyclicGraph.fromJson(new DeepAcyclicGraph(
                List.of(new TaskNode("route", "Route", List.of(binary, nonBinary)))).toJson()).rootNodes().getFirst();
        assertEquals("binary_label", ((BinaryJudgementNode) root.children().get(0)).label());
        assertEquals("format_label", ((NonBinaryJudgementNode) root.children().get(1)).label());
    }

    @Test
    void sharedJudgementChildSerializesOnceAndRebuildsAsSameObject() {
        var shared = new BinaryJudgementNode(
                "Inner check?",
                List.of(new VerdictNode(false, 0), new VerdictNode(true, 10)),
                List.of(SingleTurnParam.ACTUAL_OUTPUT),
                "shared_judge");
        var outer = new NonBinaryJudgementNode(
                "Pick a side",
                List.of(
                        new VerdictNode("left", null, shared),
                        new VerdictNode("right", null, shared),
                        new VerdictNode("none", 0)));

        var data = new DeepAcyclicGraph(List.of(outer)).toMap();
        var nodes = nodes(data);
        var sharedIds = nodes.entrySet().stream()
                .filter(entry -> "shared_judge".equals(entry.getValue().get("label")))
                .map(Map.Entry::getKey)
                .toList();

        assertEquals(1, sharedIds.size());
        assertEquals(2, nodes.values().stream()
                .filter(node -> node.get("child") instanceof Map<?, ?> child && sharedIds.getFirst().equals(child.get("ref")))
                .count());

        var rebuiltOuter = (NonBinaryJudgementNode) DeepAcyclicGraph.fromMap(data).rootNodes().getFirst();
        assertSame(rebuiltOuter.children().get(0).child(), rebuiltOuter.children().get(1).child());
    }

    @Test
    void graphRejectsMetricVerdictChildSerialization() {
        var graph = new DeepAcyclicGraph(List.of(new VerdictNode(
                "metric",
                testCase -> new MetricResult("fixed", 0.8, 0.5, true, "reason"))));

        var error = assertThrows(IllegalArgumentException.class, graph::toMap);

        assertEquals("Metric verdict children cannot be serialized.", error.getMessage());
    }

    private static DeepAcyclicGraph simpleDag() {
        var leafFalse = new VerdictNode(false, 0);
        var leafTrue = new VerdictNode(true, 10);
        var judgement = new BinaryJudgementNode(
                "Is the output a summary?",
                List.of(leafFalse, leafTrue),
                List.of(SingleTurnParam.INPUT, SingleTurnParam.ACTUAL_OUTPUT));
        var root = new TaskNode(
                "Extract the summary.",
                "Summary",
                List.of(judgement),
                List.of(SingleTurnParam.ACTUAL_OUTPUT),
                "extract");
        return new DeepAcyclicGraph(List.of(root));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> nodes(Map<String, Object> data) {
        return (Map<String, Map<String, Object>>) data.get("nodes");
    }
}
