package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class FaithfulnessPromptsTest {

    @Test
    void generateTruthsPromptIncludesRetrievalContextAndLimitPhrase() {
        var prompt = FaithfulnessPrompts.generateTruths(List.of("Paris is in France."), 1, false);

        assertTrue(prompt.contains("the single most important FACTUAL, undisputed truth"));
        assertTrue(prompt.contains("not for his groundbreaking work on relativity"));
        assertTrue(prompt.contains("Einstein won the noble prize for his discovery of the photoelectric effect in 1968."));
        assertTrue(prompt.contains("Text:\nParis is in France."));
        assertTrue(prompt.contains("\"truths\""));
        assertFalse(prompt.contains("text and images"));
    }

    @Test
    void generateTruthsPromptIncludesMultimodalInstructions() {
        var prompt = FaithfulnessPrompts.generateTruths(List.of("Image context"), 3, true);

        assertTrue(prompt.contains("excerpt (text and images)"));
        assertTrue(prompt.contains("The excerpt may contain both text and images."));
        assertTrue(prompt.contains("the 3 most important FACTUAL, undisputed truths per document"));
    }

    @Test
    void generateClaimsPromptIncludesActualOutputAndMultimodalInstruction() {
        var prompt = FaithfulnessPrompts.generateClaims("The refund lasts 30 days.", true);

        assertTrue(prompt.contains("extract a comprehensive list of FACTUAL"));
        assertTrue(prompt.contains("not for his groundbreaking work on relativity"));
        assertTrue(prompt.contains("Einstein won the noble prize for his discovery of the photoelectric effect in 1968."));
        assertTrue(prompt.contains("extract claims from all provided content"));
        assertTrue(prompt.contains("Excerpt:\nThe refund lasts 30 days."));
    }

    @Test
    void generateVerdictsPromptIncludesTruthsClaimsAndTextGuidelines() {
        var prompt = FaithfulnessPrompts.generateVerdicts(
                List.of("The refund lasts 30 days."),
                List.of("The refund lasts 60 days."),
                false);

        assertTrue(prompt.contains("indicate whether EACH claim contradicts any facts"));
        assertTrue(prompt.contains("Retrieval Contexts:"));
        assertTrue(prompt.contains("The refund lasts 30 days."));
        assertTrue(prompt.contains("Claims:"));
        assertTrue(prompt.contains("[The refund lasts 60 days.]"));
        assertTrue(prompt.contains("Generate ONE verdict per claim"));
    }

    @Test
    void generateReasonPromptFormatsScoreAndContradictions() {
        var prompt = FaithfulnessPrompts.generateReason(
                2.0 / 3.0,
                List.of("The actual output contradicts the refund duration."));

        assertTrue(prompt.contains("Faithfulness Score:\n0.67"));
        assertTrue(prompt.contains("The actual output contradicts the refund duration."));
        assertTrue(prompt.contains("\"reason\""));
    }
}
