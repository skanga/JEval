package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContextualPrecisionSchemasTest {

    @Test
    void verdictsCopyLists() {
        var verdicts = new ArrayList<>(List.of(
                new ContextualPrecisionSchemas.ContextualPrecisionVerdict("yes", "Useful node.")));

        var response = new ContextualPrecisionSchemas.Verdicts(verdicts);

        verdicts.add(new ContextualPrecisionSchemas.ContextualPrecisionVerdict("no", "ignored"));

        assertEquals(1, response.verdicts().size());
        assertThrows(UnsupportedOperationException.class,
                () -> response.verdicts().add(
                        new ContextualPrecisionSchemas.ContextualPrecisionVerdict("no", "nope")));
    }

    @Test
    void parsesVerdictsFromModelJson() {
        var verdicts = ContextualPrecisionSchemas.parseVerdicts("""
                prefix {
                  "verdicts": [
                    {"verdict": "yes", "reason": "Useful node."},
                    {"verdict": "no", "reason": "Unrelated node."}
                  ]
                } suffix
                """);

        assertEquals(List.of(
                new ContextualPrecisionSchemas.ContextualPrecisionVerdict("yes", "Useful node."),
                new ContextualPrecisionSchemas.ContextualPrecisionVerdict("no", "Unrelated node.")),
                verdicts.verdicts());
    }

    @Test
    void parsesScoreReasonFromModelJson() {
        var reason = ContextualPrecisionSchemas.parseScoreReason("{\"reason\": \"Useful nodes are ranked high.\"}");

        assertEquals("Useful nodes are ranked high.", reason.reason());
    }

    @Test
    void parsesInteractionScoreFromTurnMetricSchema() {
        var score = ContextualPrecisionSchemas.parseInteractionScore("""
                {"score":"0.75","reason":null,"verdicts":null}
                """);
        var scoreWithVerdicts = ContextualPrecisionSchemas.parseInteractionScore("""
                {"score":1.0,"reason":"All useful.","verdicts":[{"verdict":"yes","reason":"Relevant."}]}
                """);

        assertEquals(0.75, score.score());
        assertEquals(null, score.reason());
        assertEquals(null, score.verdicts());
        assertEquals(1.0, scoreWithVerdicts.score());
        assertEquals("All useful.", scoreWithVerdicts.reason());
        assertEquals(List.of(new ContextualPrecisionSchemas.ContextualPrecisionVerdict("yes", "Relevant.")),
                scoreWithVerdicts.verdicts());
    }

    @Test
    void rejectsMissingOrInvalidFieldsLikeDeepEval() {
        assertThrows(IllegalArgumentException.class, () -> ContextualPrecisionSchemas.parseVerdicts("{}"));
        assertThrows(IllegalArgumentException.class, () -> ContextualPrecisionSchemas.parseVerdicts("{\"verdicts\":\"yes\"}"));
        assertThrows(IllegalArgumentException.class, () -> ContextualPrecisionSchemas.parseVerdicts("{\"verdicts\":[{\"reason\":\"ok\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> ContextualPrecisionSchemas.parseVerdicts("{\"verdicts\":[{\"verdict\":\"yes\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> ContextualPrecisionSchemas.parseVerdicts("{\"verdicts\":[{\"verdict\":1,\"reason\":\"ok\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> ContextualPrecisionSchemas.parseVerdicts("{\"verdicts\":[{\"verdict\":\"yes\",\"reason\":1}]}"));
        assertThrows(IllegalArgumentException.class, () -> ContextualPrecisionSchemas.parseScoreReason("{}"));
        assertThrows(IllegalArgumentException.class, () -> ContextualPrecisionSchemas.parseScoreReason("{\"reason\":1}"));
        assertThrows(IllegalArgumentException.class, () -> ContextualPrecisionSchemas.parseInteractionScore("{}"));
        assertThrows(IllegalArgumentException.class, () -> ContextualPrecisionSchemas.parseInteractionScore("{\"score\":1,\"verdicts\":null}"));
        assertThrows(IllegalArgumentException.class, () -> ContextualPrecisionSchemas.parseInteractionScore("{\"score\":1,\"reason\":null}"));
        assertThrows(IllegalArgumentException.class, () -> ContextualPrecisionSchemas.parseInteractionScore("{\"score\":\"bad\",\"reason\":null,\"verdicts\":null}"));
        assertThrows(IllegalArgumentException.class, () -> ContextualPrecisionSchemas.parseInteractionScore("{\"score\":1,\"reason\":1,\"verdicts\":null}"));
        assertThrows(IllegalArgumentException.class, () -> ContextualPrecisionSchemas.parseInteractionScore("{\"score\":1,\"reason\":null,\"verdicts\":\"yes\"}"));
        assertThrows(IllegalArgumentException.class, () -> ContextualPrecisionSchemas.parseInteractionScore("{\"score\":1,\"reason\":null,\"verdicts\":[{\"verdict\":\"yes\"}]}"));
    }
}
