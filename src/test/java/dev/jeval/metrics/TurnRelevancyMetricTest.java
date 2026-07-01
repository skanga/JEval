package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.ConversationalTestCase;
import dev.jeval.EvaluationModel;
import dev.jeval.Turn;
import dev.jeval.metrics.TurnRelevancySchemas.TurnRelevancyReason;
import dev.jeval.metrics.TurnRelevancySchemas.TurnRelevancyVerdict;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TurnRelevancyMetricTest {

    @Test
    void measureScoresSlidingWindowsAndGeneratesReason() {
        var model = new ScriptedModel(List.of(
                "{\"verdict\":\"yes\",\"reason\":\"The answer follows the user request.\"}",
                "{\"verdict\":\"no\",\"reason\":\"The answer changes topic.\"}",
                "{\"reason\":\"One window changed topic.\"}"));
        var metric = new TurnRelevancyMetric(model, 0.5, true, false, 1);

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals("Turn Relevancy", result.name()),
                () -> assertEquals(0.5, result.score()),
                () -> assertTrue(result.success()),
                () -> assertEquals("One window changed topic.", result.reason()),
                () -> assertEquals(2, metric.verdicts().size()),
                () -> assertTrue(model.prompts().get(0).contains("LAST `assistant` message")),
                () -> assertTrue(model.prompts().get(0).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(0).contains("STRICTLY be either 'yes' or 'no'")),
                () -> assertTrue(model.prompts().get(0).contains("Example Messages")),
                () -> assertTrue(model.prompts().get(0).contains("Example JSON")),
                () -> assertTrue(model.prompts().get(0).contains("===== END OF EXAMPLE ======")),
                () -> assertTrue(model.prompts().get(0).contains("MUST USE the previous messages")),
                () -> assertTrue(model.prompts().get(0).contains("Book a hotel.")),
                () -> assertTrue(model.prompts().get(1).contains("COMPLETELY irrelevant")),
                () -> assertTrue(model.prompts().get(1).contains("The weather is sunny.")),
                () -> assertTrue(model.prompts().get(2).contains("Always quote WHICH MESSAGE")),
                () -> assertTrue(model.prompts().get(2).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(2).contains("Example JSON")),
                () -> assertTrue(model.prompts().get(2).contains("<relevancy_score>")),
                () -> assertTrue(model.prompts().get(2).contains("only return in JSON format")),
                () -> assertTrue(model.prompts().get(2).contains("Relevancy Score")),
                () -> assertTrue(model.prompts().get(2).contains("The answer changes topic.")));
    }

    @Test
    void includeReasonFalseSkipsReasonGeneration() {
        var model = new ScriptedModel(List.of(
                "{\"verdict\":\"yes\",\"reason\":\"Relevant.\"}",
                "{\"verdict\":\"yes\",\"reason\":\"Relevant.\"}"));
        var metric = new TurnRelevancyMetric(model, 0.5, false, false, 1);

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals(1.0, result.score()),
                () -> assertTrue(result.success()),
                () -> assertNull(result.reason()),
                () -> assertEquals(2, model.prompts().size()));
    }

    @Test
    void strictModeZerosScoresBelowThreshold() {
        var metric = new StubTurnRelevancyMetric(
                List.of(new TurnRelevancyVerdict("yes", "Relevant."),
                        new TurnRelevancyVerdict("no", "Irrelevant.")),
                new TurnRelevancyReason("One turn failed."),
                true);

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals(0.0, result.score()),
                () -> assertFalse(result.success()),
                () -> assertEquals(1.0, result.threshold()));
    }

    @Test
    void constructorRejectsNonFiniteThresholds() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new TurnRelevancyMetric(Double.NaN, true, false, 1)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new TurnRelevancyMetric(Double.POSITIVE_INFINITY, true, false, 1)));
    }

    @Test
    void noUnitInteractionsScoresOne() {
        var metric = new StubTurnRelevancyMetric(List.of(), null, false);
        var result = metric.measure(ConversationalTestCase.builder(List.of(new Turn("user", "Hello"))).build());

        assertAll(
                () -> assertEquals(1.0, result.score()),
                () -> assertTrue(result.success()),
                () -> assertNull(result.reason()));
    }

    private static ConversationalTestCase testCase() {
        return ConversationalTestCase.builder(List.of(
                new Turn("user", "Book a hotel."),
                new Turn("assistant", "I booked a hotel."),
                new Turn("user", "What about the flight?"),
                new Turn("assistant", "The weather is sunny."))).build();
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

    private static final class StubTurnRelevancyMetric extends TurnRelevancyMetric {
        private final List<TurnRelevancyVerdict> verdicts;
        private final TurnRelevancyReason reason;
        private int index;

        StubTurnRelevancyMetric(
                List<TurnRelevancyVerdict> verdicts,
                TurnRelevancyReason reason,
                boolean strictMode) {
            super(0.75, true, strictMode, 1);
            this.verdicts = verdicts;
            this.reason = reason;
        }

        @Override
        protected TurnRelevancyVerdict generateVerdict(List<Turn> turnsSlidingWindow) {
            return verdicts.get(index++);
        }

        @Override
        protected TurnRelevancyReason generateReason() {
            return reason;
        }
    }
}
