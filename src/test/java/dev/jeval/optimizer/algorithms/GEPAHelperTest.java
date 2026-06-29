package dev.jeval.optimizer.algorithms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.Golden;
import dev.jeval.optimizer.policies.TieBreaker;
import dev.jeval.prompt.Prompt;
import dev.jeval.prompt.PromptInterpolationType;
import dev.jeval.prompt.PromptMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

class GEPAHelperTest {

    @Test
    void equivalentPromptIgnoresOuterWhitespaceForTextPrompts() {
        var gepa = new GEPA(1, 2, 1, 7, 1, TieBreaker.PREFER_CHILD);

        assertTrue(gepa.isEquivalentPrompt(
                new Prompt("answer", " Answer {question}\n"),
                new Prompt("answer", "Answer {question}")));
        assertFalse(gepa.isEquivalentPrompt(
                new Prompt("answer", "Answer briefly"),
                new Prompt("answer", "Answer in detail")));
    }

    @Test
    void equivalentPromptComparesMessageRolesAndTrimmedContent() {
        var gepa = new GEPA(1, 2, 1, 7, 1, TieBreaker.PREFER_CHILD);
        var original = new Prompt("chat", List.of(
                new PromptMessage("system", "Be direct "),
                new PromptMessage("user", "{question}")),
                PromptInterpolationType.FSTRING);
        var same = new Prompt("chat", List.of(
                new PromptMessage("system", " Be direct"),
                new PromptMessage("user", "{question}\n")),
                PromptInterpolationType.FSTRING);
        var changedRole = new Prompt("chat", List.of(
                new PromptMessage("assistant", "Be direct"),
                new PromptMessage("user", "{question}")),
                PromptInterpolationType.FSTRING);

        assertTrue(gepa.isEquivalentPrompt(original, same));
        assertFalse(gepa.isEquivalentPrompt(original, changedRole));
        assertFalse(gepa.isEquivalentPrompt(original, new Prompt("answer", "Be direct")));
    }

    @Test
    void drawMinibatchSamplesUpToConfiguredSizeWithSeededRandomState() {
        var goldens = List.of(
                Golden.builder("q1").expectedOutput("a1").build(),
                Golden.builder("q2").expectedOutput("a2").build(),
                Golden.builder("q3").expectedOutput("a3").build());
        var first = new GEPA(1, 2, 1, 11, 1, TieBreaker.PREFER_CHILD);
        var second = new GEPA(1, 2, 1, 11, 1, TieBreaker.PREFER_CHILD);

        var firstBatch = first.drawMinibatch(goldens);
        var secondBatch = second.drawMinibatch(goldens);

        assertEquals(2, firstBatch.size());
        assertEquals(firstBatch, secondBatch);
        assertTrue(goldens.containsAll(firstBatch));
    }

    @Test
    void drawMinibatchReturnsAllPossibleSlotsForSmallInputs() {
        var goldens = List.of(Golden.builder("q1").expectedOutput("a1").build());
        var gepa = new GEPA(1, 3, 1, 11, 1, TieBreaker.PREFER_CHILD);

        assertEquals(1, gepa.drawMinibatch(goldens).size());
        assertEquals(List.of(), gepa.drawMinibatch(List.of()));
    }
}
