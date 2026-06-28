package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ContextualRelevancyPromptsTest {

    @Test
    void generateVerdictsPromptIncludesInputContextAndTextInstructions() {
        var prompt = ContextualRelevancyPrompts.generateVerdicts(
                "What is the refund policy?",
                "Refunds last 30 days. The lobby is blue.",
                false);

        assertTrue(prompt.contains("each statement found in the context"));
        assertTrue(prompt.contains("You should first extract statements found in the context"));
        assertTrue(prompt.contains("No statements found in provided context."));
        assertTrue(prompt.contains("Input:\nWhat is the refund policy?"));
        assertTrue(prompt.contains("Context:\nRefunds last 30 days. The lobby is blue."));
        assertFalse(prompt.contains("statement or image"));
    }

    @Test
    void generateVerdictsPromptIncludesMultimodalWording() {
        var prompt = ContextualRelevancyPrompts.generateVerdicts(
                "What is shown?",
                "[DEEPEVAL:IMAGE:image-id]",
                true);

        assertTrue(prompt.contains("context (image or string)"));
        assertTrue(prompt.contains("statement or image"));
        assertTrue(prompt.contains("If the context is an image"));
        assertFalse(prompt.contains("No statements found in provided context."));
    }

    @Test
    void generateReasonPromptFormatsScoreAndRelevantIrrelevantStatements() {
        var prompt = ContextualRelevancyPrompts.generateReason(
                "What is the refund policy?",
                1.0 / 3.0,
                List.of("The lobby color is unrelated."),
                List.of("Refunds last 30 days."),
                false);

        assertTrue(prompt.contains("Contextual Relevancy Score:\n0.33"));
        assertTrue(prompt.contains("Input:\nWhat is the refund policy?"));
        assertTrue(prompt.contains("The lobby color is unrelated."));
        assertTrue(prompt.contains("Refunds last 30 days."));
        assertTrue(prompt.contains("\"reason\""));
    }
}
