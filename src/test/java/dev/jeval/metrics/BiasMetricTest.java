package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.EvaluationModel;
import dev.jeval.LlmTestCase;
import dev.jeval.MissingTestCaseParamsException;
import dev.jeval.metrics.BiasSchemas.BiasVerdict;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BiasMetricTest {

    @Test
    void measureScoresYesVerdictsAsBiased() {
        var metric = new StubBiasMetric(List.of("opinion"), List.of(
                new BiasVerdict("yes", "Biased."),
                new BiasVerdict("no", null),
                new BiasVerdict("yes", "Also biased.")));

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals("Bias", result.name()),
                () -> assertEquals(2.0 / 3.0, result.score()),
                () -> assertFalse(result.success()),
                () -> assertEquals("reason", result.reason()),
                () -> assertEquals(List.of("opinion"), metric.opinions()),
                () -> assertEquals(3, metric.verdicts().size()));
    }

    @Test
    void measureScoresZeroWhenThereAreNoVerdicts() {
        var result = new StubBiasMetric(List.of(), List.of()).measure(testCase());

        assertEquals(0.0, result.score());
    }

    @Test
    void strictModeSetsScoreToOneWhenAnyBiasExists() {
        var metric = new StubBiasMetric(List.of("opinion"), List.of(
                new BiasVerdict("yes", "Biased.")),
                true);

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals(1.0, result.score()),
                () -> assertFalse(result.success()),
                () -> assertEquals(0.0, result.threshold()));
    }

    @Test
    void measureAllowsEmptyInputLikeDeepEval() {
        var metric = new StubBiasMetric(List.of(), List.of());

        var result = metric.measure(LlmTestCase.builder("").actualOutput("output").build());

        assertEquals(0.0, result.score());
    }

    @Test
    void measureRequiresActualOutput() {
        var metric = new StubBiasMetric(List.of(), List.of());

        assertThrows(MissingTestCaseParamsException.class,
                () -> metric.measure(LlmTestCase.builder("input").actualOutput("").build()));
    }

    @Test
    void measureUsesModelPromptsAndParsesJsonResponses() {
        var model = new ScriptedModel(List.of(
                "{\"opinions\": [\"That statement is loaded.\"]}",
                "{\"verdicts\": [{\"verdict\": \"yes\", \"reason\": \"Political framing.\"}]}",
                "{\"reason\": \"The output contains biased framing.\"}"));
        var metric = new BiasMetric(model);

        var result = metric.measure(LlmTestCase.builder("input")
                .actualOutput("output")
                .multimodal(true)
                .build());

        assertAll(
                () -> assertEquals(1.0, result.score()),
                () -> assertEquals("The output contains biased framing.", result.reason()),
                () -> assertEquals(List.of("That statement is loaded."), metric.opinions()),
                () -> assertEquals(1, metric.verdicts().size()),
                () -> assertTrue(model.prompts().get(0).contains("Cited opinions are OKAY")),
                () -> assertTrue(model.prompts().get(0).contains("Wrong facts")),
                () -> assertTrue(model.prompts().get(0).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(0).contains("output")),
                () -> assertTrue(model.prompts().get(1).contains("RUBRIC FOR BIAS")),
                () -> assertTrue(model.prompts().get(1).contains("Gender Bias")),
                () -> assertTrue(model.prompts().get(1).contains("Only provide a reason if the verdict is \"yes\"")),
                () -> assertTrue(model.prompts().get(1).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(1).contains("That statement is loaded.")),
                () -> assertTrue(model.prompts().get(2).contains("Always use cited phrases")),
                () -> assertTrue(model.prompts().get(2).contains("offer some praise")),
                () -> assertTrue(model.prompts().get(2).contains("Political framing.")));
    }

    private static LlmTestCase testCase() {
        return LlmTestCase.builder("input")
                .actualOutput("output")
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

    private static final class StubBiasMetric extends BiasMetric {
        private final List<String> opinions;
        private final List<BiasVerdict> verdicts;

        StubBiasMetric(List<String> opinions, List<BiasVerdict> verdicts) {
            this(opinions, verdicts, false);
        }

        StubBiasMetric(List<String> opinions, List<BiasVerdict> verdicts, boolean strictMode) {
            super(0.5, true, strictMode);
            this.opinions = opinions;
            this.verdicts = verdicts;
        }

        @Override
        protected List<String> generateOpinions(String actualOutput, boolean multimodal) {
            return opinions;
        }

        @Override
        protected List<BiasVerdict> generateVerdicts(boolean multimodal) {
            return verdicts;
        }

        @Override
        protected String generateReason() {
            return "reason";
        }
    }
}
