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
import dev.jeval.metrics.FaithfulnessSchemas.FaithfulnessScoreReason;
import dev.jeval.metrics.FaithfulnessSchemas.FaithfulnessVerdict;
import dev.jeval.metrics.TurnFaithfulnessMetric.InteractionFaithfulnessScore;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TurnFaithfulnessMetricTest {

    @Test
    void measureExtractsTruthsClaimsScoresVerdictsAndGeneratesFinalReason() {
        var model = new ScriptedModel(List.of(
                "{\"truths\":[\"Refunds are available for 30 days.\"]}",
                "{\"claims\":[\"Refunds are available for 60 days.\",\"The assistant can start a refund.\"]}",
                "{\"verdicts\":[{\"verdict\":\"no\",\"reason\":\"The answer says 60 days but context says 30.\"},"
                        + "{\"verdict\":\"yes\"}]}",
                "{\"reason\":\"One claim contradicted the retrieval context.\"}",
                "{\"reason\":\"Final score reflects one contradiction.\"}"));
        var metric = new TurnFaithfulnessMetric(model, 0.5, true, false, false, null, 1);

        var result = metric.measure(testCaseWithContext(true));

        assertAll(
                () -> assertEquals("Turn Faithfulness", result.name()),
                () -> assertEquals(0.5, result.score()),
                () -> assertTrue(result.success()),
                () -> assertEquals("Final score reflects one contradiction.", result.reason()),
                () -> assertEquals(1, metric.scores().size()),
                () -> assertTrue(model.prompts().get(0).contains("Refunds are available for 30 days.")),
                () -> assertTrue(model.prompts().get(0).contains("excerpt (text and images)")),
                () -> assertTrue(model.prompts().get(1).contains("I can start a refund for 60 days.")),
                () -> assertTrue(model.prompts().get(1).contains("extract claims from all provided content")),
                () -> assertTrue(model.prompts().get(2).contains("Refunds are available for 30 days.")),
                () -> assertTrue(model.prompts().get(2).contains("images that's not mentioned")),
                () -> assertTrue(model.prompts().get(3).contains("The answer says 60 days")),
                () -> assertTrue(model.prompts().get(4).contains("TurnFaithfulnessMetric")),
                () -> assertTrue(model.prompts().get(4).contains("Example JSON")),
                () -> assertTrue(model.prompts().get(4).contains("single paragraph with no lists")),
                () -> assertTrue(model.prompts().get(4).contains("Output ONLY the reason")),
                () -> assertTrue(model.prompts().get(4).contains("One claim contradicted")));
    }

    @Test
    void includeReasonFalseSkipsReasonGeneration() {
        var model = new ScriptedModel(List.of(
                "{\"truths\":[\"Refunds are available for 30 days.\"]}",
                "{\"claims\":[\"The assistant can start a refund.\"]}",
                "{\"verdicts\":[{\"verdict\":\"yes\"}]}"));
        var metric = new TurnFaithfulnessMetric(model, 0.5, false, false, false, null, 1);

        var result = metric.measure(testCaseWithContext());

        assertAll(
                () -> assertEquals(1.0, result.score()),
                () -> assertTrue(result.success()),
                () -> assertNull(result.reason()),
                () -> assertEquals(3, model.prompts().size()));
    }

    @Test
    void strictModeZerosScoresBelowThreshold() {
        var metric = new StubTurnFaithfulnessMetric(List.of(
                new InteractionFaithfulnessScore(
                        0.5,
                        "Mixed.",
                        List.of("claim"),
                        List.of("truth"),
                        List.of(new FaithfulnessVerdict("yes", null),
                                new FaithfulnessVerdict("no", "Contradicted.")))),
                new FaithfulnessScoreReason("Final."),
                true,
                false);

        var result = metric.measure(testCaseWithContext());

        assertAll(
                () -> assertEquals(0.0, result.score()),
                () -> assertFalse(result.success()),
                () -> assertEquals(1.0, result.threshold()));
    }

    @Test
    void constructorRejectsNonFiniteThresholds() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new TurnFaithfulnessMetric(Double.NaN, true, false, false, null, 1)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new TurnFaithfulnessMetric(Double.POSITIVE_INFINITY, true, false, false, null, 1)));
    }

    @Test
    void penalizeAmbiguousClaimsSubtractsIdkVerdicts() {
        var metric = new StubTurnFaithfulnessMetric(List.of(
                new InteractionFaithfulnessScore(
                        0.0,
                        "Ambiguous.",
                        List.of("claim"),
                        List.of("truth"),
                        List.of(new FaithfulnessVerdict("idk", "Not supported.")))),
                new FaithfulnessScoreReason("Final."),
                false,
                true);

        var result = metric.measure(testCaseWithContext());

        assertEquals(0.0, result.score());
    }

    @Test
    void noRetrievalContextsScoresOne() {
        var metric = new TurnFaithfulnessMetric(0.5, true, false, false, null, 1);
        var result = metric.measure(ConversationalTestCase.builder(List.of(
                new Turn("user", "Need a refund."),
                new Turn("assistant", "I can help."))).build());

        assertAll(
                () -> assertEquals(1.0, result.score()),
                () -> assertTrue(result.success()),
                () -> assertTrue(result.reason().contains("no retrieval contexts")));
    }

    @Test
    void emptyClaimVerdictsReturnLocalReasonWithoutExtraModelCall() {
        var model = new ScriptedModel(List.of(
                "{\"truths\":[\"Refunds are available for 30 days.\"]}",
                "{\"claims\":[]}"));
        var metric = new ExposedTurnFaithfulnessMetric(model);

        var scores = metric.exposedScores(testCaseWithContext().turns());

        assertAll(
                () -> assertEquals(1.0, scores.getFirst().score()),
                () -> assertEquals("<no claims to verify>", scores.getFirst().reason()),
                () -> assertEquals(List.of(), scores.getFirst().verdicts()),
                () -> assertEquals(2, model.prompts().size()));
    }

    private static ConversationalTestCase testCaseWithContext() {
        return testCaseWithContext(false);
    }

    private static ConversationalTestCase testCaseWithContext(boolean multimodal) {
        return ConversationalTestCase.builder(List.of(
                new Turn("user", "Need a refund."),
                Turn.builder("assistant", "I can start a refund for 60 days.")
                        .retrievalContext(List.of("Refunds are available for 30 days."))
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

    private static final class StubTurnFaithfulnessMetric extends TurnFaithfulnessMetric {
        private final List<InteractionFaithfulnessScore> scores;
        private final FaithfulnessScoreReason reason;

        StubTurnFaithfulnessMetric(
                List<InteractionFaithfulnessScore> scores,
                FaithfulnessScoreReason reason,
                boolean strictMode,
                boolean penalizeAmbiguousClaims) {
            super(0.75, true, strictMode, penalizeAmbiguousClaims, null, 1);
            this.scores = scores;
            this.reason = reason;
        }

        @Override
        protected List<InteractionFaithfulnessScore> getFaithfulnessScores(List<Turn> turnsWindow) {
            return scores;
        }

        @Override
        protected FaithfulnessScoreReason generateReason() {
            return reason;
        }
    }

    private static final class ExposedTurnFaithfulnessMetric extends TurnFaithfulnessMetric {
        ExposedTurnFaithfulnessMetric(EvaluationModel model) {
            super(model, 0.5, true, false, false, null, 1);
        }

        List<InteractionFaithfulnessScore> exposedScores(List<Turn> turnsWindow) {
            return getFaithfulnessScores(turnsWindow);
        }
    }
}
