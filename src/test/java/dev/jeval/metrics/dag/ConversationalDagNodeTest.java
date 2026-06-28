package dev.jeval.metrics.dag;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.jeval.MultiTurnParam;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConversationalDagNodeTest {

    @Test
    void conversationalVerdictNodeRequiresExactlyOneScoreOrChildAndValidScore() {
        var scored = new ConversationalVerdictNode(true, 8);
        var child = new ConversationalTaskNode(
                "summarize",
                "Summary",
                List.of(new ConversationalBinaryJudgementNode(
                        "pass?",
                        List.of(new ConversationalVerdictNode(true, 10), new ConversationalVerdictNode(false, 0)))));

        assertAll(
                () -> assertEquals(true, scored.verdict()),
                () -> assertEquals(8, scored.score()),
                () -> assertThrows(IllegalArgumentException.class, () -> new ConversationalVerdictNode("bad", -1)),
                () -> assertThrows(IllegalArgumentException.class, () -> new ConversationalVerdictNode("bad", 11)),
                () -> assertThrows(IllegalArgumentException.class, () -> new ConversationalVerdictNode("bad", null, null)),
                () -> assertThrows(IllegalArgumentException.class, () -> new ConversationalVerdictNode("bad", 1, child)));
    }

    @Test
    void conversationalBinaryJudgementRequiresOneTrueAndOneFalseVerdictChild() {
        var yes = new ConversationalVerdictNode(true, 10);
        var no = new ConversationalVerdictNode(false, 0);
        var node = new ConversationalBinaryJudgementNode(
                "pass?",
                List.of(yes, no),
                List.of(MultiTurnParam.CONTENT),
                new TurnWindow(0, 1),
                "binary");

        assertAll(
                () -> assertSame(node, yes.parent()),
                () -> assertSame(node, no.parent()),
                () -> assertEquals(1, yes.indegree()),
                () -> assertEquals(1, no.indegree()),
                () -> assertEquals(new TurnWindow(0, 1), node.turnWindow()),
                () -> assertEquals("binary", node.label()),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ConversationalBinaryJudgementNode("pass?", List.of(new ConversationalVerdictNode(true, 10)))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ConversationalBinaryJudgementNode("pass?", List.of(new ConversationalVerdictNode(true, 10), new ConversationalVerdictNode(true, 0)))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ConversationalBinaryJudgementNode("pass?", List.of(new ConversationalVerdictNode("yes", 10), new ConversationalVerdictNode(false, 0)))));
    }

    @Test
    void conversationalNonBinaryJudgementRequiresUniqueStringVerdictChildren() {
        var good = new ConversationalVerdictNode("good", 10);
        var bad = new ConversationalVerdictNode("bad", 0);
        var node = new ConversationalNonBinaryJudgementNode("quality?", List.of(good, bad));

        assertAll(
                () -> assertSame(node, good.parent()),
                () -> assertEquals(List.of("good", "bad"), node.verdictOptions()),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ConversationalNonBinaryJudgementNode("quality?", List.of())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ConversationalNonBinaryJudgementNode("quality?", List.of(new ConversationalVerdictNode(true, 10)))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ConversationalNonBinaryJudgementNode("quality?", List.of(new ConversationalVerdictNode("same", 10), new ConversationalVerdictNode("same", 0)))));
    }

    @Test
    void conversationalTaskNodeRejectsVerdictChildrenAndSetsParentLinks() {
        var child = new ConversationalBinaryJudgementNode(
                "pass?",
                List.of(new ConversationalVerdictNode(true, 10), new ConversationalVerdictNode(false, 0)));
        var task = new ConversationalTaskNode(
                "extract answer",
                "Answer",
                List.of(child),
                List.of(MultiTurnParam.CONTENT),
                new TurnWindow(0, 2),
                "extractor");

        assertAll(
                () -> assertSame(task, child.parents().getFirst()),
                () -> assertEquals(1, child.indegree()),
                () -> assertEquals("extractor", task.label()),
                () -> assertEquals(new TurnWindow(0, 2), task.turnWindow()),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ConversationalTaskNode("bad", "Bad", List.of(new ConversationalVerdictNode("done", 1)))));
    }
}
