package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.ConversationalTestCase;
import dev.jeval.EvaluationModel;
import dev.jeval.MissingTestCaseParamsException;
import dev.jeval.Turn;
import dev.jeval.metrics.ContextualRecallSchemas.ContextualRecallScoreReason;
import dev.jeval.metrics.ContextualRecallSchemas.ContextualRecallVerdict;
import dev.jeval.metrics.TurnContextualRecallMetric.InteractionContextualRecallScore;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TurnContextualRecallMetricTest {

    @Test
    void measureScoresRetrievalContextVerdictsAndGeneratesFinalReason() {
        var model = new ScriptedModel(List.of(
                "{\"verdicts\":[{\"verdict\":\"yes\",\"reason\":\"Expected outcome is supported.\"},"
                        + "{\"verdict\":\"no\",\"reason\":\"Cancellation detail missing.\"}]}",
                "{\"reason\":\"Cancellation detail was not supported.\"}",
                "{\"reason\":\"Final score reflects partial recall.\"}"));
        var metric = new TurnContextualRecallMetric(model, 0.5, true, false, 1);

        var result = metric.measure(testCaseWithContext(true));

        assertAll(
                () -> assertEquals("Turn Contextual Recall", result.name()),
                () -> assertEquals(0.5, result.score()),
                () -> assertTrue(result.success()),
                () -> assertEquals("Final score reflects partial recall.", result.reason()),
                () -> assertEquals(1, metric.scores().size()),
                () -> assertTrue(model.prompts().get(0).contains("Refund the customer and mention cancellation.")),
                () -> assertTrue(model.prompts().get(0).contains("sentence and image")),
                () -> assertTrue(model.prompts().get(0).contains("Refund policy supports refunds.")),
                () -> assertTrue(model.prompts().get(1).contains("Cancellation detail missing.")),
                () -> assertTrue(model.prompts().get(1).contains("sentence or image")),
                () -> assertTrue(model.prompts().get(2).contains("TurnContextualRecallMetric")),
                () -> assertTrue(model.prompts().get(2).contains("Example JSON")),
                () -> assertTrue(model.prompts().get(2).contains("single paragraph with no lists")),
                () -> assertTrue(model.prompts().get(2).contains("Output ONLY the reason")),
                () -> assertTrue(model.prompts().get(2).contains("Cancellation detail was not supported.")));
    }

    @Test
    void includeReasonFalseSkipsReasonGeneration() {
        var model = new ScriptedModel(List.of(
                "{\"verdicts\":[{\"verdict\":\"yes\",\"reason\":\"Supported.\"}]}"));
        var metric = new TurnContextualRecallMetric(model, 0.5, false, false, 1);

        var result = metric.measure(testCaseWithContext());

        assertAll(
                () -> assertEquals(1.0, result.score()),
                () -> assertTrue(result.success()),
                () -> assertNull(result.reason()),
                () -> assertEquals(1, model.prompts().size()));
    }

    @Test
    void strictModeZerosScoresBelowThreshold() {
        var metric = new StubTurnContextualRecallMetric(List.of(
                new InteractionContextualRecallScore(
                        0.5,
                        "Mixed support.",
                        List.of(new ContextualRecallVerdict("yes", "Supported."),
                                new ContextualRecallVerdict("no", "Missing.")))),
                new ContextualRecallScoreReason("Final."),
                true);

        var result = metric.measure(testCaseWithContext());

        assertAll(
                () -> assertEquals(0.0, result.score()),
                () -> assertFalse(result.success()),
                () -> assertEquals(1.0, result.threshold()));
    }

    @Test
    void noRetrievalContextsScoresOne() {
        var metric = new TurnContextualRecallMetric(0.5, true, false, 1);
        var result = metric.measure(ConversationalTestCase.builder(List.of(
                        new Turn("user", "Need a refund."),
                        new Turn("assistant", "I can help.")))
                .expectedOutcome("Refund the customer.")
                .build());

        assertAll(
                () -> assertEquals(1.0, result.score()),
                () -> assertTrue(result.success()),
                () -> assertTrue(result.reason().contains("no retrieval contexts")));
    }

    @Test
    void requiresExpectedOutcome() {
        var metric = new TurnContextualRecallMetric(0.5, true, false, 1);

        assertThrows(MissingTestCaseParamsException.class,
                () -> metric.measure(ConversationalTestCase.builder(List.of(
                        new Turn("user", "Need a refund."),
                        new Turn("assistant", "I can help."))).build()));
    }

    private static ConversationalTestCase testCaseWithContext() {
        return testCaseWithContext(false);
    }

    private static ConversationalTestCase testCaseWithContext(boolean multimodal) {
        return ConversationalTestCase.builder(List.of(
                        new Turn("user", "Need a refund."),
                        Turn.builder("assistant", "I can help.")
                                .retrievalContext(List.of("Refund policy supports refunds."))
                                .build()))
                .expectedOutcome("Refund the customer and mention cancellation.")
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

    private static final class StubTurnContextualRecallMetric extends TurnContextualRecallMetric {
        private final List<InteractionContextualRecallScore> scores;
        private final ContextualRecallScoreReason reason;

        StubTurnContextualRecallMetric(
                List<InteractionContextualRecallScore> scores,
                ContextualRecallScoreReason reason,
                boolean strictMode) {
            super(0.75, true, strictMode, 1);
            this.scores = scores;
            this.reason = reason;
        }

        @Override
        protected List<InteractionContextualRecallScore> getContextualRecallScores(
                List<Turn> turnsWindow,
                String expectedOutcome) {
            return scores;
        }

        @Override
        protected ContextualRecallScoreReason generateReason() {
            return reason;
        }
    }
}
