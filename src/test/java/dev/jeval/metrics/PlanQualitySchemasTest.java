package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class PlanQualitySchemasTest {

    @Test
    void parsesAgentPlanAndQualityScore() {
        var plan = PlanQualitySchemas.parseAgentPlan("""
                {"plan":["Inspect requirements","Run tests"]}
                """);
        var score = PlanQualitySchemas.parseScore("{\"score\":0.75,\"reason\":\"Missing deployment step.\"}");
        var stringScore = PlanQualitySchemas.parseScore("{\"score\":\"0.75\",\"reason\":\"Missing deployment step.\"}");

        assertEquals(List.of("Inspect requirements", "Run tests"), plan.plan());
        assertEquals(0.75, score.score());
        assertEquals(0.75, stringScore.score());
        assertEquals("Missing deployment step.", score.reason());
    }

    @Test
    void rejectsMissingOrInvalidFieldsLikeDeepEval() {
        assertThrows(IllegalArgumentException.class, () -> PlanQualitySchemas.parseAgentPlan("{}"));
        assertThrows(IllegalArgumentException.class, () -> PlanQualitySchemas.parseAgentPlan("{\"plan\":\"Inspect\"}"));
        assertThrows(IllegalArgumentException.class, () -> PlanQualitySchemas.parseAgentPlan("{\"plan\":[1]}"));
        assertThrows(IllegalArgumentException.class, () -> PlanQualitySchemas.parseScore("{\"score\":0.75}"));
        assertThrows(IllegalArgumentException.class, () -> PlanQualitySchemas.parseScore("{\"reason\":\"ok\"}"));
        assertThrows(IllegalArgumentException.class, () -> PlanQualitySchemas.parseScore("{\"score\":\"bad\",\"reason\":\"ok\"}"));
        assertThrows(IllegalArgumentException.class, () -> PlanQualitySchemas.parseScore("{\"score\":0.75,\"reason\":1}"));
    }
}
