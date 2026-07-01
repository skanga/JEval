package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.EvaluationModel;
import dev.jeval.LlmTestCase;
import dev.jeval.MissingTestCaseParamsException;
import dev.jeval.metrics.HallucinationSchemas.HallucinationVerdict;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class HallucinationMetricTest {

    @Test
    void measureScoresNoVerdictsAsHallucinations() {
        var metric = new StubHallucinationMetric(List.of(
                new HallucinationVerdict("yes", "Agrees."),
                new HallucinationVerdict("no", "Contradicts."),
                new HallucinationVerdict("no", "Contradicts again.")));

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals("Hallucination", result.name()),
                () -> assertEquals(2.0 / 3.0, result.score()),
                () -> assertFalse(result.success()),
                () -> assertEquals("reason", result.reason()),
                () -> assertEquals(3, metric.verdicts().size()));
    }

    @Test
    void measureScoresZeroWhenThereAreNoVerdicts() {
        var result = new StubHallucinationMetric(List.of()).measure(testCase());

        assertEquals(0.0, result.score());
    }

    @Test
    void strictModeSetsScoreToOneWhenAnyHallucinationExists() {
        var metric = new StubHallucinationMetric(List.of(
                new HallucinationVerdict("yes", "Agrees."),
                new HallucinationVerdict("no", "Contradicts.")),
                true);

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals(1.0, result.score()),
                () -> assertFalse(result.success()),
                () -> assertEquals(0.0, result.threshold()));
    }

    @Test
    void constructorRejectsNonFiniteThresholds() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new HallucinationMetric(Double.NaN, true, false)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new HallucinationMetric(Double.POSITIVE_INFINITY, true, false)));
    }

    @Test
    void measureAllowsEmptyInputLikeDeepEval() {
        var result = new StubHallucinationMetric(List.of())
                .measure(LlmTestCase.builder("")
                        .actualOutput("output")
                        .context(List.of("ctx"))
                        .build());

        assertEquals(0.0, result.score());
    }

    @Test
    void measureRequiresActualOutputAndContext() {
        var metric = new StubHallucinationMetric(List.of());

        assertThrows(MissingTestCaseParamsException.class,
                () -> metric.measure(LlmTestCase.builder("input")
                        .actualOutput("")
                        .context(List.of("ctx"))
                        .build()));
        assertThrows(MissingTestCaseParamsException.class,
                () -> metric.measure(LlmTestCase.builder("input")
                        .actualOutput("output")
                        .build()));
    }

    @Test
    void measureUsesModelPromptsAndParsesJsonResponses() {
        var model = new ScriptedModel(List.of(
                "{\"verdicts\": [{\"verdict\": \"yes\", \"reason\": \"Agrees.\"},"
                        + "{\"verdict\": \"no\", \"reason\": \"Contradicts.\"}]}",
                "{\"reason\": \"One context is contradicted.\"}"));
        var metric = new HallucinationMetric(model);

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals(0.5, result.score()),
                () -> assertEquals("One context is contradicted.", result.reason()),
                () -> assertEquals(2, metric.verdicts().size()),
                () -> assertTrue(model.prompts().get(0).contains("Example contexts")),
                () -> assertTrue(model.prompts().get(0).contains("photoelectric effect")),
                () -> assertTrue(model.prompts().get(0).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(0).contains("output")),
                () -> assertTrue(model.prompts().get(0).contains("context one")),
                () -> assertTrue(model.prompts().get(1).contains("`actual output` and `contexts")),
                () -> assertTrue(model.prompts().get(1).contains("CONCISELY")),
                () -> assertTrue(model.prompts().get(1).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(1).contains("Contradicts.")));
    }

    private static LlmTestCase testCase() {
        return LlmTestCase.builder("input")
                .actualOutput("output")
                .context(List.of("context one", "context two"))
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

    private static final class StubHallucinationMetric extends HallucinationMetric {
        private final List<HallucinationVerdict> verdicts;

        StubHallucinationMetric(List<HallucinationVerdict> verdicts) {
            this(verdicts, false);
        }

        StubHallucinationMetric(List<HallucinationVerdict> verdicts, boolean strictMode) {
            super(0.5, true, strictMode);
            this.verdicts = verdicts;
        }

        @Override
        protected List<HallucinationVerdict> generateVerdicts(String actualOutput, List<String> context) {
            return verdicts;
        }

        @Override
        protected String generateReason() {
            return "reason";
        }
    }
}
