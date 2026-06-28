package dev.jeval.metrics.dag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.SingleTurnParam;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DagUtilsTest {

    @Test
    void validatesDagFromRoots() {
        var root = new TaskNode(
                "Extract",
                "X",
                List.of(new BinaryJudgementNode("?", List.of(new VerdictNode(false, 0), new VerdictNode(true, 10)))),
                List.of(SingleTurnParam.INPUT),
                null);

        assertTrue(DagUtils.isValidDagFromRoots(List.of(root)));
    }

    @Test
    void extractsRequiredParamsFromTaskAndJudgementNodes() {
        var judgement = new NonBinaryJudgementNode(
                "Evaluate",
                List.of(new VerdictNode("A", 10), new VerdictNode("B", 0)),
                List.of(SingleTurnParam.EXPECTED_OUTPUT));
        var task = new TaskNode(
                "Analyze",
                "Result",
                List.of(judgement),
                List.of(SingleTurnParam.INPUT, SingleTurnParam.ACTUAL_OUTPUT),
                null);

        assertEquals(
                Set.of(SingleTurnParam.INPUT, SingleTurnParam.ACTUAL_OUTPUT, SingleTurnParam.EXPECTED_OUTPUT),
                DagUtils.extractRequiredParams(List.of(task)));
    }

    @Test
    void copyGraphDeepCopiesNodesAndPreservesSharedChildren() {
        var shared = new BinaryJudgementNode(
                "Inner?",
                List.of(new VerdictNode(false, 0), new VerdictNode(true, 10)),
                List.of(SingleTurnParam.ACTUAL_OUTPUT),
                "shared");
        var outer = new NonBinaryJudgementNode(
                "Outer?",
                List.of(new VerdictNode("left", null, shared), new VerdictNode("right", null, shared), new VerdictNode("none", 0)),
                List.of(SingleTurnParam.INPUT),
                "outer");
        var root = new TaskNode("Top", "Output", List.of(outer), List.of(SingleTurnParam.EXPECTED_OUTPUT), "root");
        var dag = new DeepAcyclicGraph(List.of(root));

        var copy = DagUtils.copyGraph(dag);
        var copiedRoot = (TaskNode) copy.rootNodes().getFirst();
        var copiedOuter = (NonBinaryJudgementNode) copiedRoot.children().getFirst();
        var copiedLeft = copiedOuter.children().get(0);
        var copiedRight = copiedOuter.children().get(1);
        var copiedShared = (BinaryJudgementNode) copiedLeft.child();

        assertNotSame(dag, copy);
        assertNotSame(root, copiedRoot);
        assertNotSame(outer, copiedOuter);
        assertNotSame(shared, copiedShared);
        assertSame(copiedShared, copiedRight.child());
        assertEquals("Top", copiedRoot.instructions());
        assertEquals("root", copiedRoot.label());
        assertEquals("outer", copiedOuter.label());
        assertEquals("shared", copiedShared.label());
        assertEquals(List.of(SingleTurnParam.EXPECTED_OUTPUT), copiedRoot.evaluationParams());
        assertEquals(List.of(SingleTurnParam.INPUT), copiedOuter.evaluationParams());
        assertEquals(List.of(SingleTurnParam.ACTUAL_OUTPUT), copiedShared.evaluationParams());
    }
}
