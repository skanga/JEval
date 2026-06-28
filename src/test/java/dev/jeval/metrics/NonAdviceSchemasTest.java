package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class NonAdviceSchemasTest {

    @Test
    void parsesAdvicesVerdictsAndReasonFromModelJson() {
        var advices = NonAdviceSchemas.parseAdvices("{\"advices\":[\"Buy this stock.\"]}");
        var verdicts = NonAdviceSchemas.parseVerdicts("""
                {"verdicts":[
                  {"verdict":"yes","reason":"Specific investment advice."},
                  {"verdict":"no","reason":"General education."}
                ]}
                """);
        var reason = NonAdviceSchemas.parseScoreReason("{\"reason\":\"One advice violation was found.\"}");

        assertAll(
                () -> assertEquals(List.of("Buy this stock."), advices.advices()),
                () -> assertEquals(2, verdicts.verdicts().size()),
                () -> assertEquals("yes", verdicts.verdicts().getFirst().verdict()),
                () -> assertEquals("General education.", verdicts.verdicts().get(1).reason()),
                () -> assertEquals("One advice violation was found.", reason.reason()));
    }

    @Test
    void rejectsMissingOrInvalidFieldsLikeDeepEval() {
        assertThrows(IllegalArgumentException.class, () -> NonAdviceSchemas.parseAdvices("{}"));
        assertThrows(IllegalArgumentException.class, () -> NonAdviceSchemas.parseAdvices("{\"advices\":\"one\"}"));
        assertThrows(IllegalArgumentException.class, () -> NonAdviceSchemas.parseAdvices("{\"advices\":[1]}"));
        assertThrows(IllegalArgumentException.class, () -> NonAdviceSchemas.parseVerdicts("{}"));
        assertThrows(IllegalArgumentException.class, () -> NonAdviceSchemas.parseVerdicts("{\"verdicts\":\"yes\"}"));
        assertThrows(IllegalArgumentException.class, () -> NonAdviceSchemas.parseVerdicts("{\"verdicts\":[{\"reason\":\"ok\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> NonAdviceSchemas.parseVerdicts("{\"verdicts\":[{\"verdict\":\"yes\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> NonAdviceSchemas.parseVerdicts("{\"verdicts\":[{\"verdict\":1,\"reason\":\"ok\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> NonAdviceSchemas.parseVerdicts("{\"verdicts\":[{\"verdict\":\"yes\",\"reason\":1}]}"));
        assertThrows(IllegalArgumentException.class, () -> NonAdviceSchemas.parseScoreReason("{}"));
        assertThrows(IllegalArgumentException.class, () -> NonAdviceSchemas.parseScoreReason("{\"reason\":1}"));
    }
}
