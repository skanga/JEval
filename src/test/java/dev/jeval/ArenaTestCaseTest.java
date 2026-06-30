package dev.jeval;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ArenaTestCaseTest {

    @Test
    void requiresUniqueContestantNamesAndSharedReferences() {
        var first = new Contestant("a", new LlmTestCase("input", "actual-a", "expected"));
        var second = new Contestant("b", new LlmTestCase("input", "actual-b", "expected"));

        assertFalse(new ArenaTestCase(List.of(first, second)).multimodal());

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ArenaTestCase(List.of(first, new Contestant("a",
                                new LlmTestCase("input", "actual-c", "expected"))))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ArenaTestCase(List.of(first, new Contestant("c",
                                new LlmTestCase("other input", "actual-c", "expected"))))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ArenaTestCase(List.of(first, new Contestant("c",
                                new LlmTestCase("input", "actual-c", "other expected"))))));
    }

    @Test
    void becomesMultimodalWhenAnyContestantTestCaseIsMultimodal() {
        var plain = new Contestant("plain", new LlmTestCase("input", "actual-a", "expected"));
        var multimodal = new Contestant("multi",
                LlmTestCase.builder("input")
                        .actualOutput("actual-b [DEEPEVAL:IMAGE:image-id]")
                        .expectedOutput("expected")
                        .build());

        assertTrue(new ArenaTestCase(List.of(plain, multimodal)).multimodal());
    }

    @Test
    void arenaContainerCopiesArenaTestCasesLikeDeepEvalSurface() {
        var testCase = new ArenaTestCase(List.of(
                new Contestant("a", new LlmTestCase("input", "actual-a", "expected")),
                new Contestant("b", new LlmTestCase("input", "actual-b", "expected"))));
        var testCases = new java.util.ArrayList<>(List.of(testCase));

        var arena = new Arena(testCases);
        testCases.clear();

        assertEquals(List.of(testCase), arena.testCases());
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new Arena(null)),
                () -> assertThrows(IllegalArgumentException.class, () -> new Arena(List.of())));
    }
}
