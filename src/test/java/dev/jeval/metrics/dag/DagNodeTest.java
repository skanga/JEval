package dev.jeval.metrics.dag;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.jeval.SingleTurnParam;
import java.util.List;
import org.junit.jupiter.api.Test;

class DagNodeTest {

    @Test
    void verdictNodeRequiresExactlyOneScoreOrChildAndValidScore() {
        var scored = new VerdictNode("good", 7);
        var child = new TaskNode("summarize", "Summary", List.of(new BinaryJudgementNode(
                "is good",
                List.of(new VerdictNode(true, 10), new VerdictNode(false, 0)))));

        assertAll(
                () -> assertEquals("good", scored.verdict()),
                () -> assertEquals(7, scored.score()),
                () -> assertThrows(IllegalArgumentException.class, () -> new VerdictNode("bad", -1)),
                () -> assertThrows(IllegalArgumentException.class, () -> new VerdictNode("bad", 11)),
                () -> assertThrows(IllegalArgumentException.class, () -> new VerdictNode("bad", null, null)),
                () -> assertThrows(IllegalArgumentException.class, () -> new VerdictNode("bad", 1, child)));
    }

    @Test
    void binaryJudgementRequiresOneTrueAndOneFalseVerdictChild() {
        var yes = new VerdictNode(true, 10);
        var no = new VerdictNode(false, 0);
        var node = new BinaryJudgementNode("pass?", List.of(yes, no), List.of(SingleTurnParam.ACTUAL_OUTPUT));

        assertAll(
                () -> assertSame(node, yes.parent()),
                () -> assertSame(node, no.parent()),
                () -> assertEquals(1, yes.indegree()),
                () -> assertEquals(1, no.indegree()),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new BinaryJudgementNode("pass?", List.of(new VerdictNode(true, 10)))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new BinaryJudgementNode("pass?", List.of(new VerdictNode(true, 10), new VerdictNode(true, 0)))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new BinaryJudgementNode("pass?", List.of(new VerdictNode("yes", 10), new VerdictNode(false, 0)))));
    }

    @Test
    void nonBinaryJudgementRequiresUniqueStringVerdictChildren() {
        var good = new VerdictNode("good", 10);
        var bad = new VerdictNode("bad", 0);
        var node = new NonBinaryJudgementNode("quality?", List.of(good, bad));

        assertAll(
                () -> assertSame(node, good.parent()),
                () -> assertEquals(List.of("good", "bad"), node.verdictOptions()),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new NonBinaryJudgementNode("quality?", List.of())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new NonBinaryJudgementNode("quality?", List.of(new VerdictNode(true, 10)))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new NonBinaryJudgementNode("quality?", List.of(new VerdictNode("same", 10), new VerdictNode("same", 0)))));
    }

    @Test
    void taskNodeRejectsVerdictChildrenAndSetsParentLinks() {
        var child = new BinaryJudgementNode("pass?", List.of(new VerdictNode(true, 10), new VerdictNode(false, 0)));
        var task = new TaskNode(
                "extract answer",
                "Answer",
                List.of(child),
                List.of(SingleTurnParam.INPUT),
                "extractor");

        assertAll(
                () -> assertSame(task, child.parents().getFirst()),
                () -> assertEquals(1, child.indegree()),
                () -> assertEquals("extractor", task.label()),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new TaskNode("bad", "Bad", List.of(new VerdictNode("done", 1)))));
    }

    @Test
    void judgementNodesIncrementNestedVerdictChildIndegree() {
        var binaryNested = new TaskNode("binary path", "BinaryPath", List.of(new BinaryJudgementNode(
                "nested?",
                List.of(new VerdictNode(true, 10), new VerdictNode(false, 0)))));
        var nonBinaryNested = new TaskNode("non-binary path", "NonBinaryPath", List.of(new BinaryJudgementNode(
                "nested?",
                List.of(new VerdictNode(true, 10), new VerdictNode(false, 0)))));

        new BinaryJudgementNode("route?", List.of(new VerdictNode(true, null, binaryNested), new VerdictNode(false, 0)));
        new NonBinaryJudgementNode("route?", List.of(new VerdictNode("good", null, nonBinaryNested), new VerdictNode("bad", 0)));

        assertAll(
                () -> assertEquals(1, binaryNested.indegree()),
                () -> assertEquals(1, nonBinaryNested.indegree()));
    }
}
