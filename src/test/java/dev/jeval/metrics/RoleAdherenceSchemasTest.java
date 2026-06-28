package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RoleAdherenceSchemasTest {

    @Test
    void parsesVerdictsAndReason() {
        var verdicts = RoleAdherenceSchemas.parseVerdicts("""
                {"verdicts":[{"index":3,"reason":"Too arrogant."}]}
                """);
        var reason = RoleAdherenceSchemas.parseReason("{\"reason\":\"One response broke character.\"}");

        assertEquals(1, verdicts.verdicts().size());
        assertEquals(3, verdicts.verdicts().getFirst().index());
        assertEquals("Too arrogant.", verdicts.verdicts().getFirst().reason());
        assertEquals("One response broke character.", reason.reason());
    }

    @Test
    void rejectsInvalidFieldsLikePydanticSchema() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> RoleAdherenceSchemas.parseVerdicts("{}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> RoleAdherenceSchemas.parseVerdicts("{\"verdicts\":{\"index\":1,\"reason\":\"bad\"}}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> RoleAdherenceSchemas.parseVerdicts("{\"verdicts\":\"bad\"}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> RoleAdherenceSchemas.parseVerdicts("{\"verdicts\":[{\"reason\":\"bad\"}]}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> RoleAdherenceSchemas.parseVerdicts("{\"verdicts\":[{\"index\":\"1\",\"reason\":\"bad\"}]}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> RoleAdherenceSchemas.parseVerdicts("{\"verdicts\":[{\"index\":1}]}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> RoleAdherenceSchemas.parseVerdicts("{\"verdicts\":[{\"index\":1,\"reason\":1}]}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> RoleAdherenceSchemas.parseVerdicts(
                                "{\"verdicts\":[{\"index\":1,\"reason\":\"bad\",\"ai_message\":1}]}")),
                () -> assertThrows(IllegalArgumentException.class, () -> RoleAdherenceSchemas.parseReason("{}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> RoleAdherenceSchemas.parseReason("{\"reason\":1}")));
    }
}
