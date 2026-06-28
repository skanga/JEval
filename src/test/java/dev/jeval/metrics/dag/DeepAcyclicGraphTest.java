package dev.jeval.metrics.dag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class DeepAcyclicGraphTest {

    @Test
    void graphStoresRootNodesAndRejectsEmptyRoots() {
        var root = new TaskNode("extract", "Answer", List.of(new BinaryJudgementNode(
                "pass?",
                List.of(new VerdictNode(true, 10), new VerdictNode(false, 0)))));

        var graph = new DeepAcyclicGraph(List.of(root));

        assertEquals(List.of(root), graph.rootNodes());
        assertFalse(graph.multiturn());
        assertThrows(IllegalArgumentException.class, () -> new DeepAcyclicGraph(List.of()));
    }

    @Test
    void graphRejectsMultipleJudgementRoots() {
        var first = new BinaryJudgementNode("first?", List.of(new VerdictNode(true, 10), new VerdictNode(false, 0)));
        var second = new NonBinaryJudgementNode("second?", List.of(new VerdictNode("good", 10), new VerdictNode("bad", 0)));

        assertThrows(IllegalArgumentException.class, () -> new DeepAcyclicGraph(List.of(first, second)));
    }
}
