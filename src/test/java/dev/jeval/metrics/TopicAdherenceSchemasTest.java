package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class TopicAdherenceSchemasTest {

    @Test
    void parsesQaPairsVerdictAndReason() {
        var pairs = TopicAdherenceSchemas.parseQaPairs("""
                {"qa_pairs":[{"question":"Which food helps?","response":"Oats help."}]}
                """);
        var verdict = TopicAdherenceSchemas.parseVerdict("""
                {"verdict":"TP","reason":"Relevant and answered."}
                """);
        var reason = TopicAdherenceSchemas.parseReason("{\"reason\":\"Most turns stayed on topic.\"}");

        assertEquals(List.of(new TopicAdherenceSchemas.QAPair("Which food helps?", "Oats help.")), pairs.qaPairs());
        assertEquals("TP", verdict.verdict());
        assertEquals("Relevant and answered.", verdict.reason());
        assertEquals("Most turns stayed on topic.", reason.reason());
    }

    @Test
    void rejectsInvalidFieldsLikePydanticSchema() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> TopicAdherenceSchemas.parseQaPairs("{}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> TopicAdherenceSchemas.parseQaPairs("{\"qa_pairs\":\"bad\"}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> TopicAdherenceSchemas.parseQaPairs("{\"qa_pairs\":[{\"response\":\"A\"}]}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> TopicAdherenceSchemas.parseQaPairs("{\"qa_pairs\":[{\"question\":\"Q\",\"response\":1}]}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> TopicAdherenceSchemas.parseVerdict("{\"reason\":\"ok\"}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> TopicAdherenceSchemas.parseVerdict("{\"verdict\":\"maybe\",\"reason\":\"ok\"}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> TopicAdherenceSchemas.parseVerdict("{\"verdict\":\"TP\"}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> TopicAdherenceSchemas.parseVerdict("{\"verdict\":\"TP\",\"reason\":1}")),
                () -> assertThrows(IllegalArgumentException.class, () -> TopicAdherenceSchemas.parseReason("{}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> TopicAdherenceSchemas.parseReason("{\"reason\":1}")));
    }
}
