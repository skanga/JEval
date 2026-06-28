package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.EvaluationModel;
import dev.jeval.LlmTestCase;
import dev.jeval.MissingTestCaseParamsException;
import dev.jeval.metrics.ToxicitySchemas.ToxicityVerdict;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToxicityMetricTest {

    @Test
    void measureScoresYesVerdictsAsToxic() {
        var metric = new StubToxicityMetric(List.of("opinion"), List.of(
                new ToxicityVerdict("yes", "Toxic."),
                new ToxicityVerdict("no", null),
                new ToxicityVerdict("yes", "Also toxic.")));

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals("Toxicity", result.name()),
                () -> assertEquals(2.0 / 3.0, result.score()),
                () -> assertFalse(result.success()),
                () -> assertEquals("reason", result.reason()),
                () -> assertEquals(List.of("opinion"), metric.opinions()),
                () -> assertEquals(3, metric.verdicts().size()));
    }

    @Test
    void measureScoresZeroWhenThereAreNoVerdicts() {
        var result = new StubToxicityMetric(List.of(), List.of()).measure(testCase());

        assertEquals(0.0, result.score());
    }

    @Test
    void strictModeSetsScoreToOneWhenAnyToxicityExists() {
        var metric = new StubToxicityMetric(List.of("opinion"), List.of(
                new ToxicityVerdict("yes", "Toxic.")),
                true);

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals(1.0, result.score()),
                () -> assertFalse(result.success()),
                () -> assertEquals(0.0, result.threshold()));
    }

    @Test
    void measureAllowsEmptyInputLikeDeepEval() {
        var metric = new StubToxicityMetric(List.of(), List.of());

        var result = metric.measure(LlmTestCase.builder("").actualOutput("output").build());

        assertEquals(0.0, result.score());
    }

    @Test
    void measureRequiresActualOutput() {
        var metric = new StubToxicityMetric(List.of(), List.of());

        assertThrows(MissingTestCaseParamsException.class,
                () -> metric.measure(LlmTestCase.builder("input").actualOutput("").build()));
    }

    @Test
    void measureUsesModelPromptsAndParsesJsonResponses() {
        var model = new ScriptedModel(List.of(
                "{\"opinions\": [\"That was insulting.\"]}",
                "{\"verdicts\": [{\"verdict\": \"yes\", \"reason\": \"Insulting phrase.\"}]}",
                "{\"reason\": \"The output contains an insult.\"}"));
        var metric = new ToxicityMetric(model);

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals(1.0, result.score()),
                () -> assertEquals("The output contains an insult.", result.reason()),
                () -> assertEquals(List.of("That was insulting."), metric.opinions()),
                () -> assertEquals(1, metric.verdicts().size()),
                () -> assertTrue(model.prompts().get(0).contains("Cited opinions are OKAY")),
                () -> assertTrue(model.prompts().get(0).contains("Wrong facts")),
                () -> assertTrue(model.prompts().get(0).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(0).contains("output")),
                () -> assertTrue(model.prompts().get(1).contains("RUBRIC FOR TOXICITY")),
                () -> assertTrue(model.prompts().get(1).contains("Personal Attacks")),
                () -> assertTrue(model.prompts().get(1).contains("Only provide a reason if the verdict is \"yes\"")),
                () -> assertTrue(model.prompts().get(1).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(1).contains("That was insulting.")),
                () -> assertTrue(model.prompts().get(2).contains("Always use cited phrases")),
                () -> assertTrue(model.prompts().get(2).contains("offer some praise")),
                () -> assertTrue(model.prompts().get(2).contains("Insulting phrase.")));
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

    private static final class StubToxicityMetric extends ToxicityMetric {
        private final List<String> opinions;
        private final List<ToxicityVerdict> verdicts;

        StubToxicityMetric(List<String> opinions, List<ToxicityVerdict> verdicts) {
            this(opinions, verdicts, false);
        }

        StubToxicityMetric(List<String> opinions, List<ToxicityVerdict> verdicts, boolean strictMode) {
            super(0.5, true, strictMode);
            this.opinions = opinions;
            this.verdicts = verdicts;
        }

        @Override
        protected List<String> generateOpinions(String actualOutput) {
            return opinions;
        }

        @Override
        protected List<ToxicityVerdict> generateVerdicts() {
            return verdicts;
        }

        @Override
        protected String generateReason() {
            return "reason";
        }
    }
}
