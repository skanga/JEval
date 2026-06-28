package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ArgumentCorrectnessSchemasTest {

    @Test
    void parsesVerdictsAndReasonFromModelJson() {
        var verdicts = ArgumentCorrectnessSchemas.parseVerdicts("""
                {"verdicts":[
                  {"verdict":"yes"},
                  {"verdict":"idk"},
                  {"verdict":"no","reason":"Wrong city."}
                ]}
                """);
        var reason = ArgumentCorrectnessSchemas.parseScoreReason("{\"reason\":\"One tool used the wrong city.\"}");

        assertAll(
                () -> assertEquals(3, verdicts.verdicts().size()),
                () -> assertEquals("yes", verdicts.verdicts().getFirst().verdict()),
                () -> assertEquals("idk", verdicts.verdicts().get(1).verdict()),
                () -> assertEquals("Wrong city.", verdicts.verdicts().get(2).reason()),
                () -> assertEquals("One tool used the wrong city.", reason.reason()));
    }

    @Test
    void rejectsInvalidFieldsLikePydanticSchema() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> ArgumentCorrectnessSchemas.parseVerdicts("{}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ArgumentCorrectnessSchemas.parseVerdicts("{\"verdicts\":\"yes\"}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ArgumentCorrectnessSchemas.parseVerdicts("{\"verdicts\":[{\"reason\":\"ok\"}]}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ArgumentCorrectnessSchemas.parseVerdicts("{\"verdicts\":[{\"verdict\":\"maybe\"}]}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ArgumentCorrectnessSchemas.parseVerdicts("{\"verdicts\":[{\"verdict\":1}]}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ArgumentCorrectnessSchemas.parseVerdicts(
                                "{\"verdicts\":[{\"verdict\":\"yes\",\"reason\":1}]}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ArgumentCorrectnessSchemas.parseScoreReason("{}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ArgumentCorrectnessSchemas.parseScoreReason("{\"reason\":1}")));
    }
}
