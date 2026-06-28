package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TaskCompletionSchemasTest {

    @Test
    void parsesTaskOutcomeAndVerdictFromModelJson() {
        var taskAndOutcome = TaskCompletionSchemas.parseTaskAndOutcome(
                "prefix {\"task\":\"Plan trip\",\"outcome\":\"Suggested flights.\"} suffix");
        var verdict = TaskCompletionSchemas.parseVerdict(
                "{\"verdict\":0.75,\"reason\":\"Most pieces are complete.\"}");
        var stringVerdict = TaskCompletionSchemas.parseVerdict(
                "{\"verdict\":\"0.75\",\"reason\":null}");
        var scoreAlias = TaskCompletionSchemas.parseVerdict(
                "{\"score\":0.5,\"reason\":\"Visible MCP task score.\"}");

        assertAll(
                () -> assertEquals(
                        new TaskCompletionSchemas.TaskAndOutcome("Plan trip", "Suggested flights."),
                        taskAndOutcome),
                () -> assertEquals(0.75, verdict.verdict()),
                () -> assertEquals("Most pieces are complete.", verdict.reason()),
                () -> assertEquals(0.75, stringVerdict.verdict()),
                () -> assertNull(stringVerdict.reason()),
                () -> assertEquals(0.5, scoreAlias.verdict()),
                () -> assertEquals("Visible MCP task score.", scoreAlias.reason()));
    }

    @Test
    void rejectsMissingOrInvalidRequiredFieldsLikeDeepEval() {
        assertThrows(IllegalArgumentException.class, () -> TaskCompletionSchemas.parseTaskAndOutcome("{\"task\":\"Plan trip\"}"));
        assertThrows(IllegalArgumentException.class, () -> TaskCompletionSchemas.parseTaskAndOutcome("{\"outcome\":\"Suggested flights.\"}"));
        assertThrows(IllegalArgumentException.class, () -> TaskCompletionSchemas.parseTaskAndOutcome("{\"task\":1,\"outcome\":\"Suggested flights.\"}"));
        assertThrows(IllegalArgumentException.class, () -> TaskCompletionSchemas.parseTaskAndOutcome("{\"task\":\"Plan trip\",\"outcome\":1}"));
        assertThrows(IllegalArgumentException.class, () -> TaskCompletionSchemas.parseVerdict("{\"reason\":\"ok\"}"));
        assertThrows(IllegalArgumentException.class, () -> TaskCompletionSchemas.parseVerdict("{\"verdict\":\"bad\",\"reason\":\"ok\"}"));
        assertThrows(IllegalArgumentException.class, () -> TaskCompletionSchemas.parseVerdict("{\"verdict\":0.75,\"reason\":1}"));
    }
}
