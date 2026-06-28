package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContextualRelevancySchemasTest {

    @Test
    void verdictsCopyLists() {
        var verdicts = new ArrayList<>(List.of(
                new ContextualRelevancySchemas.ContextualRelevancyVerdict("Policy refunds last 30 days.", "yes", null)));

        var response = new ContextualRelevancySchemas.ContextualRelevancyVerdicts(verdicts);

        verdicts.add(new ContextualRelevancySchemas.ContextualRelevancyVerdict("ignored", "no", "ignored"));

        assertEquals(1, response.verdicts().size());
        assertThrows(UnsupportedOperationException.class,
                () -> response.verdicts().add(new ContextualRelevancySchemas.ContextualRelevancyVerdict("nope", "no", null)));
    }

    @Test
    void parsesVerdictsFromModelJson() {
        var verdicts = ContextualRelevancySchemas.parseVerdicts("""
                prefix {
                  "verdicts": [
                    {"statement": "Refunds last 30 days.", "verdict": "yes"},
                    {"statement": "The lobby is blue.", "verdict": "no", "reason": "Unrelated."}
                  ]
                } suffix
                """);

        assertEquals(List.of(
                new ContextualRelevancySchemas.ContextualRelevancyVerdict("Refunds last 30 days.", "yes", null),
                new ContextualRelevancySchemas.ContextualRelevancyVerdict("The lobby is blue.", "no", "Unrelated.")),
                verdicts.verdicts());
    }

    @Test
    void parsesScoreReasonFromModelJson() {
        var reason = ContextualRelevancySchemas.parseScoreReason("{\"reason\": \"Most context is relevant.\"}");

        assertEquals("Most context is relevant.", reason.reason());
    }

    @Test
    void parsesInteractionScoreFromTurnMetricSchema() {
        var score = ContextualRelevancySchemas.parseInteractionScore("""
                {"score":"0.5","reason":null,"verdicts":null}
                """);
        var scoreWithVerdicts = ContextualRelevancySchemas.parseInteractionScore("""
                {"score":1.0,"reason":"All relevant.","verdicts":[{"statement":"Refunds last 30 days.","verdict":"yes"}]}
                """);

        assertEquals(0.5, score.score());
        assertEquals(null, score.reason());
        assertEquals(null, score.verdicts());
        assertEquals(1.0, scoreWithVerdicts.score());
        assertEquals("All relevant.", scoreWithVerdicts.reason());
        assertEquals(List.of(new ContextualRelevancySchemas.ContextualRelevancyVerdict(
                "Refunds last 30 days.", "yes", null)), scoreWithVerdicts.verdicts());
    }

    @Test
    void rejectsMissingOrInvalidFieldsLikeDeepEval() {
        assertThrows(IllegalArgumentException.class, () -> ContextualRelevancySchemas.parseVerdicts("{}"));
        assertThrows(IllegalArgumentException.class, () -> ContextualRelevancySchemas.parseVerdicts("{\"verdicts\":\"yes\"}"));
        assertThrows(IllegalArgumentException.class, () -> ContextualRelevancySchemas.parseVerdicts("{\"verdicts\":[{\"verdict\":\"yes\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> ContextualRelevancySchemas.parseVerdicts("{\"verdicts\":[{\"statement\":\"ok\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> ContextualRelevancySchemas.parseVerdicts("{\"verdicts\":[{\"statement\":1,\"verdict\":\"yes\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> ContextualRelevancySchemas.parseVerdicts("{\"verdicts\":[{\"statement\":\"ok\",\"verdict\":1}]}"));
        assertThrows(IllegalArgumentException.class, () -> ContextualRelevancySchemas.parseVerdicts("{\"verdicts\":[{\"statement\":\"ok\",\"verdict\":\"yes\",\"reason\":1}]}"));
        assertThrows(IllegalArgumentException.class, () -> ContextualRelevancySchemas.parseScoreReason("{}"));
        assertThrows(IllegalArgumentException.class, () -> ContextualRelevancySchemas.parseScoreReason("{\"reason\":1}"));
        assertThrows(IllegalArgumentException.class, () -> ContextualRelevancySchemas.parseInteractionScore("{}"));
        assertThrows(IllegalArgumentException.class, () -> ContextualRelevancySchemas.parseInteractionScore("{\"score\":1,\"verdicts\":null}"));
        assertThrows(IllegalArgumentException.class, () -> ContextualRelevancySchemas.parseInteractionScore("{\"score\":1,\"reason\":null}"));
        assertThrows(IllegalArgumentException.class, () -> ContextualRelevancySchemas.parseInteractionScore("{\"score\":\"bad\",\"reason\":null,\"verdicts\":null}"));
        assertThrows(IllegalArgumentException.class, () -> ContextualRelevancySchemas.parseInteractionScore("{\"score\":1,\"reason\":1,\"verdicts\":null}"));
        assertThrows(IllegalArgumentException.class, () -> ContextualRelevancySchemas.parseInteractionScore("{\"score\":1,\"reason\":null,\"verdicts\":\"yes\"}"));
        assertThrows(IllegalArgumentException.class, () -> ContextualRelevancySchemas.parseInteractionScore("{\"score\":1,\"reason\":null,\"verdicts\":[{\"statement\":\"ok\"}]}"));
    }
}
