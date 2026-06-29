package dev.jeval.optimizer.algorithms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.prompt.Prompt;
import dev.jeval.prompt.PromptInterpolationType;
import dev.jeval.prompt.PromptMessage;
import java.util.ArrayDeque;
import java.util.List;
import org.junit.jupiter.api.Test;

class COPROProposerTest {

    @Test
    void proposeBootstrapBuildsCandidatesFromGuidelinesAndSkipsDuplicates() {
        var responses = new ArrayDeque<>(List.of(
                "{\"guidelines\":[\"Add JSON output\",\"Add citations\",\"Ignored extra\"]}",
                "{\"revised_prompt\":\"Answer {{input}} as JSON\"}",
                "{\"revised_prompt\":\"Answer {{input}} as JSON\"}",
                "{\"revised_prompt\":\"Answer {{input}} with citations\"}"));
        var proposer = new COPROProposer(prompt -> responses.removeFirst());

        var candidates = proposer.proposeBootstrap(new Prompt("answer", "Answer {{input}}"), 2);

        assertEquals(1, responses.size());
        assertEquals(1, candidates.size());
        assertEquals("Answer {{input}} as JSON", candidates.getFirst().textTemplate());
    }

    @Test
    void proposeFromHistoryUsesHistoryGuidelinesPrompt() {
        var seenPrompts = new ArrayDeque<String>();
        var responses = new ArrayDeque<>(List.of(
                "{\"guidelines\":[\"Fix failed JSON metric\"]}",
                "{\"revised_prompt\":\"Answer {{input}} in strict JSON\"}"));
        var proposer = new COPROProposer(prompt -> {
            seenPrompts.add(prompt);
            return responses.removeFirst();
        });

        var candidates = proposer.proposeFromHistory(
                new Prompt("answer", "Answer {{input}}"),
                "Attempt 1 score 0.25 Evaluation Feedback: invalid JSON",
                1);

        assertEquals(1, candidates.size());
        assertEquals("Answer {{input}} in strict JSON", candidates.getFirst().textTemplate());
        assertTrue(seenPrompts.getFirst().contains("[PAST ATTEMPTS, SCORES, & EVALUATION FEEDBACK]"));
        assertTrue(seenPrompts.getFirst().contains("invalid JSON"));
    }

    @Test
    void proposeBootstrapCreatesListPromptCandidates() {
        var responses = new ArrayDeque<>(List.of(
                "{\"guidelines\":[\"Add system role\"]}",
                """
                {"revised_prompt":[
                  {"role":"system","content":"Be concise"},
                  {"role":"user","content":"{{input}}"}
                ]}
                """));
        var proposer = new COPROProposer(prompt -> responses.removeFirst());
        var original = new Prompt("chat", List.of(new PromptMessage("user", "{{input}}")),
                PromptInterpolationType.FSTRING);

        var candidates = proposer.proposeBootstrap(original, 1);

        assertEquals(List.of(
                new PromptMessage("system", "Be concise"),
                new PromptMessage("user", "{{input}}")), candidates.getFirst().messagesTemplate());
    }
}
