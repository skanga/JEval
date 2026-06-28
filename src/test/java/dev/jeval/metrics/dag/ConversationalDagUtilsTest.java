package dev.jeval.metrics.dag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.MultiTurnParam;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConversationalDagUtilsTest {

    @Test
    void validatesDagFromRoots() {
        var root = new ConversationalTaskNode(
                "Extract",
                "X",
                List.of(new ConversationalBinaryJudgementNode(
                        "?",
                        List.of(new ConversationalVerdictNode(false, 0), new ConversationalVerdictNode(true, 10)))),
                List.of(MultiTurnParam.CONTENT),
                null,
                null);

        assertTrue(ConversationalDagUtils.isValidDagFromRoots(List.of(root)));
    }

    @Test
    void extractsRequiredParamsFromTaskAndJudgementNodes() {
        var judgement = new ConversationalNonBinaryJudgementNode(
                "Evaluate",
                List.of(new ConversationalVerdictNode("A", 10), new ConversationalVerdictNode("B", 0)),
                List.of(MultiTurnParam.EXPECTED_OUTCOME),
                null,
                null);
        var task = new ConversationalTaskNode(
                "Analyze",
                "Result",
                List.of(judgement),
                List.of(MultiTurnParam.CONTENT, MultiTurnParam.ROLE),
                null,
                null);

        assertEquals(
                Set.of(MultiTurnParam.CONTENT, MultiTurnParam.ROLE, MultiTurnParam.EXPECTED_OUTCOME),
                ConversationalDagUtils.extractRequiredParams(List.of(task)));
    }

    @Test
    void copyGraphDeepCopiesNodesAndPreservesSharedChildren() {
        var sharedWindow = new TurnWindow(0, 1);
        var rootWindow = new TurnWindow(1, 3);
        var shared = new ConversationalBinaryJudgementNode(
                "Inner?",
                List.of(new ConversationalVerdictNode(false, 0), new ConversationalVerdictNode(true, 10)),
                List.of(MultiTurnParam.CONTEXT),
                sharedWindow,
                "shared");
        var outer = new ConversationalNonBinaryJudgementNode(
                "Outer?",
                List.of(
                        new ConversationalVerdictNode("left", null, shared),
                        new ConversationalVerdictNode("right", null, shared),
                        new ConversationalVerdictNode("none", 0)),
                List.of(MultiTurnParam.EXPECTED_OUTCOME),
                null,
                "outer");
        var root = new ConversationalTaskNode(
                "Top",
                "Output",
                List.of(outer),
                List.of(MultiTurnParam.CONTENT),
                rootWindow,
                "root");
        var dag = new ConversationalDeepAcyclicGraph(List.of(root));

        var copy = ConversationalDagUtils.copyGraph(dag);
        var copiedRoot = (ConversationalTaskNode) copy.rootNodes().getFirst();
        var copiedOuter = (ConversationalNonBinaryJudgementNode) copiedRoot.children().getFirst();
        var copiedLeft = copiedOuter.children().get(0);
        var copiedRight = copiedOuter.children().get(1);
        var copiedShared = (ConversationalBinaryJudgementNode) copiedLeft.child();

        assertNotSame(dag, copy);
        assertNotSame(root, copiedRoot);
        assertNotSame(outer, copiedOuter);
        assertNotSame(shared, copiedShared);
        assertSame(copiedShared, copiedRight.child());
        assertEquals("Top", copiedRoot.instructions());
        assertEquals("root", copiedRoot.label());
        assertEquals("outer", copiedOuter.label());
        assertEquals("shared", copiedShared.label());
        assertEquals(rootWindow, copiedRoot.turnWindow());
        assertEquals(sharedWindow, copiedShared.turnWindow());
        assertEquals(List.of(MultiTurnParam.CONTENT), copiedRoot.evaluationParams());
        assertEquals(List.of(MultiTurnParam.EXPECTED_OUTCOME), copiedOuter.evaluationParams());
        assertEquals(List.of(MultiTurnParam.CONTEXT), copiedShared.evaluationParams());
    }
}
