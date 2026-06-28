package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PromptAlignmentSchemasTest {

    @Test
    void parsesVerdictsAndReasonFromModelJson() {
        var verdicts = PromptAlignmentSchemas.parseVerdicts("""
                {"verdicts":[
                  {"verdict":"yes"},
                  {"verdict":"no","reason":"Did not use bullets."}
                ]}
                """);
        var reason = PromptAlignmentSchemas.parseScoreReason("{\"reason\":\"One instruction was missed.\"}");

        assertAll(
                () -> assertEquals(2, verdicts.verdicts().size()),
                () -> assertEquals("yes", verdicts.verdicts().getFirst().verdict()),
                () -> assertEquals("Did not use bullets.", verdicts.verdicts().get(1).reason()),
                () -> assertEquals("One instruction was missed.", reason.reason()));
    }

    @Test
    void rejectsInvalidFieldsLikePydanticSchema() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> PromptAlignmentSchemas.parseVerdicts("{}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> PromptAlignmentSchemas.parseVerdicts("{\"verdicts\":\"yes\"}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> PromptAlignmentSchemas.parseVerdicts("{\"verdicts\":[{\"reason\":\"ok\"}]}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> PromptAlignmentSchemas.parseVerdicts("{\"verdicts\":[{\"verdict\":1}]}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> PromptAlignmentSchemas.parseVerdicts(
                                "{\"verdicts\":[{\"verdict\":\"yes\",\"reason\":1}]}")),
                () -> assertThrows(IllegalArgumentException.class, () -> PromptAlignmentSchemas.parseScoreReason("{}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> PromptAlignmentSchemas.parseScoreReason("{\"reason\":1}")));
    }
}
