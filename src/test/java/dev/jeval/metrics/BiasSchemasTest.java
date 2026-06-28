package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BiasSchemasTest {

    @Test
    void verdictsCopyLists() {
        var verdicts = new ArrayList<>(List.of(new BiasSchemas.BiasVerdict("yes", "Biased.")));

        var response = new BiasSchemas.Verdicts(verdicts);

        verdicts.add(new BiasSchemas.BiasVerdict("no", null));

        assertEquals(1, response.verdicts().size());
        assertThrows(UnsupportedOperationException.class,
                () -> response.verdicts().add(new BiasSchemas.BiasVerdict("no", null)));
    }

    @Test
    void parsesOpinionsVerdictsAndReasonFromModelJson() {
        var opinions = BiasSchemas.parseOpinions("{\"opinions\": [\"That statement is loaded.\"]}");
        var verdicts = BiasSchemas.parseVerdicts("""
                prefix {"verdicts": [
                  {"verdict": "yes", "reason": "Political framing."},
                  {"verdict": "no"}
                ]} suffix
                """);
        var reason = BiasSchemas.parseScoreReason("{\"reason\": \"One opinion is biased.\"}");

        assertEquals(List.of("That statement is loaded."), opinions.opinions());
        assertEquals(List.of(
                new BiasSchemas.BiasVerdict("yes", "Political framing."),
                new BiasSchemas.BiasVerdict("no", null)),
                verdicts.verdicts());
        assertEquals("One opinion is biased.", reason.reason());
    }

    @Test
    void rejectsMissingOrInvalidFieldsLikeDeepEval() {
        assertThrows(IllegalArgumentException.class, () -> BiasSchemas.parseOpinions("{}"));
        assertThrows(IllegalArgumentException.class, () -> BiasSchemas.parseOpinions("{\"opinions\":\"one\"}"));
        assertThrows(IllegalArgumentException.class, () -> BiasSchemas.parseOpinions("{\"opinions\":[1]}"));
        assertThrows(IllegalArgumentException.class, () -> BiasSchemas.parseVerdicts("{}"));
        assertThrows(IllegalArgumentException.class, () -> BiasSchemas.parseVerdicts("{\"verdicts\":\"yes\"}"));
        assertThrows(IllegalArgumentException.class, () -> BiasSchemas.parseVerdicts("{\"verdicts\":[{\"reason\":\"ok\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> BiasSchemas.parseVerdicts("{\"verdicts\":[{\"verdict\":\"idk\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> BiasSchemas.parseVerdicts("{\"verdicts\":[{\"verdict\":1}]}"));
        assertThrows(IllegalArgumentException.class, () -> BiasSchemas.parseVerdicts("{\"verdicts\":[{\"verdict\":\"yes\",\"reason\":1}]}"));
        assertThrows(IllegalArgumentException.class, () -> BiasSchemas.parseScoreReason("{}"));
        assertThrows(IllegalArgumentException.class, () -> BiasSchemas.parseScoreReason("{\"reason\":1}"));
    }
}
