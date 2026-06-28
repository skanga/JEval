package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class PIILeakageSchemasTest {

    @Test
    void parsesExtractedPiiVerdictsAndReasonFromModelJson() {
        var extracted = PIILeakageSchemas.parseExtractedPII(
                "{\"extracted_pii\":[\"Call Jane at 555-0101.\"]}");
        var verdicts = PIILeakageSchemas.parseVerdicts("""
                {"verdicts":[
                  {"verdict":"yes","reason":"Contains a phone number."},
                  {"verdict":"no","reason":"No private data."}
                ]}
                """);
        var reason = PIILeakageSchemas.parseScoreReason("{\"reason\":\"One privacy issue was found.\"}");

        assertAll(
                () -> assertEquals(List.of("Call Jane at 555-0101."), extracted.extractedPII()),
                () -> assertEquals(2, verdicts.verdicts().size()),
                () -> assertEquals("yes", verdicts.verdicts().getFirst().verdict()),
                () -> assertEquals("No private data.", verdicts.verdicts().get(1).reason()),
                () -> assertEquals("One privacy issue was found.", reason.reason()));
    }

    @Test
    void rejectsMissingOrInvalidFieldsLikeDeepEval() {
        assertThrows(IllegalArgumentException.class, () -> PIILeakageSchemas.parseExtractedPII("{}"));
        assertThrows(IllegalArgumentException.class, () -> PIILeakageSchemas.parseExtractedPII("{\"extracted_pii\":\"one\"}"));
        assertThrows(IllegalArgumentException.class, () -> PIILeakageSchemas.parseExtractedPII("{\"extracted_pii\":[1]}"));
        assertThrows(IllegalArgumentException.class, () -> PIILeakageSchemas.parseVerdicts("{}"));
        assertThrows(IllegalArgumentException.class, () -> PIILeakageSchemas.parseVerdicts("{\"verdicts\":\"yes\"}"));
        assertThrows(IllegalArgumentException.class, () -> PIILeakageSchemas.parseVerdicts("{\"verdicts\":[{\"reason\":\"ok\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> PIILeakageSchemas.parseVerdicts("{\"verdicts\":[{\"verdict\":\"yes\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> PIILeakageSchemas.parseVerdicts("{\"verdicts\":[{\"verdict\":1,\"reason\":\"ok\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> PIILeakageSchemas.parseVerdicts("{\"verdicts\":[{\"verdict\":\"yes\",\"reason\":1}]}"));
        assertThrows(IllegalArgumentException.class, () -> PIILeakageSchemas.parseScoreReason("{}"));
        assertThrows(IllegalArgumentException.class, () -> PIILeakageSchemas.parseScoreReason("{\"reason\":1}"));
    }
}
