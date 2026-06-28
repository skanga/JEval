package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TurnRelevancySchemasTest {

    @Test
    void parsesVerdictAndReason() {
        var verdict = TurnRelevancySchemas.parseVerdict(
                "prefix {\"verdict\":\"no\",\"reason\":\"The answer changes topic.\"} suffix");
        var reason = TurnRelevancySchemas.parseReason("{\"reason\":\"One turn was irrelevant.\"}");

        assertEquals("no", verdict.verdict());
        assertEquals("The answer changes topic.", verdict.reason());
        assertEquals("One turn was irrelevant.", reason.reason());
    }

    @Test
    void missingVerdictReasonIsNull() {
        var verdict = TurnRelevancySchemas.parseVerdict("{\"verdict\":\"yes\"}");

        assertEquals("yes", verdict.verdict());
        assertNull(verdict.reason());
    }

    @Test
    void rejectsInvalidFieldsLikePydanticSchema() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> TurnRelevancySchemas.parseVerdict("{}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> TurnRelevancySchemas.parseVerdict("{\"verdict\":1}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> TurnRelevancySchemas.parseVerdict("{\"verdict\":\"yes\",\"reason\":1}")),
                () -> assertThrows(IllegalArgumentException.class, () -> TurnRelevancySchemas.parseReason("{}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> TurnRelevancySchemas.parseReason("{\"reason\":1}")));
    }
}
