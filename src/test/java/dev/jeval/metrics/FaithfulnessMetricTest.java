package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.EvaluationModel;
import dev.jeval.LlmTestCase;
import dev.jeval.MissingTestCaseParamsException;
import dev.jeval.metrics.FaithfulnessSchemas.FaithfulnessVerdict;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FaithfulnessMetricTest {

    @Test
    void measureScoresNonNoVerdictsAsFaithful() {
        var metric = new StubFaithfulnessMetric(List.of(
                new FaithfulnessVerdict("yes", null),
                new FaithfulnessVerdict("idk", "ambiguous"),
                new FaithfulnessVerdict("no", "contradicted")));

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals("Faithfulness", result.name()),
                () -> assertEquals(2.0 / 3.0, result.score()),
                () -> assertTrue(result.success()),
                () -> assertEquals("reason", result.reason()),
                () -> assertEquals(List.of("truth"), metric.truths()),
                () -> assertEquals(List.of("claim"), metric.claims()));
    }

    @Test
    void measureScoresOneWhenThereAreNoVerdicts() {
        var result = new StubFaithfulnessMetric(List.of()).measure(testCase());

        assertEquals(1.0, result.score());
    }

    @Test
    void penalizeAmbiguousClaimsSubtractsIdkVerdicts() {
        var metric = new StubFaithfulnessMetric(List.of(
                new FaithfulnessVerdict("idk", "unclear")),
                false,
                true);

        var result = metric.measure(testCase());

        assertEquals(0.0, result.score());
    }

    @Test
    void strictModeZerosScoresBelowPerfect() {
        var metric = new StubFaithfulnessMetric(List.of(
                new FaithfulnessVerdict("yes", null),
                new FaithfulnessVerdict("no", null)),
                true,
                false);

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
                        () -> new FaithfulnessMetric(Double.NaN, true, false, false)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new FaithfulnessMetric(Double.POSITIVE_INFINITY, true, false, false)));
    }

    @Test
    void measureAllowsEmptyInputLikeDeepEval() {
        var result = new StubFaithfulnessMetric(List.of())
                .measure(LlmTestCase.builder("").actualOutput("output").retrievalContext(List.of("ctx")).build());

        assertEquals(1.0, result.score());
    }

    @Test
    void measureRequiresActualOutputAndRetrievalContext() {
        var metric = new StubFaithfulnessMetric(List.of());

        assertThrows(MissingTestCaseParamsException.class,
                () -> metric.measure(LlmTestCase.builder("input").actualOutput("").retrievalContext(List.of("ctx")).build()));
        assertThrows(MissingTestCaseParamsException.class,
                () -> metric.measure(LlmTestCase.builder("input").actualOutput("output").build()));
    }

    @Test
    void measureUsesModelPromptsAndParsesJsonResponses() {
        var model = new ScriptedModel(List.of(
                "{\"truths\": [\"Refunds last 30 days.\"]}",
                "{\"claims\": [\"Refunds last 60 days.\"]}",
                "{\"verdicts\": [{\"verdict\": \"no\", \"reason\": \"The output says 60 days, but context says 30.\"}]}",
                "{\"reason\": \"The claim contradicts the retrieval context.\"}"));
        var metric = new FaithfulnessMetric(model);

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals(0.0, result.score()),
                () -> assertEquals("The claim contradicts the retrieval context.", result.reason()),
                () -> assertEquals(List.of("Refunds last 30 days."), metric.truths()),
                () -> assertEquals(List.of("Refunds last 60 days."), metric.claims()),
                () -> assertEquals(1, metric.verdicts().size()),
                () -> assertTrue(model.prompts().get(0).contains("context")),
                () -> assertTrue(model.prompts().get(0).contains("Example Text")),
                () -> assertTrue(model.prompts().get(0).contains("\"truths\"")),
                () -> assertTrue(model.prompts().get(1).contains("output")),
                () -> assertTrue(model.prompts().get(1).contains("Example Text")),
                () -> assertTrue(model.prompts().get(1).contains("\"claims\"")),
                () -> assertTrue(model.prompts().get(2).contains("Refunds last 30 days.")),
                () -> assertTrue(model.prompts().get(3).contains("The output says 60 days")),
                () -> assertTrue(model.prompts().get(3).contains("If there are no contradictions")),
                () -> assertTrue(model.prompts().get(3).contains("Your reason MUST use information in `contradiction`")));
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

    private static LlmTestCase testCase() {
        return LlmTestCase.builder("input")
                .actualOutput("output")
                .retrievalContext(List.of("context"))
                .build();
    }

    private static final class StubFaithfulnessMetric extends FaithfulnessMetric {
        private final List<FaithfulnessVerdict> verdicts;

        StubFaithfulnessMetric(List<FaithfulnessVerdict> verdicts) {
            this(verdicts, false, false);
        }

        StubFaithfulnessMetric(List<FaithfulnessVerdict> verdicts, boolean strictMode, boolean penalizeAmbiguousClaims) {
            super(0.5, true, strictMode, penalizeAmbiguousClaims);
            this.verdicts = verdicts;
        }

        @Override
        protected List<String> generateTruths(List<String> retrievalContext, boolean multimodal) {
            return List.of("truth");
        }

        @Override
        protected List<String> generateClaims(String actualOutput, boolean multimodal) {
            return List.of("claim");
        }

        @Override
        protected List<FaithfulnessVerdict> generateVerdicts(boolean multimodal) {
            return verdicts;
        }

        @Override
        protected String generateReason(boolean multimodal) {
            return "reason";
        }
    }
}
