package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.EvaluationModel;
import dev.jeval.LlmTestCase;
import dev.jeval.MissingTestCaseParamsException;
import dev.jeval.metrics.NonAdviceSchemas.NonAdviceVerdict;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NonAdviceMetricTest {

    @Test
    void constructorRequiresAdviceTypes() {
        assertThrows(IllegalArgumentException.class, () -> new NonAdviceMetric(List.of()));
    }

    @Test
    void measureScoresNoVerdictsAsAppropriateAdvice() {
        var metric = new StubNonAdviceMetric(List.of("advice"), List.of(
                new NonAdviceVerdict("yes", "Investment advice."),
                new NonAdviceVerdict("no", "Educational."),
                new NonAdviceVerdict("no", "General.")));

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals("Non-Advice", result.name()),
                () -> assertEquals(2.0 / 3.0, result.score()),
                () -> assertTrue(result.success()),
                () -> assertEquals("reason", result.reason()),
                () -> assertEquals(List.of("advice"), metric.advices()),
                () -> assertEquals(3, metric.verdicts().size()));
    }

    @Test
    void noAdviceStatementsReturnPerfectScore() {
        var result = new StubNonAdviceMetric(List.of(), List.of()).measure(testCase());

        assertEquals(1.0, result.score());
    }

    @Test
    void strictModeZerosScoresBelowThreshold() {
        var metric = new StubNonAdviceMetric(List.of("advice"), List.of(
                new NonAdviceVerdict("yes", "Investment advice."),
                new NonAdviceVerdict("no", "Educational.")),
                true);

        var result = metric.measure(LlmTestCase.builder("input")
                .actualOutput("output")
                .multimodal(true)
                .build());

        assertAll(
                () -> assertEquals(0.0, result.score()),
                () -> assertFalse(result.success()),
                () -> assertEquals(1.0, result.threshold()));
    }

    @Test
    void constructorRejectsNonFiniteThresholds() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new NonAdviceMetric(List.of("financial"), Double.NaN, true, false)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new NonAdviceMetric(List.of("financial"), Double.POSITIVE_INFINITY, true, false)));
    }

    @Test
    void measureAllowsEmptyInputLikeDeepEval() {
        var metric = new StubNonAdviceMetric(List.of(), List.of());

        var result = metric.measure(LlmTestCase.builder("").actualOutput("output").build());

        assertEquals(1.0, result.score());
    }

    @Test
    void measureRequiresActualOutput() {
        var metric = new StubNonAdviceMetric(List.of(), List.of());

        assertThrows(MissingTestCaseParamsException.class,
                () -> metric.measure(LlmTestCase.builder("input").actualOutput("").build()));
    }

    @Test
    void measureUsesModelPromptsAndParsesJsonResponses() {
        var model = new ScriptedModel(List.of(
                "{\"advices\":[\"Buy this stock today.\"]}",
                "{\"verdicts\":[{\"verdict\":\"yes\",\"reason\":\"Specific investment advice.\"}]}",
                "{\"reason\":\"The output gives investment advice.\"}"));
        var metric = new NonAdviceMetric(model, List.of("financial"));

        var result = metric.measure(LlmTestCase.builder("input")
                .actualOutput("output")
                .multimodal(true)
                .build());

        assertAll(
                () -> assertEquals(0.0, result.score()),
                () -> assertEquals("The output gives investment advice.", result.reason()),
                () -> assertEquals(List.of("Buy this stock today."), metric.advices()),
                () -> assertEquals(1, metric.verdicts().size()),
                () -> assertTrue(model.prompts().get(0).contains("financial")),
                () -> assertTrue(model.prompts().get(0).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(0).contains("Direct recommendations or suggestions")),
                () -> assertTrue(model.prompts().get(0).contains("General educational information")),
                () -> assertTrue(model.prompts().get(0).contains("with the 'advices' key")),
                () -> assertTrue(model.prompts().get(0).contains("Example JSON")),
                () -> assertTrue(model.prompts().get(0).contains(
                        "\"advices\": [\"Statement 1\", \"Statement 2\", ...]")),
                () -> assertTrue(model.prompts().get(0).contains("output")),
                () -> assertTrue(model.prompts().get(1).contains("Financial advice")),
                () -> assertTrue(model.prompts().get(1).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(1).contains("Professional consultation")),
                () -> assertTrue(model.prompts().get(1).contains("with the 'verdicts' key")),
                () -> assertTrue(model.prompts().get(1).contains("Example JSON")),
                () -> assertTrue(model.prompts().get(1).contains("Buy this stock today.")),
                () -> assertTrue(model.prompts().get(2).contains("specific advice violations found")),
                () -> assertTrue(model.prompts().get(2).contains("non-advice score")),
                () -> assertTrue(model.prompts().get(2).contains("with the 'reason' key")),
                () -> assertTrue(model.prompts().get(2).contains("<non_advice_score>")),
                () -> assertTrue(model.prompts().get(2).contains("Specific investment advice.")));
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

    private static final class StubNonAdviceMetric extends NonAdviceMetric {
        private final List<String> advices;
        private final List<NonAdviceVerdict> verdicts;

        StubNonAdviceMetric(List<String> advices, List<NonAdviceVerdict> verdicts) {
            this(advices, verdicts, false);
        }

        StubNonAdviceMetric(List<String> advices, List<NonAdviceVerdict> verdicts, boolean strictMode) {
            super(List.of("financial"), 0.5, true, strictMode);
            this.advices = advices;
            this.verdicts = verdicts;
        }

        @Override
        protected List<String> generateAdvices(String actualOutput, boolean multimodal) {
            return advices;
        }

        @Override
        protected List<NonAdviceVerdict> generateVerdicts(boolean multimodal) {
            return verdicts;
        }

        @Override
        protected String generateReason() {
            return "reason";
        }
    }
}
