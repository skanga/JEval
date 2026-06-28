package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.ConversationalTestCase;
import dev.jeval.EvaluationModel;
import dev.jeval.Turn;
import dev.jeval.metrics.ConversationCompletenessSchemas.ConversationCompletenessScoreReason;
import dev.jeval.metrics.ConversationCompletenessSchemas.ConversationCompletenessVerdict;
import dev.jeval.metrics.ConversationCompletenessSchemas.UserIntentions;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConversationCompletenessMetricTest {

    @Test
    void measureExtractsIntentionsScoresVerdictsAndGeneratesReason() {
        var model = new ScriptedModel(List.of(
                "{\"intentions\":[\"Book a hotel\",\"Book a flight\"]}",
                "{\"verdict\":\"yes\"}",
                "{\"verdict\":\"no\",\"reason\":\"The flight was not booked.\"}",
                "{\"reason\":\"The hotel was booked, but the flight request was incomplete.\"}"));
        var metric = new ConversationCompletenessMetric(model);

        var result = metric.measure(ConversationalTestCase.builder(List.of(
                new Turn("user", "Need a hotel and flight."),
                new Turn("assistant", "I booked the hotel.")))
                .multimodal(true)
                .build());

        assertAll(
                () -> assertEquals("Conversation Completeness", result.name()),
                () -> assertEquals(0.5, result.score()),
                () -> assertTrue(result.success()),
                () -> assertEquals("The hotel was booked, but the flight request was incomplete.", result.reason()),
                () -> assertEquals(List.of("Book a hotel", "Book a flight"), metric.userIntentions()),
                () -> assertEquals(2, metric.verdicts().size()),
                () -> assertTrue(model.prompts().get(0).contains("overall objective of the conversation")),
                () -> assertTrue(model.prompts().get(0).contains("Example Turns")),
                () -> assertTrue(model.prompts().get(0).contains("Example JSON")),
                () -> assertTrue(model.prompts().get(0).contains("Nothing, I'm just playing with you.")),
                () -> assertTrue(model.prompts().get(0).contains("===== END OF EXAMPLE ======")),
                () -> assertTrue(model.prompts().get(0).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(0).contains("Need a hotel and flight.")),
                () -> assertTrue(model.prompts().get(1).contains("verdict' key should STRICTLY be either 'yes' or 'no'")),
                () -> assertTrue(model.prompts().get(1).contains("Example Intention")),
                () -> assertTrue(model.prompts().get(1).contains("You MUST TRY to quote some LLM responses")),
                () -> assertTrue(model.prompts().get(1).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(1).contains("Book a hotel")),
                () -> assertTrue(model.prompts().get(2).contains("Book a flight")),
                () -> assertTrue(model.prompts().get(3).contains("minimal knowledge")),
                () -> assertTrue(model.prompts().get(3).contains("OVERALL `actual_output`s")),
                () -> assertTrue(model.prompts().get(3).contains("Example JSON")),
                () -> assertTrue(model.prompts().get(3).contains("Always quote information")),
                () -> assertTrue(model.prompts().get(3).contains("Be sure in your reason")),
                () -> assertTrue(model.prompts().get(3).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(3).contains("0.5")),
                () -> assertTrue(model.prompts().get(3).contains("The flight was not booked.")));
    }

    @Test
    void includeReasonFalseSkipsReasonGeneration() {
        var model = new ScriptedModel(List.of(
                "{\"intentions\":[\"Book a hotel\"]}",
                "{\"verdict\":\"yes\"}"));
        var metric = new ConversationCompletenessMetric(model, 0.5, false, false);

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals(1.0, result.score()),
                () -> assertTrue(result.success()),
                () -> assertNull(result.reason()),
                () -> assertEquals(2, model.prompts().size()));
    }

    @Test
    void strictModeZerosScoresBelowThreshold() {
        var metric = new StubConversationCompletenessMetric(
                new UserIntentions(List.of("Book a hotel", "Book a flight")),
                List.of(
                        new ConversationCompletenessVerdict("yes", null),
                        new ConversationCompletenessVerdict("no", "Missing flight.")),
                new ConversationCompletenessScoreReason("Missing flight."),
                true);

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals(0.0, result.score()),
                () -> assertFalse(result.success()),
                () -> assertEquals(1.0, result.threshold()));
    }

    @Test
    void noVerdictsScoresOne() {
        var metric = new StubConversationCompletenessMetric(
                new UserIntentions(List.of()),
                List.of(),
                new ConversationCompletenessScoreReason("No missing intentions."),
                false);

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals(1.0, result.score()),
                () -> assertTrue(result.success()));
    }

    private static ConversationalTestCase testCase() {
        return ConversationalTestCase.builder(List.of(
                new Turn("user", "Need a hotel and flight."),
                new Turn("assistant", "I booked the hotel."))).build();
    }

    private static final class ScriptedModel implements EvaluationModel {
        private final List<String> responses;
        private final List<String> prompts = new ArrayList<>();

        ScriptedModel(List<String> responses) {
            this.responses = responses;
        }

        @Override
        public String generate(String prompt) {
            prompts.add(prompt);
            return responses.get(prompts.size() - 1);
        }

        List<String> prompts() {
            return prompts;
        }
    }

    private static final class StubConversationCompletenessMetric extends ConversationCompletenessMetric {
        private final UserIntentions intentions;
        private final List<ConversationCompletenessVerdict> verdicts;
        private final ConversationCompletenessScoreReason reason;

        StubConversationCompletenessMetric(
                UserIntentions intentions,
                List<ConversationCompletenessVerdict> verdicts,
                ConversationCompletenessScoreReason reason,
                boolean strictMode) {
            super(0.5, true, strictMode);
            this.intentions = intentions;
            this.verdicts = verdicts;
            this.reason = reason;
        }

        @Override
        protected UserIntentions extractUserIntentions(List<Turn> turns, boolean multimodal) {
            return intentions;
        }

        @Override
        protected ConversationCompletenessVerdict generateVerdict(List<Turn> turns, String intention, boolean multimodal) {
            return verdicts.get(intentions.intentions().indexOf(intention));
        }

        @Override
        protected ConversationCompletenessScoreReason generateReason(boolean multimodal) {
            return reason;
        }
    }
}
