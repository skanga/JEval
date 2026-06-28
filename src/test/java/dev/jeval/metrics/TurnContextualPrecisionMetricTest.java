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
import dev.jeval.metrics.ContextualPrecisionSchemas.ContextualPrecisionScoreReason;
import dev.jeval.metrics.ContextualPrecisionSchemas.ContextualPrecisionVerdict;
import dev.jeval.metrics.TurnContextualPrecisionMetric.InteractionContextualPrecisionScore;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TurnContextualPrecisionMetricTest {

    @Test
    void measureUsesAveragePrecisionAndGeneratesFinalReason() {
        var model = new ScriptedModel(List.of(
                "{\"verdicts\":[{\"verdict\":\"yes\",\"reason\":\"Useful first node.\"},"
                        + "{\"verdict\":\"no\",\"reason\":\"Unrelated second node.\"},"
                        + "{\"verdict\":\"yes\",\"reason\":\"Useful third node.\"}]}",
                "{\"reason\":\"Useful nodes are mostly ranked high.\"}",
                "{\"reason\":\"Final score reflects ranking quality.\"}"));
        var metric = new TurnContextualPrecisionMetric(model, 0.5, true, false, 1);

        var result = metric.measure(testCaseWithContext(true));

        assertAll(
                () -> assertEquals("Turn Contextual Precision", result.name()),
                () -> assertEquals(5.0 / 6.0, result.score(), 1.0e-12),
                () -> assertTrue(result.success()),
                () -> assertEquals("Final score reflects ranking quality.", result.reason()),
                () -> assertEquals(1, metric.scores().size()),
                () -> assertTrue(model.prompts().get(0).contains("Need a refund.")),
                () -> assertTrue(model.prompts().get(0).contains("text or an image")),
                () -> assertTrue(model.prompts().get(0).contains("Refund the customer.")),
                () -> assertTrue(model.prompts().get(0).contains("Refund policy supports refunds.")),
                () -> assertTrue(model.prompts().get(1).contains("Unrelated second node.")),
                () -> assertTrue(model.prompts().get(1).contains("provide a CONCISE summarize")),
                () -> assertTrue(model.prompts().get(2).contains("TurnContextualPrecisionMetric")),
                () -> assertTrue(model.prompts().get(2).contains("Example JSON")),
                () -> assertTrue(model.prompts().get(2).contains("single paragraph with no lists")),
                () -> assertTrue(model.prompts().get(2).contains("Output ONLY the reason")),
                () -> assertTrue(model.prompts().get(2).contains("Useful nodes are mostly ranked high.")));
    }

    @Test
    void includeReasonFalseSkipsReasonGeneration() {
        var model = new ScriptedModel(List.of(
                "{\"verdicts\":[{\"verdict\":\"yes\",\"reason\":\"Useful.\"}]}"));
        var metric = new TurnContextualPrecisionMetric(model, 0.5, false, false, 1);

        var result = metric.measure(testCaseWithContext());

        assertAll(
                () -> assertEquals(1.0, result.score()),
                () -> assertTrue(result.success()),
                () -> assertNull(result.reason()),
                () -> assertEquals(1, model.prompts().size()));
    }

    @Test
    void strictModeZerosScoresBelowThreshold() {
        var metric = new StubTurnContextualPrecisionMetric(List.of(
                new InteractionContextualPrecisionScore(
                        0.5,
                        "Mixed ranking.",
                        List.of(new ContextualPrecisionVerdict("no", "Bad first."),
                                new ContextualPrecisionVerdict("yes", "Useful second.")))),
                new ContextualPrecisionScoreReason("Final."),
                true);

        var result = metric.measure(testCaseWithContext());

        assertAll(
                () -> assertEquals(0.0, result.score()),
                () -> assertFalse(result.success()),
                () -> assertEquals(1.0, result.threshold()));
    }

    @Test
    void noRetrievalContextsScoresOne() {
        var metric = new TurnContextualPrecisionMetric(0.5, true, false, 1);
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
        var metric = new TurnContextualPrecisionMetric(0.5, true, false, 1);

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
                                .retrievalContext(List.of(
                                        "Refund policy supports refunds.",
                                        "Office color is blue.",
                                        "Refunds are processed in five days."))
                                .build()))
                .expectedOutcome("Refund the customer.")
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

    private static final class StubTurnContextualPrecisionMetric extends TurnContextualPrecisionMetric {
        private final List<InteractionContextualPrecisionScore> scores;
        private final ContextualPrecisionScoreReason reason;

        StubTurnContextualPrecisionMetric(
                List<InteractionContextualPrecisionScore> scores,
                ContextualPrecisionScoreReason reason,
                boolean strictMode) {
            super(0.75, true, strictMode, 1);
            this.scores = scores;
            this.reason = reason;
        }

        @Override
        protected List<InteractionContextualPrecisionScore> getContextualPrecisionScores(
                List<Turn> turnsWindow,
                String expectedOutcome) {
            return scores;
        }

        @Override
        protected ContextualPrecisionScoreReason generateReason() {
            return reason;
        }
    }
}
