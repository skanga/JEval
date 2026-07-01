package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class StepEfficiencySchemasTest {

    @Test
    void parsesTaskAndEfficiencyVerdict() {
        var task = StepEfficiencySchemas.parseTask("{\"task\":\"Book a flight\"}");
        var verdict = StepEfficiencySchemas.parseVerdict("""
                {"score":0.75,"reason":"One redundant lookup."}
                """);
        var stringScore = StepEfficiencySchemas.parseVerdict("""
                {"score":"0.75","reason":"One redundant lookup."}
                """);

        assertEquals("Book a flight", task.task());
        assertEquals(0.75, verdict.score());
        assertEquals(0.75, stringScore.score());
        assertEquals("One redundant lookup.", verdict.reason());
    }

    @Test
    void rejectsMissingOrInvalidFieldsLikeDeepEval() {
        assertThrows(IllegalArgumentException.class, () -> StepEfficiencySchemas.parseTask("{}"));
        assertThrows(IllegalArgumentException.class, () -> StepEfficiencySchemas.parseTask("{\"task\":1}"));
        assertThrows(IllegalArgumentException.class, () -> StepEfficiencySchemas.parseVerdict("{\"score\":0.75}"));
        assertThrows(IllegalArgumentException.class, () -> StepEfficiencySchemas.parseVerdict("{\"reason\":\"ok\"}"));
        assertThrows(IllegalArgumentException.class, () -> StepEfficiencySchemas.parseVerdict("{\"score\":\"bad\",\"reason\":\"ok\"}"));
        assertThrows(IllegalArgumentException.class, () -> StepEfficiencySchemas.parseVerdict("{\"score\":0.75,\"reason\":1}"));
    }

    @Test
    void verdictRecordRejectsNonFiniteScores() {
        assertThrows(IllegalArgumentException.class,
                () -> new StepEfficiencySchemas.EfficiencyVerdict(Double.NaN, "bad"));
        assertThrows(IllegalArgumentException.class,
                () -> new StepEfficiencySchemas.EfficiencyVerdict(Double.POSITIVE_INFINITY, "bad"));
    }
}
