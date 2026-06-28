package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class ConversationCompletenessSchemasTest {

    @Test
    void parsesIntentionsVerdictAndReason() {
        var intentions = ConversationCompletenessSchemas.parseUserIntentions("""
                {"intentions":["Book a hotel","Book a flight"]}
                """);
        var verdict = ConversationCompletenessSchemas.parseVerdict("""
                {"verdict":"no","reason":"The flight was not booked."}
                """);
        var reason = ConversationCompletenessSchemas.parseReason("{\"reason\":\"One request was incomplete.\"}");

        assertEquals(List.of("Book a hotel", "Book a flight"), intentions.intentions());
        assertEquals("no", verdict.verdict());
        assertEquals("The flight was not booked.", verdict.reason());
        assertEquals("One request was incomplete.", reason.reason());
    }

    @Test
    void rejectsInvalidFieldsLikePydanticSchema() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ConversationCompletenessSchemas.parseUserIntentions("{}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ConversationCompletenessSchemas.parseUserIntentions("{\"intentions\":\"Book hotel\"}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ConversationCompletenessSchemas.parseUserIntentions("{\"intentions\":[1]}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ConversationCompletenessSchemas.parseVerdict("{\"reason\":\"ok\"}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ConversationCompletenessSchemas.parseVerdict("{\"verdict\":1}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ConversationCompletenessSchemas.parseVerdict("{\"verdict\":\"yes\",\"reason\":1}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ConversationCompletenessSchemas.parseReason("{}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ConversationCompletenessSchemas.parseReason("{\"reason\":1}")));
    }
}
