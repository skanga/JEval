package dev.jeval.optimizer.algorithms;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MIPROV2TemplateTest {

    @Test
    void datasetSummaryTemplateIncludesExamplesAndJsonSchema() {
        var template = MIPROV2Template.generateDatasetSummary("Example 1:\n  Input: q\n  Expected: a");

        assertTrue(template.contains("[EXAMPLE DATA]"));
        assertTrue(template.contains("Input: q"));
        assertTrue(template.contains("\"summary\""));
        assertTrue(template.contains("overarching objective"));
    }

    @Test
    void instructionProposalTemplateIncludesGroundingAndTextSchema() {
        var template = MIPROV2Template.generateInstructionProposal(
                "Answer {{input}}",
                "Answer questions directly.",
                "Example 1:\n  Input: q\n  Expected: a",
                "Be concise and direct.",
                1,
                false);

        assertTrue(template.contains("[CURRENT PROMPT]\nAnswer {{input}}"));
        assertTrue(template.contains("[DATASET SUMMARY]\nAnswer questions directly."));
        assertTrue(template.contains("[GENERATION TIP]\nBe concise and direct."));
        assertTrue(template.contains("candidate #2"));
        assertTrue(template.contains("\"revised_instruction\""));
        assertTrue(template.contains("optimized revised instruction"));
    }

    @Test
    void instructionProposalTemplateIncludesMessageArraySchemaForListPrompt() {
        var template = MIPROV2Template.generateInstructionProposal(
                "[{\"role\":\"user\",\"content\":\"{{input}}\"}]",
                "Chat task.",
                "Example 1: user question",
                "Use structured formatting when appropriate.",
                0,
                true);

        assertTrue(template.contains("A STRICT JSON array of message objects"));
        assertTrue(template.contains("\"role\" and \"content\""));
    }
}
