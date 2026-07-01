package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class ArenaGEvalSchemasTest {

    @Test
    void parsesWinnerAndRewrittenReason() {
        var winner = ArenaGEvalSchemas.parseWinner("""
                {"winner":"contestant_a","reason":"More accurate."}
                """);
        var rewrittenReason = ArenaGEvalSchemas.parseRewrittenReason("""
                {"rewritten_reason":"Contestant A was more accurate."}
                """);
        var steps = ArenaGEvalSchemas.parseSteps("""
                {"steps":["Compare accuracy","Compare completeness"]}
                """);
        var reasonScore = ArenaGEvalSchemas.parseReasonScore("""
                {"reason":"Contestant A is stronger.","score":"9"}
                """);

        assertAll(
                () -> assertEquals("contestant_a", winner.winner()),
                () -> assertEquals("More accurate.", winner.reason()),
                () -> assertEquals("Contestant A was more accurate.", rewrittenReason.rewrittenReason()),
                () -> assertEquals(List.of("Compare accuracy", "Compare completeness"), steps.steps()),
                () -> assertEquals("Contestant A is stronger.", reasonScore.reason()),
                () -> assertEquals(9.0, reasonScore.score()));
    }

    @Test
    void rejectsInvalidFieldsLikePydanticSchema() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> ArenaGEvalSchemas.parseSteps("{}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ArenaGEvalSchemas.parseSteps("{\"steps\":\"Compare\"}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ArenaGEvalSchemas.parseSteps("{\"steps\":[1]}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ArenaGEvalSchemas.parseReasonScore("{\"score\":9}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ArenaGEvalSchemas.parseReasonScore("{\"reason\":\"ok\"}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ArenaGEvalSchemas.parseReasonScore("{\"reason\":1,\"score\":9}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ArenaGEvalSchemas.parseReasonScore("{\"reason\":\"ok\",\"score\":\"bad\"}")),
                () -> assertThrows(IllegalArgumentException.class, () -> ArenaGEvalSchemas.parseWinner("{}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ArenaGEvalSchemas.parseWinner("{\"winner\":1,\"reason\":\"ok\"}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ArenaGEvalSchemas.parseWinner("{\"winner\":\"contestant_a\"}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ArenaGEvalSchemas.parseWinner("{\"winner\":\"contestant_a\",\"reason\":1}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ArenaGEvalSchemas.parseRewrittenReason("{}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ArenaGEvalSchemas.parseRewrittenReason("{\"rewritten_reason\":1}")));
    }

    @Test
    void reasonScoreRejectsNonFiniteScores() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ArenaGEvalSchemas.ReasonScore("reason", Double.NaN)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ArenaGEvalSchemas.ReasonScore("reason", Double.POSITIVE_INFINITY)));
    }
}
