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
    void parseSyntheticDataExtractsEmbeddedJsonLikeDeepEval() {
        var data = SynthesizerSchemas.parseSyntheticData("""
                Generated data:
                ```json
                {"data":[{"input":"Question?","expected_output":"Answer","used_source_files":["doc.md"],}],}
                ```
                """);

        assertEquals(1, data.size());
        assertEquals("Question?", data.getFirst().input());
        assertEquals("Answer", data.getFirst().expectedOutput());
        assertEquals("doc.md", data.getFirst().usedSourceFiles().getFirst());
    }

    @Test
    void parsesRewrittenInput() {
        assertEquals("clear question", SynthesizerSchemas.parseRewrittenInput("""
                {"rewritten_input":"clear question"}
                """));
    }

    @Test
    void parseScalarAndFeedbackSchemasExtractEmbeddedJsonLikeDeepEval() {
        var rewritten = SynthesizerSchemas.parseRewrittenInput("""
                ```json
                {"rewritten_input":"Clear question?",}
                ```
                """);
        var input = SynthesizerSchemas.parseInput("""
                model says {"input":"Styled question?",} thanks
                """);
        var feedback = SynthesizerSchemas.parseInputFeedback("""
                Feedback:
                {"feedback":"Good enough","score":0.75,}
                """);
        var styling = SynthesizerSchemas.parseStylingConfig("""
                {"scenario":"students","task":"ask questions","input_format":"short question",}
                """);

        assertEquals("Clear question?", rewritten);
        assertEquals("Styled question?", input);
        assertEquals("Good enough", feedback.feedback());
        assertEquals(0.75, feedback.score());
        assertEquals("students", styling.scenario());
        assertEquals("ask questions", styling.task());
        assertEquals("short question", styling.inputFormat());
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

    @Test
    void parseConversationalDataExtractsEmbeddedJsonLikeDeepEval() {
        var data = SynthesizerSchemas.parseConversationalData("""
                Conversation:
                ```json
                {"data":[{"scenario":"refund support","turns":[{"role":"user","content":"Can I get a refund?",}],"expected_outcome":"Refund explained",}],}
                ```
                """);

        assertEquals(1, data.size());
        assertEquals("refund support", data.getFirst().scenario());
        assertEquals("Refund explained", data.getFirst().expectedOutcome());
        assertEquals("user", data.getFirst().turns().getFirst().role());
        assertEquals("Can I get a refund?", data.getFirst().turns().getFirst().content());
    }
}
