package dev.jeval.optimizer.algorithms;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class COPROTemplateTest {

    @Test
    void bootstrapGuidelinesTemplateIncludesOriginalPromptBreadthAndJsonSchema() {
        var prompt = COPROTemplate.generateBootstrapGuidelines("Answer {{input}}", 3);

        assertTrue(prompt.contains("generate 3 distinct"));
        assertTrue(prompt.contains("[ORIGINAL PROMPT]\nAnswer {{input}}"));
        assertTrue(prompt.contains("Brainstorm exactly 3 diverse \"Variation Guidelines\""));
        assertTrue(prompt.contains("\"guidelines\""));
        assertTrue(prompt.endsWith("JSON:\n"));
    }

    @Test
    void historyGuidelinesTemplateIncludesHistoryAndFeedbackInstruction() {
        var prompt = COPROTemplate.generateHistoryGuidelines(
                "Answer {{input}}",
                "Attempt A: score 0.25\nEvaluation Feedback: missed JSON",
                2);

        assertTrue(prompt.contains("[PAST ATTEMPTS, SCORES, & EVALUATION FEEDBACK]"));
        assertTrue(prompt.contains("Attempt A: score 0.25"));
        assertTrue(prompt.contains("brainstorm exactly 2 new \"Variation Guidelines\""));
        assertTrue(prompt.contains("explicitly address and fix the errors"));
        assertTrue(prompt.endsWith("JSON:\n"));
    }

    @Test
    void candidateTemplateForTextPromptAsksForStringRevisedPrompt() {
        var prompt = COPROTemplate.generateCandidate(
                "Answer {{input}}",
                "Add strict JSON output.",
                false);

        assertTrue(prompt.contains("[OPTIMIZATION GUIDELINE]\nAdd strict JSON output."));
        assertTrue(prompt.contains("DO NOT wrap your revised_prompt in markdown blocks"));
        assertTrue(prompt.contains("If the original prompt uses variable placeholders"));
        assertTrue(prompt.contains("\"revised_prompt\": \"You are a helpful assistant. Please answer: {{input}}\""));
    }

    @Test
    void candidateTemplateForListPromptAsksForMessageArrayRevisedPrompt() {
        var prompt = COPROTemplate.generateCandidate(
                "[{\"role\":\"user\",\"content\":\"{{input}}\"}]",
                "Add a system message.",
                true);

        assertTrue(prompt.contains("A JSON array of message objects"));
        assertTrue(prompt.contains("{\"role\": \"system\", \"content\": \"You are a helpful assistant.\"}"));
        assertTrue(prompt.contains("{\"role\": \"user\", \"content\": \"{{input}}\"}"));
    }
}
