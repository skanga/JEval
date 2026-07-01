package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.EvaluationModel;
import dev.jeval.LlmTestCase;
import dev.jeval.MissingTestCaseParamsException;
import dev.jeval.metrics.PIILeakageSchemas.PIILeakageVerdict;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PIILeakageMetricTest {

    @Test
    void measureScoresNoVerdictsAsPrivacyPreserving() {
        var metric = new StubPIILeakageMetric(List.of("pii"), List.of(
                new PIILeakageVerdict("yes", "Contains phone."),
                new PIILeakageVerdict("no", "Generic."),
                new PIILeakageVerdict("no", "No PII.")));

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals("PII Leakage", result.name()),
                () -> assertEquals(2.0 / 3.0, result.score()),
                () -> assertTrue(result.success()),
                () -> assertEquals("reason", result.reason()),
                () -> assertEquals(List.of("pii"), metric.extractedPII()),
                () -> assertEquals(3, metric.verdicts().size()));
    }

    @Test
    void noExtractedPiiReturnsPerfectScore() {
        var result = new StubPIILeakageMetric(List.of(), List.of()).measure(testCase());

        assertEquals(1.0, result.score());
    }

    @Test
    void strictModeZerosScoresBelowThreshold() {
        var metric = new StubPIILeakageMetric(List.of("pii"), List.of(
                new PIILeakageVerdict("yes", "Contains phone."),
                new PIILeakageVerdict("no", "Generic.")),
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
                        () -> new PIILeakageMetric(Double.NaN, true, false)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new PIILeakageMetric(Double.POSITIVE_INFINITY, true, false)));
    }

    @Test
    void measureAllowsEmptyInputLikeDeepEval() {
        var metric = new StubPIILeakageMetric(List.of(), List.of());

        var result = metric.measure(LlmTestCase.builder("").actualOutput("output").build());

        assertEquals(1.0, result.score());
    }

    @Test
    void measureRequiresActualOutput() {
        var metric = new StubPIILeakageMetric(List.of(), List.of());

        assertThrows(MissingTestCaseParamsException.class,
                () -> metric.measure(LlmTestCase.builder("input").actualOutput("").build()));
    }

    @Test
    void measureUsesModelPromptsAndParsesJsonResponses() {
        var model = new ScriptedModel(List.of(
                "{\"extracted_pii\":[\"Call Jane at 555-0101.\"]}",
                "{\"verdicts\":[{\"verdict\":\"yes\",\"reason\":\"Contains phone number.\"}]}",
                "{\"reason\":\"The output leaks a phone number.\"}"));
        var metric = new PIILeakageMetric(model);

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals(0.0, result.score()),
                () -> assertEquals("The output leaks a phone number.", result.reason()),
                () -> assertEquals(List.of("Call Jane at 555-0101."), metric.extractedPII()),
                () -> assertEquals(1, metric.verdicts().size()),
                () -> assertTrue(model.prompts().get(0).contains("Personal identifiers")),
                () -> assertTrue(model.prompts().get(0).contains("Return a list of these statements")),
                () -> assertTrue(model.prompts().get(0).contains("with the 'extracted_pii' key")),
                () -> assertTrue(model.prompts().get(0).contains("Example JSON")),
                () -> assertTrue(model.prompts().get(0).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(0).contains("output")),
                () -> assertTrue(model.prompts().get(1).contains("Personally Identifiable Information")),
                () -> assertTrue(model.prompts().get(1).contains("Social security numbers")),
                () -> assertTrue(model.prompts().get(1).contains("with the 'verdicts' key")),
                () -> assertTrue(model.prompts().get(1).contains("Example JSON")),
                () -> assertTrue(model.prompts().get(1).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(1).contains("Call Jane at 555-0101.")),
                () -> assertTrue(model.prompts().get(2).contains("privacy violation score")),
                () -> assertTrue(model.prompts().get(2).contains("specific privacy violations found")),
                () -> assertTrue(model.prompts().get(2).contains("with the 'reason' key")),
                () -> assertTrue(model.prompts().get(2).contains("<privacy_score>")),
                () -> assertTrue(model.prompts().get(2).contains("Contains phone number.")));
    }

    private static LlmTestCase testCase() {
        return LlmTestCase.builder("input").actualOutput("output").build();
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

    private static final class StubPIILeakageMetric extends PIILeakageMetric {
        private final List<String> extractedPII;
        private final List<PIILeakageVerdict> verdicts;

        StubPIILeakageMetric(List<String> extractedPII, List<PIILeakageVerdict> verdicts) {
            this(extractedPII, verdicts, false);
        }

        StubPIILeakageMetric(List<String> extractedPII, List<PIILeakageVerdict> verdicts, boolean strictMode) {
            super(0.5, true, strictMode);
            this.extractedPII = extractedPII;
            this.verdicts = verdicts;
        }

        @Override
        protected List<String> extractPII(String actualOutput, boolean multimodal) {
            return extractedPII;
        }

        @Override
        protected List<PIILeakageVerdict> generateVerdicts() {
            return verdicts;
        }

        @Override
        protected String generateReason() {
            return "reason";
        }
    }
}
