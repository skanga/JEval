package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ToolUseSchemasTest {

    @Test
    void parsesScoresAndReason() {
        var selection = ToolUseSchemas.parseToolSelectionScore("""
                {"score":1.0,"reason":"Search was the right tool."}
                """);
        var arguments = ToolUseSchemas.parseArgumentCorrectnessScore("""
                {"score":0.75,"reason":"Location was correct."}
                """);
        var stringScore = ToolUseSchemas.parseToolSelectionScore("""
                {"score":"1.0","reason":"Search was the right tool."}
                """);
        var reason = ToolUseSchemas.parseReason("{\"reason\":\"Tool use passed.\"}");

        assertEquals(1.0, selection.score());
        assertEquals("Search was the right tool.", selection.reason());
        assertEquals(1.0, stringScore.score());
        assertEquals(0.75, arguments.score());
        assertEquals("Location was correct.", arguments.reason());
        assertEquals("Tool use passed.", reason.reason());
    }

    @Test
    void rejectsMissingOrInvalidFieldsLikeDeepEval() {
        assertThrows(IllegalArgumentException.class, () -> ToolUseSchemas.parseToolSelectionScore("{\"score\":1.0}"));
        assertThrows(IllegalArgumentException.class, () -> ToolUseSchemas.parseToolSelectionScore("{\"reason\":\"ok\"}"));
        assertThrows(IllegalArgumentException.class, () -> ToolUseSchemas.parseToolSelectionScore("{\"score\":\"bad\",\"reason\":\"ok\"}"));
        assertThrows(IllegalArgumentException.class, () -> ToolUseSchemas.parseToolSelectionScore("{\"score\":1.0,\"reason\":1}"));
        assertThrows(IllegalArgumentException.class, () -> ToolUseSchemas.parseArgumentCorrectnessScore("{\"score\":0.75}"));
        assertThrows(IllegalArgumentException.class, () -> ToolUseSchemas.parseArgumentCorrectnessScore("{\"reason\":\"ok\"}"));
        assertThrows(IllegalArgumentException.class, () -> ToolUseSchemas.parseArgumentCorrectnessScore("{\"score\":\"bad\",\"reason\":\"ok\"}"));
        assertThrows(IllegalArgumentException.class, () -> ToolUseSchemas.parseArgumentCorrectnessScore("{\"score\":0.75,\"reason\":1}"));
        assertThrows(IllegalArgumentException.class, () -> ToolUseSchemas.parseReason("{}"));
        assertThrows(IllegalArgumentException.class, () -> ToolUseSchemas.parseReason("{\"reason\":1}"));
    }
}
