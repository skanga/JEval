package dev.jeval.optimizer.algorithms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.prompt.Prompt;
import dev.jeval.prompt.PromptInterpolationType;
import dev.jeval.prompt.PromptMessage;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SIMBAProposerTest {

    @Test
    void rewriteFromIntrospectionFormatsTrajectoriesAndReturnsRewrittenTextPrompt() {
        var seenPrompt = new AtomicReference<String>();
        var proposer = new SIMBAProposer(prompt -> {
            seenPrompt.set(prompt);
            return """
                    {
                      "discussion": "The stronger trajectory cites evidence before answering.",
                      "revised_prompt": "Answer {{input}} with a cited fact"
                    }
                    """;
        });

        var rewritten = proposer.rewriteFromIntrospection(
                new Prompt("answer", "Answer {{input}}"),
                "question",
                "answer with source",
                1.0,
                "correct",
                "question",
                "unsupported answer",
                0.0,
                "missing evidence");

        assertEquals("Answer {{input}} with a cited fact", rewritten.textTemplate());
        assertTrue(seenPrompt.get().contains("Inputs: question"));
        assertTrue(seenPrompt.get().contains("Model Output: unsupported answer"));
        assertTrue(seenPrompt.get().contains("Score: 0.0000"));
        assertTrue(seenPrompt.get().contains("Evaluation Feedback: correct"));
    }

    @Test
    void rewriteFromIntrospectionReturnsOriginalPromptWhenCallbackReturnsInvalidJson() {
        var original = new Prompt("answer", "Answer {{input}}");
        var proposer = new SIMBAProposer(prompt -> "not json");

        var rewritten = proposer.rewriteFromIntrospection(
                original,
                "good input",
                "good output",
                1.0,
                "good",
                "bad input",
                "bad output",
                0.0,
                "bad");

        assertSame(original, rewritten);
    }

    @Test
    void rewriteFromIntrospectionRecreatesMessagePromptFromArrayResponse() {
        var original = new Prompt("chat", List.of(new PromptMessage("user", "{{input}}")),
                PromptInterpolationType.FSTRING);
        var proposer = new SIMBAProposer(prompt -> """
                {
                  "discussion": "Use a system rule.",
                  "revised_prompt": [
                    {"role": "system", "content": "Cite evidence"},
                    {"role": "user", "content": "{{input}}"}
                  ]
                }
                """);

        var rewritten = proposer.rewriteFromIntrospection(
                original,
                "good input",
                "good output",
                1.0,
                "good",
                "bad input",
                "bad output",
                0.0,
                "bad");

        assertEquals(List.of(
                new PromptMessage("system", "Cite evidence"),
                new PromptMessage("user", "{{input}}")), rewritten.messagesTemplate());
    }

    @Test
    void appendADemoAddsExampleToTextPrompt() {
        var proposer = new SIMBAProposer(prompt -> "{}");

        var rewritten = proposer.appendADemo(new Prompt("answer", "Answer"), "q", "a");

        assertEquals("Answer\n\n[Example]\nInput: q\nOutput: a", rewritten.textTemplate());
    }

    @Test
    void appendADemoInjectsExampleIntoFirstSystemMessage() {
        var proposer = new SIMBAProposer(prompt -> "{}");
        var original = new Prompt("chat", List.of(
                new PromptMessage("system", "Answer carefully."),
                new PromptMessage("user", "{{input}}")), PromptInterpolationType.FSTRING);

        var rewritten = proposer.appendADemo(original, "q", "a");

        assertEquals(List.of(
                new PromptMessage("system", "Answer carefully.\n\n[Example]\nInput: q\nOutput: a"),
                new PromptMessage("user", "{{input}}")), rewritten.messagesTemplate());
    }
}
