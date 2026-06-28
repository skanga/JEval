package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PlanAdherenceSchemasTest {

    @Test
    void parsesPlanAdherenceScore() {
        var score = PlanAdherenceSchemas.parseScore("{\"score\":0.25,\"reason\":\"Step 2 was skipped.\"}");
        var stringScore = PlanAdherenceSchemas.parseScore("{\"score\":\"0.25\",\"reason\":\"Step 2 was skipped.\"}");

        assertEquals(0.25, score.score());
        assertEquals(0.25, stringScore.score());
        assertEquals("Step 2 was skipped.", score.reason());
    }

    @Test
    void rejectsMissingOrInvalidFieldsLikeDeepEval() {
        assertThrows(IllegalArgumentException.class, () -> PlanAdherenceSchemas.parseScore("{\"score\":0.25}"));
        assertThrows(IllegalArgumentException.class, () -> PlanAdherenceSchemas.parseScore("{\"reason\":\"ok\"}"));
        assertThrows(IllegalArgumentException.class, () -> PlanAdherenceSchemas.parseScore("{\"score\":\"bad\",\"reason\":\"ok\"}"));
        assertThrows(IllegalArgumentException.class, () -> PlanAdherenceSchemas.parseScore("{\"score\":0.25,\"reason\":1}"));
    }
}
