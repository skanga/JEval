package dev.jeval.synthesizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SynthesizerSchemasTest {

    @Test
    void parsesSyntheticDataListWithSnakeCaseFields() {
        var data = SynthesizerSchemas.parseSyntheticData("""
                {"data":[{"input":"What is the capital?","expected_output":"Paris","used_source_files":["cities.txt"]}]}
                """);

        assertEquals(1, data.size());
        assertEquals("What is the capital?", data.getFirst().input());
        assertEquals("Paris", data.getFirst().expectedOutput());
        assertEquals(List.of("cities.txt"), data.getFirst().usedSourceFiles());
    }

    @Test
    void parsesRewrittenInput() {
        assertEquals("clear question", SynthesizerSchemas.parseRewrittenInput("""
                {"rewritten_input":"clear question"}
                """));
    }

    @Test
    void contextPromptAsksForJsonInputsAndIncludesContext() {
        var prompt = SynthesizerPrompts.generateSyntheticInputs(List.of("Paris is in France."), 2, true);

        assertTrue(prompt.contains("Paris is in France."));
        assertTrue(prompt.contains("\"data\""));
        assertTrue(prompt.contains("\"input\""));
        assertTrue(prompt.contains("2"));
    }

    @Test
    void conversationalExpectedOutcomePromptIncludesConfiguredFormat() {
        var prompt = SynthesizerPrompts.generateConversationalExpectedOutcome(
                "refund support",
                List.of("Refunds take 5 days."),
                "Return a numbered checklist.");

        assertTrue(prompt.contains("refund support"));
        assertTrue(prompt.contains("Refunds take 5 days."));
        assertTrue(prompt.contains("Expected outcome format: Return a numbered checklist."));
    }
}
