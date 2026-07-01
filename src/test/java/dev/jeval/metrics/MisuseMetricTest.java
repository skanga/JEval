package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.EvaluationModel;
import dev.jeval.LlmTestCase;
import dev.jeval.MissingTestCaseParamsException;
import dev.jeval.metrics.MisuseSchemas.MisuseVerdict;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MisuseMetricTest {

    @Test
    void constructorRequiresDomain() {
        assertThrows(IllegalArgumentException.class, () -> new MisuseMetric(" "));
    }

    @Test
    void measureScoresYesVerdictsAsMisuse() {
        var metric = new StubMisuseMetric(List.of("misuse"), List.of(
                new MisuseVerdict("yes", "Outside scope."),
                new MisuseVerdict("no", null),
                new MisuseVerdict("yes", "Also outside.")));

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals("Misuse", result.name()),
                () -> assertEquals(2.0 / 3.0, result.score()),
                () -> assertFalse(result.success()),
                () -> assertEquals("reason", result.reason()),
                () -> assertEquals("banking", metric.domain()),
                () -> assertEquals(List.of("misuse"), metric.misuses()),
                () -> assertEquals(3, metric.verdicts().size()));
    }

    @Test
    void noMisusesReturnZeroScore() {
        var result = new StubMisuseMetric(List.of(), List.of()).measure(testCase());

        assertEquals(0.0, result.score());
    }

    @Test
    void strictModeSetsScoreToOneWhenAnyMisuseExists() {
        var metric = new StubMisuseMetric(List.of("misuse"), List.of(
                new MisuseVerdict("yes", "Outside scope.")),
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
                        () -> new MisuseMetric("banking", Double.NaN, true, false)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new MisuseMetric("banking", Double.POSITIVE_INFINITY, true, false)));
    }

    @Test
    void measureAllowsEmptyInputLikeDeepEval() {
        var metric = new StubMisuseMetric(List.of(), List.of());

        var result = metric.measure(LlmTestCase.builder("").actualOutput("output").build());

        assertEquals(0.0, result.score());
    }

    @Test
    void measureRequiresActualOutput() {
        var metric = new StubMisuseMetric(List.of(), List.of());

        assertThrows(MissingTestCaseParamsException.class,
                () -> metric.measure(LlmTestCase.builder("input").actualOutput("").build()));
    }

    @Test
    void measureUsesModelPromptsAndParsesJsonResponses() {
        var model = new ScriptedModel(List.of(
                "{\"misuses\":[\"Write a poem.\"]}",
                "{\"verdicts\":[{\"verdict\":\"yes\",\"reason\":\"Creative writing is outside banking.\"}]}",
                "{\"reason\":\"The output contains an off-domain request.\"}"));
        var metric = new MisuseMetric(model, "Banking");

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals(1.0, result.score()),
                () -> assertEquals("The output contains an off-domain request.", result.reason()),
                () -> assertEquals(List.of("Write a poem."), metric.misuses()),
                () -> assertEquals(1, metric.verdicts().size()),
                () -> assertTrue(model.prompts().get(0).contains("banking")),
                () -> assertTrue(model.prompts().get(0).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(0).contains("outside the banking domain scope")),
                () -> assertTrue(model.prompts().get(0).contains("different type of specialist")),
                () -> assertTrue(model.prompts().get(0).contains("with the 'misuses' key")),
                () -> assertTrue(model.prompts().get(0).contains("Example JSON")),
                () -> assertTrue(model.prompts().get(0).contains(
                        "\"misuses\": [\"Statement 1\", \"Statement 2\", ...]")),
                () -> assertTrue(model.prompts().get(0).contains("output")),
                () -> assertTrue(model.prompts().get(1).contains("Off-topic conversations")),
                () -> assertTrue(model.prompts().get(1).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(1).contains("general-purpose AI assistant")),
                () -> assertTrue(model.prompts().get(1).contains("with the 'verdicts' key")),
                () -> assertTrue(model.prompts().get(1).contains("Example JSON")),
                () -> assertTrue(model.prompts().get(1).contains("Write a poem.")),
                () -> assertTrue(model.prompts().get(2).contains("specific misuse violations found")),
                () -> assertTrue(model.prompts().get(2).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(2).contains("misuse score")),
                () -> assertTrue(model.prompts().get(2).contains("with the 'reason' key")),
                () -> assertTrue(model.prompts().get(2).contains("<misuse_score>")),
                () -> assertTrue(model.prompts().get(2).contains("Creative writing is outside banking.")));
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

    private static final class StubMisuseMetric extends MisuseMetric {
        private final List<String> misuses;
        private final List<MisuseVerdict> verdicts;

        StubMisuseMetric(List<String> misuses, List<MisuseVerdict> verdicts) {
            this(misuses, verdicts, false);
        }

        StubMisuseMetric(List<String> misuses, List<MisuseVerdict> verdicts, boolean strictMode) {
            super("banking", 0.5, true, strictMode);
            this.misuses = misuses;
            this.verdicts = verdicts;
        }

        @Override
        protected List<String> generateMisuses(String actualOutput) {
            return misuses;
        }

        @Override
        protected List<MisuseVerdict> generateVerdicts() {
            return verdicts;
        }

        @Override
        protected String generateReason() {
            return "reason";
        }
    }
}
