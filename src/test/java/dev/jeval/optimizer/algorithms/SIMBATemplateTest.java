package dev.jeval.optimizer.algorithms;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SIMBATemplateTest {

    @Test
    void introspectionRewriteTemplateIncludesTrajectoriesAndTextSchema() {
        var template = SIMBATemplate.generateIntrospectionRewrite(
                "Answer {{input}}",
                "Inputs: hard\nModel Output: bad\nScore: 0.0000\nEvaluation Feedback: wrong",
                "Inputs: hard\nModel Output: good\nScore: 1.0000\nEvaluation Feedback: correct",
                false);

        assertTrue(template.contains("Stochastic Introspective Mini-Batch Ascent"));
        assertTrue(template.contains("[ORIGINAL INSTRUCTIONS]\nAnswer {{input}}"));
        assertTrue(template.contains("[WORSE TRAJECTORY (The Failure)]"));
        assertTrue(template.contains("[BETTER TRAJECTORY (The Success)]"));
        assertTrue(template.contains("The final string representing the fully rewritten prompt."));
        assertTrue(template.contains("\"revised_prompt\""));
    }

    @Test
    void introspectionRewriteTemplateIncludesMessageArraySchemaForListPrompts() {
        var template = SIMBATemplate.generateIntrospectionRewrite(
                "[{\"role\":\"system\",\"content\":\"Answer {{input}}\"}]",
                "worse",
                "better",
                true);

        assertTrue(template.contains("A STRICT JSON array of message objects"));
        assertTrue(template.contains("{\"role\": \"system\", \"content\":"));
    }
}
