package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MCPUseSchemasTest {

    @Test
    void parsesPrimitiveAndArgumentScoresFromModelJson() {
        var primitivesScore = MCPUseSchemas.parsePrimitivesScore(
                "{\"score\":0.75,\"reason\":\"Used a relevant tool.\"}");
        var argumentsScore = MCPUseSchemas.parseArgsScore(
                "{\"score\":0.5,\"reason\":\"The query argument was too broad.\"}");
        var stringScore = MCPUseSchemas.parsePrimitivesScore(
                "{\"score\":\"0.75\",\"reason\":\"Used a relevant tool.\"}");

        assertAll(
                () -> assertEquals(0.75, primitivesScore.score()),
                () -> assertEquals("Used a relevant tool.", primitivesScore.reason()),
                () -> assertEquals(0.75, stringScore.score()),
                () -> assertEquals(0.5, argumentsScore.score()),
                () -> assertEquals("The query argument was too broad.", argumentsScore.reason()));
    }

    @Test
    void rejectsMissingOrInvalidScoreReasonFieldsLikeDeepEval() {
        assertThrows(IllegalArgumentException.class, () -> MCPUseSchemas.parsePrimitivesScore("{\"score\":0.75}"));
        assertThrows(IllegalArgumentException.class, () -> MCPUseSchemas.parsePrimitivesScore("{\"reason\":\"ok\"}"));
        assertThrows(IllegalArgumentException.class, () -> MCPUseSchemas.parsePrimitivesScore("{\"score\":\"bad\",\"reason\":\"ok\"}"));
        assertThrows(IllegalArgumentException.class, () -> MCPUseSchemas.parsePrimitivesScore("{\"score\":0.75,\"reason\":1}"));
        assertThrows(IllegalArgumentException.class, () -> MCPUseSchemas.parseArgsScore("{\"score\":0.5}"));
        assertThrows(IllegalArgumentException.class, () -> MCPUseSchemas.parseArgsScore("{\"reason\":\"ok\"}"));
        assertThrows(IllegalArgumentException.class, () -> MCPUseSchemas.parseArgsScore("{\"score\":\"bad\",\"reason\":\"ok\"}"));
        assertThrows(IllegalArgumentException.class, () -> MCPUseSchemas.parseArgsScore("{\"score\":0.5,\"reason\":1}"));
    }
}
