package dev.jeval.optimizer.algorithms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.Golden;
import dev.jeval.prompt.Prompt;
import dev.jeval.prompt.PromptInterpolationType;
import dev.jeval.prompt.PromptMessage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MIPROV2InstructionProposerTest {

    @Test
    void proposeIncludesOriginalAndGeneratedTextCandidates() {
        var seenPrompts = new ArrayList<String>();
        var responses = new ArrayDeque<>(List.of(
                "{\"summary\":\"Answer geography questions with concise factual outputs.\"}",
                "{\"thought_process\":\"Make format explicit.\",\"revised_instruction\":\"Answer {{input}} with one factual sentence.\"}",
                "{\"thought_process\":\"Duplicate.\",\"revised_instruction\":\"Answer {{input}} with one factual sentence.\"}"));
        var proposer = new MIPROV2InstructionProposer(prompt -> {
            seenPrompts.add(prompt);
            return responses.removeFirst();
        }, 7);

        var candidates = proposer.propose(
                new Prompt("answer", "Answer {{input}}"),
                List.of(
                        Golden.builder("Capital of France?").expectedOutput("Paris").build(),
                        Golden.builder("Capital of Italy?").expectedOutput("Rome").build()),
                3);

        assertEquals(2, candidates.size());
        assertEquals("Answer {{input}}", candidates.getFirst().textTemplate());
        assertEquals("Answer {{input}} with one factual sentence.", candidates.get(1).textTemplate());
        assertTrue(seenPrompts.getFirst().contains("[EXAMPLE DATA]"));
        assertTrue(seenPrompts.get(1).contains("[DATASET SUMMARY]"));
        assertTrue(seenPrompts.get(1).contains("Answer geography questions"));
        assertTrue(seenPrompts.get(1).contains("Capital of"));
    }

    @Test
    void proposeFallsBackToGenericSummaryWhenSummaryGenerationFails() {
        var responses = new ArrayDeque<>(List.of(
                "not json",
                "{\"thought_process\":\"Use fallback.\",\"revised_instruction\":\"Classify {{input}} exactly.\"}"));
        var proposer = new MIPROV2InstructionProposer(prompt -> responses.removeFirst(), 3);

        var candidates = proposer.propose(
                new Prompt("classify", "Classify {{input}}"),
                List.of(Golden.builder("text").expectedOutput("label").build()),
                2);

        assertEquals("Classify {{input}} exactly.", candidates.get(1).textTemplate());
    }

    @Test
    void proposeRecreatesMessagePromptCandidates() {
        var responses = new ArrayDeque<>(List.of(
                "{\"summary\":\"Answer support chats.\"}",
                """
                {
                  "thought_process": "Add a system instruction.",
                  "revised_instruction": [
                    {"role": "system", "content": "Resolve support issues clearly."},
                    {"role": "user", "content": "{{input}}"}
                  ]
                }
                """));
        var proposer = new MIPROV2InstructionProposer(prompt -> responses.removeFirst(), 5);
        var original = new Prompt("chat", List.of(new PromptMessage("user", "{{input}}")),
                PromptInterpolationType.FSTRING);

        var candidates = proposer.propose(
                original,
                List.of(Golden.builder("Need refund").expectedOutput("Explain refund path").build()),
                2);

        assertEquals(2, candidates.size());
        assertEquals(List.of(
                new PromptMessage("system", "Resolve support issues clearly."),
                new PromptMessage("user", "{{input}}")), candidates.get(1).messagesTemplate());
    }
}
