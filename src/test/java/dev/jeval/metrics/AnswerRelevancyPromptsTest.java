package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class AnswerRelevancyPromptsTest {

    @Test
    void generateStatementsPromptIncludesActualOutputAndJsonInstruction() {
        var prompt = AnswerRelevancyPrompts.generateStatements("The product has a warranty.", false);

        assertTrue(prompt.contains("breakdown and generate a list of statements"));
        assertTrue(prompt.contains("\"statements\""));
        assertTrue(prompt.contains("Text:\nThe product has a warranty."));
        assertFalse(prompt.contains("--- MULTIMODAL INPUT RULES ---"));
    }

    @Test
    void generateVerdictsPromptIncludesInputStatementsAndMultimodalRules() {
        var prompt = AnswerRelevancyPrompts.generateVerdicts(
                "What is the policy?",
                List.of("Returns are accepted.", "The office is blue."),
                true);

        assertTrue(prompt.contains("determine whether each statement is relevant"));
        assertTrue(prompt.contains("--- MULTIMODAL INPUT RULES ---"));
        assertTrue(prompt.contains("Input:\nWhat is the policy?"));
        assertTrue(prompt.contains("Statements:\n[Returns are accepted., The office is blue.]"));
    }

    @Test
    void generateReasonPromptFormatsScoreAndIrrelevantStatements() {
        var prompt = AnswerRelevancyPrompts.generateReason(
                "What is the policy?",
                2.0 / 3.0,
                List.of("The office color is unrelated."),
                false);

        assertTrue(prompt.contains("Answer Relevancy Score:\n0.67"));
        assertTrue(prompt.contains("The office color is unrelated."));
        assertTrue(prompt.contains("Input:\nWhat is the policy?"));
    }
}
