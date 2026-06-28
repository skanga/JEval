package dev.jeval.metrics.dag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.MetricResult;
import dev.jeval.MultiTurnParam;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConversationalDagSerializationTest {

    @Test
    void graphSerializesTurnWindowsAndMultiTurnParams() {
        var data = simpleDag().toMap();
        var nodes = nodes(data);

        assertEquals(Set.of("nodes"), data.keySet());
        assertEquals(4, nodes.size());

        var task = nodes.values().stream()
                .filter(node -> "TaskNode".equals(node.get("type")))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of("content"), task.get("evaluation_params"));
        assertEquals(List.of(1, 3), task.get("turn_window"));

        var judgement = nodes.values().stream()
                .filter(node -> "BinaryJudgementNode".equals(node.get("type")))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of("expected_outcome"), judgement.get("evaluation_params"));
        assertEquals(List.of(0, 2), judgement.get("turn_window"));
    }

    @Test
    void graphRoundTripsThroughJson() {
        var rebuilt = ConversationalDeepAcyclicGraph.fromJson(simpleDag().toJson());

        assertTrue(rebuilt.multiturn());
        var root = assertInstanceOf(ConversationalTaskNode.class, rebuilt.rootNodes().getFirst());
        assertEquals("Extract the summary.", root.instructions());
        assertEquals("Summary", root.outputLabel());
        assertEquals("extract", root.label());
        assertEquals(List.of(MultiTurnParam.CONTENT), root.evaluationParams());
        assertEquals(new TurnWindow(1, 3), root.turnWindow());

        var judge = assertInstanceOf(ConversationalBinaryJudgementNode.class, root.children().getFirst());
        assertEquals("Is the assistant helpful?", judge.criteria());
        assertEquals(List.of(MultiTurnParam.EXPECTED_OUTCOME), judge.evaluationParams());
        assertEquals(new TurnWindow(0, 2), judge.turnWindow());
        assertEquals(Set.of(true, false), Set.of(judge.children().get(0).verdict(), judge.children().get(1).verdict()));
        assertEquals(Set.of(0, 10), Set.of(judge.children().get(0).score(), judge.children().get(1).score()));
    }

    @Test
    void sharedJudgementChildSerializesOnceAndRebuildsAsSameObject() {
        var shared = new ConversationalBinaryJudgementNode(
                "Inner check?",
                List.of(new ConversationalVerdictNode(false, 0), new ConversationalVerdictNode(true, 10)),
                List.of(MultiTurnParam.CONTEXT),
                new TurnWindow(0, 1),
                "shared_judge");
        var outer = new ConversationalNonBinaryJudgementNode(
                "Pick a side",
                List.of(
                        new ConversationalVerdictNode("left", null, shared),
                        new ConversationalVerdictNode("right", null, shared),
                        new ConversationalVerdictNode("none", 0)));

        var data = new ConversationalDeepAcyclicGraph(List.of(outer)).toMap();
        var nodes = nodes(data);
        var sharedIds = nodes.entrySet().stream()
                .filter(entry -> "shared_judge".equals(entry.getValue().get("label")))
                .map(Map.Entry::getKey)
                .toList();

        assertEquals(1, sharedIds.size());
        assertEquals(2, nodes.values().stream()
                .filter(node -> node.get("child") instanceof Map<?, ?> child && sharedIds.getFirst().equals(child.get("ref")))
                .count());

        var rebuiltOuter = (ConversationalNonBinaryJudgementNode) ConversationalDeepAcyclicGraph.fromMap(data)
                .rootNodes()
                .getFirst();
        assertSame(rebuiltOuter.children().get(0).child(), rebuiltOuter.children().get(1).child());
    }

    @Test
    void graphRejectsMetricVerdictChildSerialization() {
        var graph = new ConversationalDeepAcyclicGraph(List.of(new ConversationalVerdictNode(
                "metric",
                testCase -> new MetricResult("fixed", 0.8, 0.5, true, "reason"))));

        var error = assertThrows(IllegalArgumentException.class, graph::toMap);

        assertEquals("Metric verdict children cannot be serialized.", error.getMessage());
    }

    private static ConversationalDeepAcyclicGraph simpleDag() {
        var leafFalse = new ConversationalVerdictNode(false, 0);
        var leafTrue = new ConversationalVerdictNode(true, 10);
        var judgement = new ConversationalBinaryJudgementNode(
                "Is the assistant helpful?",
                List.of(leafFalse, leafTrue),
                List.of(MultiTurnParam.EXPECTED_OUTCOME),
                new TurnWindow(0, 2),
                "helpful");
        var root = new ConversationalTaskNode(
                "Extract the summary.",
                "Summary",
                List.of(judgement),
                List.of(MultiTurnParam.CONTENT),
                new TurnWindow(1, 3),
                "extract");
        return new ConversationalDeepAcyclicGraph(List.of(root));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> nodes(Map<String, Object> data) {
        return (Map<String, Map<String, Object>>) data.get("nodes");
    }
}
