package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class GEvalSchemasTest {

    @Test
    void parsesStepsAndReasonScoreFromModelJson() {
        var steps = GEvalSchemas.parseSteps("{\"steps\":[\"Check correctness\",\"Check completeness\"]}");
        var reasonScore = GEvalSchemas.parseReasonScore("{\"score\":8,\"reason\":\"Mostly correct.\"}");
        var stringScore = GEvalSchemas.parseReasonScore("{\"score\":\"8\",\"reason\":\"Mostly correct.\"}");

        assertAll(
                () -> assertEquals(List.of("Check correctness", "Check completeness"), steps.steps()),
                () -> assertEquals(8.0, reasonScore.score()),
                () -> assertEquals(8.0, stringScore.score()),
                () -> assertEquals("Mostly correct.", reasonScore.reason()));
    }

    @Test
    void rejectsMissingOrInvalidSchemaValuesLikeDeepEval() {
        assertThrows(IllegalArgumentException.class, () -> GEvalSchemas.parseSteps("{}"));
        assertThrows(IllegalArgumentException.class, () -> GEvalSchemas.parseSteps("{\"steps\":\"Check\"}"));
        assertThrows(IllegalArgumentException.class, () -> GEvalSchemas.parseSteps("{\"steps\":[1]}"));
        assertThrows(IllegalArgumentException.class, () -> GEvalSchemas.parseReasonScore("{\"score\":8}"));
        assertThrows(IllegalArgumentException.class, () -> GEvalSchemas.parseReasonScore("{\"reason\":\"ok\"}"));
        assertThrows(IllegalArgumentException.class, () -> GEvalSchemas.parseReasonScore("{\"reason\":1,\"score\":8}"));
        assertThrows(IllegalArgumentException.class, () -> GEvalSchemas.parseReasonScore("{\"reason\":\"ok\",\"score\":\"bad\"}"));
    }
}
