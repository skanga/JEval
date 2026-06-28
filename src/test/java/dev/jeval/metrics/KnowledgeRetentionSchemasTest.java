package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KnowledgeRetentionSchemasTest {

    @Test
    void parsesKnowledgeVerdictAndReason() {
        var knowledge = KnowledgeRetentionSchemas.parseKnowledge("""
                {"data":{"Name":"Ada","Diet":["Vegetarian","Peanut Allergy"]}}
                """);
        var verdict = KnowledgeRetentionSchemas.parseVerdict("""
                {"verdict":"yes","reason":"Asked for allergies again."}
                """);
        var reason = KnowledgeRetentionSchemas.parseReason("{\"reason\":\"The assistant forgot known allergies.\"}");

        assertEquals(Map.of("Name", "Ada", "Diet", List.of("Vegetarian", "Peanut Allergy")), knowledge.data());
        assertEquals("yes", verdict.verdict());
        assertEquals("Asked for allergies again.", verdict.reason());
        assertEquals("The assistant forgot known allergies.", reason.reason());
    }

    @Test
    void allowsMissingOptionalKnowledgeData() {
        assertNull(KnowledgeRetentionSchemas.parseKnowledge("{}").data());
    }

    @Test
    void rejectsInvalidFieldsLikePydanticSchema() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> KnowledgeRetentionSchemas.parseKnowledge("{\"data\":\"Ada\"}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> KnowledgeRetentionSchemas.parseKnowledge("{\"data\":{\"Name\":1}}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> KnowledgeRetentionSchemas.parseKnowledge("{\"data\":{\"Diet\":[\"Vegetarian\",1]}}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> KnowledgeRetentionSchemas.parseKnowledge("{\"data\":{},\"extra\":\"bad\"}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> KnowledgeRetentionSchemas.parseVerdict("{\"reason\":\"ok\"}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> KnowledgeRetentionSchemas.parseVerdict("{\"verdict\":1}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> KnowledgeRetentionSchemas.parseVerdict("{\"verdict\":\"yes\",\"reason\":1}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> KnowledgeRetentionSchemas.parseVerdict(
                                "{\"verdict\":\"yes\",\"reason\":\"ok\",\"extra\":\"bad\"}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> KnowledgeRetentionSchemas.parseReason("{}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> KnowledgeRetentionSchemas.parseReason("{\"reason\":1}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> KnowledgeRetentionSchemas.parseReason("{\"reason\":\"ok\",\"extra\":\"bad\"}")));
    }
}
