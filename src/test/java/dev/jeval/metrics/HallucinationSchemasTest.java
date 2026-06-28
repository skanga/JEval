package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class HallucinationSchemasTest {

    @Test
    void verdictsCopyLists() {
        var verdicts = new ArrayList<>(List.of(
                new HallucinationSchemas.HallucinationVerdict("yes", "Agrees.")));

        var response = new HallucinationSchemas.Verdicts(verdicts);

        verdicts.add(new HallucinationSchemas.HallucinationVerdict("no", "ignored"));

        assertEquals(1, response.verdicts().size());
        assertThrows(UnsupportedOperationException.class,
                () -> response.verdicts().add(new HallucinationSchemas.HallucinationVerdict("no", "nope")));
    }

    @Test
    void parsesVerdictsFromModelJson() {
        var verdicts = HallucinationSchemas.parseVerdicts("""
                prefix {
                  "verdicts": [
                    {"verdict": "yes", "reason": "Agrees."},
                    {"verdict": "no", "reason": "Contradicts."}
                  ]
                } suffix
                """);

        assertEquals(List.of(
                new HallucinationSchemas.HallucinationVerdict("yes", "Agrees."),
                new HallucinationSchemas.HallucinationVerdict("no", "Contradicts.")),
                verdicts.verdicts());
    }

    @Test
    void parsesScoreReasonFromModelJson() {
        var reason = HallucinationSchemas.parseScoreReason("{\"reason\": \"One context is contradicted.\"}");

        assertEquals("One context is contradicted.", reason.reason());
    }

    @Test
    void rejectsMissingOrInvalidFieldsLikeDeepEval() {
        assertThrows(IllegalArgumentException.class, () -> HallucinationSchemas.parseVerdicts("{}"));
        assertThrows(IllegalArgumentException.class, () -> HallucinationSchemas.parseVerdicts("{\"verdicts\":\"yes\"}"));
        assertThrows(IllegalArgumentException.class, () -> HallucinationSchemas.parseVerdicts("{\"verdicts\":[{\"reason\":\"ok\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> HallucinationSchemas.parseVerdicts("{\"verdicts\":[{\"verdict\":\"idk\",\"reason\":\"ok\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> HallucinationSchemas.parseVerdicts("{\"verdicts\":[{\"verdict\":\"yes\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> HallucinationSchemas.parseVerdicts("{\"verdicts\":[{\"verdict\":1,\"reason\":\"ok\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> HallucinationSchemas.parseVerdicts("{\"verdicts\":[{\"verdict\":\"yes\",\"reason\":1}]}"));
        assertThrows(IllegalArgumentException.class, () -> HallucinationSchemas.parseScoreReason("{}"));
        assertThrows(IllegalArgumentException.class, () -> HallucinationSchemas.parseScoreReason("{\"reason\":1}"));
    }
}
