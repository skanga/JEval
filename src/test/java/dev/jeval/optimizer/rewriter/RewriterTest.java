package dev.jeval.optimizer.rewriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.optimizer.ScorerDiagnosisResult;
import dev.jeval.prompt.Prompt;
import dev.jeval.prompt.PromptInterpolationType;
import dev.jeval.prompt.PromptMessage;
import dev.jeval.prompt.PromptType;
import java.util.List;
import org.junit.jupiter.api.Test;

class RewriterTest {

    @Test
    void mutationTemplateIncludesPromptAndDiagnosisBlocks() {
        var prompt = RewriterTemplate.generateMutation(
                "Answer {question}",
                "failure block",
                "success block",
                "result block",
                "analysis block",
                false);

        assertTrue(prompt.contains("# Original Prompt\nAnswer {question}"));
        assertTrue(prompt.contains("Failures: failure block"));
        assertTrue(prompt.contains("Successes: success block"));
        assertTrue(prompt.contains("Actual results from the previous generation: result block"));
        assertTrue(prompt.contains("Overall analysis of the diagnostic report: analysis block"));
        assertTrue(prompt.contains("\"revised_prompt\""));
    }

    @Test
    void rewriteReturnsOriginalPromptWhenAnalysisIsBlank() {
        var original = new Prompt("answer", "Answer {question}");
        var rewriter = new Rewriter(prompt -> {
            throw new AssertionError("callback should not be called");
        });

        assertSame(original, rewriter.rewrite(original, new ScorerDiagnosisResult("f", "s", "", List.of("r"))));
    }

    @Test
    void rewriteExtractsJsonRevisedPromptAndPreservesPromptMetadata() {
        var original = new Prompt(
                "answer",
                "Answer {question}",
                null,
                null,
                null,
                null,
                PromptInterpolationType.JINJA,
                "key",
                "branch");
        var rewriter = new Rewriter(prompt -> """
                {"thought_process":"tighten","revised_prompt":"New {{ question }}"}
                """);

        var rewritten = rewriter.rewrite(original, new ScorerDiagnosisResult(
                "bad answer", "good answer", "make it specific", List.of("trace")));

        assertEquals("answer", rewritten.alias());
        assertEquals("New {{ question }}", rewritten.textTemplate());
        assertEquals(PromptType.TEXT, rewritten.type());
        assertEquals(PromptInterpolationType.JINJA, rewritten.interpolationType());
        assertEquals("key", rewritten.confidentApiKey());
        assertEquals("branch", rewritten.branch());
    }

    @Test
    void rewriteAcceptsJsonArrayRevisedPromptForMessagePrompts() {
        var original = new Prompt("chat", List.of(new PromptMessage("system", "Old")),
                PromptInterpolationType.FSTRING);
        var rewriter = new Rewriter(prompt -> """
                {
                  "thought_process": "rewrite system",
                  "revised_prompt": [
                    {"role":"system","content":"New"},
                    {"role":"user","content":"{question}"}
                  ]
                }
                """);

        var rewritten = rewriter.rewrite(original, new ScorerDiagnosisResult(
                "failure", "success", "analysis", List.of("trace")));

        assertEquals(PromptType.LIST, rewritten.type());
        assertEquals(List.of(
                new PromptMessage("system", "New"),
                new PromptMessage("user", "{question}")), rewritten.messagesTemplate());
    }
}
