package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToxicitySchemasTest {

    @Test
    void verdictsCopyLists() {
        var verdicts = new ArrayList<>(List.of(new ToxicitySchemas.ToxicityVerdict("yes", "Toxic.")));

        var response = new ToxicitySchemas.Verdicts(verdicts);

        verdicts.add(new ToxicitySchemas.ToxicityVerdict("no", null));

        assertEquals(1, response.verdicts().size());
        assertThrows(UnsupportedOperationException.class,
                () -> response.verdicts().add(new ToxicitySchemas.ToxicityVerdict("no", null)));
    }

    @Test
    void parsesOpinionsVerdictsAndReasonFromModelJson() {
        var opinions = ToxicitySchemas.parseOpinions("{\"opinions\": [\"That was insulting.\"]}");
        var verdicts = ToxicitySchemas.parseVerdicts("""
                prefix {"verdicts": [
                  {"verdict": "yes", "reason": "Insulting phrase."},
                  {"verdict": "no"}
                ]} suffix
                """);
        var reason = ToxicitySchemas.parseScoreReason("{\"reason\": \"One opinion is toxic.\"}");

        assertEquals(List.of("That was insulting."), opinions.opinions());
        assertEquals(List.of(
                new ToxicitySchemas.ToxicityVerdict("yes", "Insulting phrase."),
                new ToxicitySchemas.ToxicityVerdict("no", null)),
                verdicts.verdicts());
        assertEquals("One opinion is toxic.", reason.reason());
    }

    @Test
    void rejectsMissingOrInvalidFieldsLikeDeepEval() {
        assertThrows(IllegalArgumentException.class, () -> ToxicitySchemas.parseOpinions("{}"));
        assertThrows(IllegalArgumentException.class, () -> ToxicitySchemas.parseOpinions("{\"opinions\":\"one\"}"));
        assertThrows(IllegalArgumentException.class, () -> ToxicitySchemas.parseOpinions("{\"opinions\":[1]}"));
        assertThrows(IllegalArgumentException.class, () -> ToxicitySchemas.parseVerdicts("{}"));
        assertThrows(IllegalArgumentException.class, () -> ToxicitySchemas.parseVerdicts("{\"verdicts\":\"yes\"}"));
        assertThrows(IllegalArgumentException.class, () -> ToxicitySchemas.parseVerdicts("{\"verdicts\":[{\"reason\":\"ok\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> ToxicitySchemas.parseVerdicts("{\"verdicts\":[{\"verdict\":\"idk\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> ToxicitySchemas.parseVerdicts("{\"verdicts\":[{\"verdict\":1}]}"));
        assertThrows(IllegalArgumentException.class, () -> ToxicitySchemas.parseVerdicts("{\"verdicts\":[{\"verdict\":\"yes\",\"reason\":1}]}"));
        assertThrows(IllegalArgumentException.class, () -> ToxicitySchemas.parseScoreReason("{}"));
        assertThrows(IllegalArgumentException.class, () -> ToxicitySchemas.parseScoreReason("{\"reason\":1}"));
    }
}
