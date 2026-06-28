package dev.jeval.metrics.dag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ConversationalDeepAcyclicGraphTest {

    @Test
    void graphStoresRootNodesAndRejectsEmptyRoots() {
        var root = new ConversationalTaskNode("extract", "Answer", List.of(new ConversationalBinaryJudgementNode(
                "pass?",
                List.of(new ConversationalVerdictNode(true, 10), new ConversationalVerdictNode(false, 0)))));

        var graph = new ConversationalDeepAcyclicGraph(List.of(root));

        assertEquals(List.of(root), graph.rootNodes());
        assertTrue(graph.multiturn());
        assertThrows(IllegalArgumentException.class, () -> new ConversationalDeepAcyclicGraph(List.of()));
    }

    @Test
    void graphRejectsMultipleJudgementRoots() {
        var first = new ConversationalBinaryJudgementNode(
                "first?",
                List.of(new ConversationalVerdictNode(true, 10), new ConversationalVerdictNode(false, 0)));
        var second = new ConversationalNonBinaryJudgementNode(
                "second?",
                List.of(new ConversationalVerdictNode("good", 10), new ConversationalVerdictNode("bad", 0)));

        assertThrows(IllegalArgumentException.class, () -> new ConversationalDeepAcyclicGraph(List.of(first, second)));
    }
}
