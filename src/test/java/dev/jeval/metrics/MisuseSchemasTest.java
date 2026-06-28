package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class MisuseSchemasTest {

    @Test
    void parsesMisusesVerdictsAndReasonFromModelJson() {
        var misuses = MisuseSchemas.parseMisuses("{\"misuses\":[\"Write a poem.\"]}");
        var verdicts = MisuseSchemas.parseVerdicts("""
                {"verdicts":[
                  {"verdict":"yes","reason":"Outside banking scope."},
                  {"verdict":"no"}
                ]}
                """);
        var reason = MisuseSchemas.parseScoreReason("{\"reason\":\"One misuse was found.\"}");

        assertAll(
                () -> assertEquals(List.of("Write a poem."), misuses.misuses()),
                () -> assertEquals(2, verdicts.verdicts().size()),
                () -> assertEquals("yes", verdicts.verdicts().getFirst().verdict()),
                () -> assertEquals("Outside banking scope.", verdicts.verdicts().getFirst().reason()),
                () -> assertEquals("One misuse was found.", reason.reason()));
    }

    @Test
    void rejectsMissingOrInvalidFieldsLikeDeepEval() {
        assertThrows(IllegalArgumentException.class, () -> MisuseSchemas.parseMisuses("{}"));
        assertThrows(IllegalArgumentException.class, () -> MisuseSchemas.parseMisuses("{\"misuses\":\"one\"}"));
        assertThrows(IllegalArgumentException.class, () -> MisuseSchemas.parseMisuses("{\"misuses\":[1]}"));
        assertThrows(IllegalArgumentException.class, () -> MisuseSchemas.parseVerdicts("{}"));
        assertThrows(IllegalArgumentException.class, () -> MisuseSchemas.parseVerdicts("{\"verdicts\":\"yes\"}"));
        assertThrows(IllegalArgumentException.class, () -> MisuseSchemas.parseVerdicts("{\"verdicts\":[{\"reason\":\"ok\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> MisuseSchemas.parseVerdicts("{\"verdicts\":[{\"verdict\":\"idk\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> MisuseSchemas.parseVerdicts("{\"verdicts\":[{\"verdict\":1}]}"));
        assertThrows(IllegalArgumentException.class, () -> MisuseSchemas.parseVerdicts("{\"verdicts\":[{\"verdict\":\"yes\",\"reason\":1}]}"));
        assertThrows(IllegalArgumentException.class, () -> MisuseSchemas.parseScoreReason("{}"));
        assertThrows(IllegalArgumentException.class, () -> MisuseSchemas.parseScoreReason("{\"reason\":1}"));
    }
}
