package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContextualRecallSchemasTest {

    @Test
    void verdictsCopyLists() {
        var verdicts = new ArrayList<>(List.of(
                new ContextualRecallSchemas.ContextualRecallVerdict("yes", "Supported by node 1.")));

        var response = new ContextualRecallSchemas.Verdicts(verdicts);

        verdicts.add(new ContextualRecallSchemas.ContextualRecallVerdict("no", "ignored"));

        assertEquals(1, response.verdicts().size());
        assertThrows(UnsupportedOperationException.class,
                () -> response.verdicts().add(
                        new ContextualRecallSchemas.ContextualRecallVerdict("no", "nope")));
    }

    @Test
    void parsesVerdictsFromModelJson() {
        var verdicts = ContextualRecallSchemas.parseVerdicts("""
                prefix {
                  "verdicts": [
                    {"verdict": "yes", "reason": "Supported by node 1."},
                    {"verdict": "no", "reason": "Not found."}
                  ]
                } suffix
                """);

        assertEquals(List.of(
                new ContextualRecallSchemas.ContextualRecallVerdict("yes", "Supported by node 1."),
                new ContextualRecallSchemas.ContextualRecallVerdict("no", "Not found.")),
                verdicts.verdicts());
    }

    @Test
    void parsesScoreReasonFromModelJson() {
        var reason = ContextualRecallSchemas.parseScoreReason("{\"reason\": \"One expected fact was missing.\"}");

        assertEquals("One expected fact was missing.", reason.reason());
    }

    @Test
    void parsesInteractionScoreFromTurnMetricSchema() {
        var score = ContextualRecallSchemas.parseInteractionScore("""
                {"score":"0.5","reason":null,"verdicts":null}
                """);
        var scoreWithVerdicts = ContextualRecallSchemas.parseInteractionScore("""
                {"score":1.0,"reason":"All recalled.","verdicts":[{"verdict":"yes","reason":"Found."}]}
                """);

        assertEquals(0.5, score.score());
        assertEquals(null, score.reason());
        assertEquals(null, score.verdicts());
        assertEquals(1.0, scoreWithVerdicts.score());
        assertEquals("All recalled.", scoreWithVerdicts.reason());
        assertEquals(List.of(new ContextualRecallSchemas.ContextualRecallVerdict("yes", "Found.")),
                scoreWithVerdicts.verdicts());
    }

    @Test
    void rejectsMissingOrInvalidFieldsLikeDeepEval() {
        assertThrows(IllegalArgumentException.class, () -> ContextualRecallSchemas.parseVerdicts("{}"));
        assertThrows(IllegalArgumentException.class, () -> ContextualRecallSchemas.parseVerdicts("{\"verdicts\":\"yes\"}"));
        assertThrows(IllegalArgumentException.class, () -> ContextualRecallSchemas.parseVerdicts("{\"verdicts\":[{\"reason\":\"ok\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> ContextualRecallSchemas.parseVerdicts("{\"verdicts\":[{\"verdict\":\"yes\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> ContextualRecallSchemas.parseVerdicts("{\"verdicts\":[{\"verdict\":1,\"reason\":\"ok\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> ContextualRecallSchemas.parseVerdicts("{\"verdicts\":[{\"verdict\":\"yes\",\"reason\":1}]}"));
        assertThrows(IllegalArgumentException.class, () -> ContextualRecallSchemas.parseScoreReason("{}"));
        assertThrows(IllegalArgumentException.class, () -> ContextualRecallSchemas.parseScoreReason("{\"reason\":1}"));
        assertThrows(IllegalArgumentException.class, () -> ContextualRecallSchemas.parseInteractionScore("{}"));
        assertThrows(IllegalArgumentException.class, () -> ContextualRecallSchemas.parseInteractionScore("{\"score\":1,\"verdicts\":null}"));
        assertThrows(IllegalArgumentException.class, () -> ContextualRecallSchemas.parseInteractionScore("{\"score\":1,\"reason\":null}"));
        assertThrows(IllegalArgumentException.class, () -> ContextualRecallSchemas.parseInteractionScore("{\"score\":\"bad\",\"reason\":null,\"verdicts\":null}"));
        assertThrows(IllegalArgumentException.class, () -> ContextualRecallSchemas.parseInteractionScore("{\"score\":1,\"reason\":1,\"verdicts\":null}"));
        assertThrows(IllegalArgumentException.class, () -> ContextualRecallSchemas.parseInteractionScore("{\"score\":1,\"reason\":null,\"verdicts\":\"yes\"}"));
        assertThrows(IllegalArgumentException.class, () -> ContextualRecallSchemas.parseInteractionScore("{\"score\":1,\"reason\":null,\"verdicts\":[{\"verdict\":\"yes\"}]}"));
    }
}
