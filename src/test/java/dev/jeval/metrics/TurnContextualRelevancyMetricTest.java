package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.ConversationalTestCase;
import dev.jeval.EvaluationModel;
import dev.jeval.Turn;
import dev.jeval.metrics.ContextualRelevancySchemas.ContextualRelevancyScoreReason;
import dev.jeval.metrics.ContextualRelevancySchemas.ContextualRelevancyVerdict;
import dev.jeval.metrics.TurnContextualRelevancyMetric.InteractionContextualRelevancyScore;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TurnContextualRelevancyMetricTest {

    @Test
    void measureScoresRetrievalContextVerdictsAndGeneratesFinalReason() {
        var model = new ScriptedModel(List.of(
                "{\"verdicts\":[{\"statement\":\"Hotel check-in is 3 PM.\",\"verdict\":\"yes\"}]}",
                "{\"verdicts\":[{\"statement\":\"Airport shuttle runs hourly.\",\"verdict\":\"no\",\"reason\":\"Airport shuttle is unrelated.\"}]}",
                "{\"reason\":\"One context was unrelated to the hotel question.\"}",
                "{\"reason\":\"Final score reflects one relevant and one irrelevant context.\"}"));
        var metric = new TurnContextualRelevancyMetric(model, 0.5, true, false, 1);

        var result = metric.measure(testCaseWithContext(true));

        assertAll(
                () -> assertEquals("Turn Contextual Relevancy", result.name()),
                () -> assertEquals(0.5, result.score()),
                () -> assertTrue(result.success()),
                () -> assertEquals("Final score reflects one relevant and one irrelevant context.", result.reason()),
                () -> assertEquals(1, metric.scores().size()),
                () -> assertTrue(model.prompts().get(0).contains("Need a hotel.")),
                () -> assertTrue(model.prompts().get(0).contains("context (image or string)")),
                () -> assertTrue(model.prompts().get(1).contains("Airport shuttle runs hourly.")),
                () -> assertTrue(model.prompts().get(2).contains("Airport shuttle is unrelated.")),
                () -> assertTrue(model.prompts().get(2).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(3).contains("TurnContextualRelevancyMetric")),
                () -> assertTrue(model.prompts().get(3).contains("Example JSON")),
                () -> assertTrue(model.prompts().get(3).contains("single paragraph with no lists")),
                () -> assertTrue(model.prompts().get(3).contains("Output ONLY the reason")),
                () -> assertTrue(model.prompts().get(3).contains("One context was unrelated")));
    }

    @Test
    void includeReasonFalseSkipsReasonGeneration() {
        var model = new ScriptedModel(List.of(
                "{\"verdicts\":[{\"statement\":\"Hotel check-in is 3 PM.\",\"verdict\":\"yes\"}]}",
                "{\"verdicts\":[{\"statement\":\"Airport shuttle runs hourly.\",\"verdict\":\"yes\"}]}"));
        var metric = new TurnContextualRelevancyMetric(model, 0.5, false, false, 1);

        var result = metric.measure(testCaseWithContext());

        assertAll(
                () -> assertEquals(1.0, result.score()),
                () -> assertTrue(result.success()),
                () -> assertNull(result.reason()),
                () -> assertEquals(2, model.prompts().size()));
    }

    @Test
    void strictModeZerosScoresBelowThreshold() {
        var metric = new StubTurnContextualRelevancyMetric(List.of(
                new InteractionContextualRelevancyScore(
                        0.5,
                        "Mixed.",
                        List.of(new ContextualRelevancyVerdict("ok", "yes", null),
                                new ContextualRelevancyVerdict("bad", "no", "Wrong.")))),
                new ContextualRelevancyScoreReason("Final."),
                true);

        var result = metric.measure(testCaseWithContext());

        assertAll(
                () -> assertEquals(0.0, result.score()),
                () -> assertFalse(result.success()),
                () -> assertEquals(1.0, result.threshold()));
    }

    @Test
    void noRetrievalContextsScoresOne() {
        var metric = new TurnContextualRelevancyMetric(0.5, true, false, 1);
        var result = metric.measure(ConversationalTestCase.builder(List.of(
                new Turn("user", "Need a hotel."),
                new Turn("assistant", "I can help."))).build());

        assertAll(
                () -> assertEquals(1.0, result.score()),
                () -> assertTrue(result.success()),
                () -> assertTrue(result.reason().contains("no retrieval contexts")));
    }

    private static ConversationalTestCase testCaseWithContext() {
        return testCaseWithContext(false);
    }

    private static ConversationalTestCase testCaseWithContext(boolean multimodal) {
        return ConversationalTestCase.builder(List.of(
                new Turn("user", "Need a hotel."),
                Turn.builder("assistant", "I can help.")
                        .retrievalContext(List.of("Hotel check-in is 3 PM.", "Airport shuttle runs hourly."))
                        .build()))
                .multimodal(multimodal)
                .build();
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

    private static final class StubTurnContextualRelevancyMetric extends TurnContextualRelevancyMetric {
        private final List<InteractionContextualRelevancyScore> scores;
        private final ContextualRelevancyScoreReason reason;

        StubTurnContextualRelevancyMetric(
                List<InteractionContextualRelevancyScore> scores,
                ContextualRelevancyScoreReason reason,
                boolean strictMode) {
            super(0.75, true, strictMode, 1);
            this.scores = scores;
            this.reason = reason;
        }

        @Override
        protected List<InteractionContextualRelevancyScore> getContextualRelevancyScores(List<Turn> turnsWindow) {
            return scores;
        }

        @Override
        protected ContextualRelevancyScoreReason generateReason() {
            return reason;
        }
    }
}
