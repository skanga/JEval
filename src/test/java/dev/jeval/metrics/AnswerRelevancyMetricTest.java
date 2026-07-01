package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.EvaluationModel;
import dev.jeval.LlmTestCase;
import dev.jeval.MissingTestCaseParamsException;
import dev.jeval.metrics.AnswerRelevancySchemas.AnswerRelevancyVerdict;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnswerRelevancyMetricTest {

    @Test
    void measureScoresNonNoVerdictsAsRelevant() {
        var metric = new StubAnswerRelevancyMetric(List.of(
                new AnswerRelevancyVerdict("yes", "answered"),
                new AnswerRelevancyVerdict("idk", "not contradicted"),
                new AnswerRelevancyVerdict("no", "unrelated")));

        var result = metric.measure(new LlmTestCase("What is the return policy?", "Returns are allowed.", null));

        assertAll(
                () -> assertEquals("Answer Relevancy", result.name()),
                () -> assertEquals(2.0 / 3.0, result.score()),
                () -> assertTrue(result.success()),
                () -> assertEquals(2.0 / 3.0, metric.score()),
                () -> assertEquals("reason", metric.reason()));
    }

    @Test
    void measureScoresOneWhenThereAreNoVerdicts() {
        var result = new StubAnswerRelevancyMetric(List.of())
                .measure(new LlmTestCase("Question?", "Answer.", null));

        assertEquals(1.0, result.score());
    }

    @Test
    void strictModeZerosScoresBelowPerfect() {
        var metric = new StubAnswerRelevancyMetric(
                List.of(new AnswerRelevancyVerdict("yes", null), new AnswerRelevancyVerdict("no", null)),
                true);

        var result = metric.measure(new LlmTestCase("Question?", "Answer.", null));

        assertAll(
                () -> assertEquals(0.0, result.score()),
                () -> assertFalse(result.success()),
                () -> assertEquals(1.0, result.threshold()));
    }

    @Test
    void constructorRejectsNonFiniteThresholds() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new AnswerRelevancyMetric(Double.NaN, true, false)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new AnswerRelevancyMetric(Double.POSITIVE_INFINITY, true, false)));
    }

    @Test
    void measureAllowsEmptyInputLikeDeepEval() {
        var result = new StubAnswerRelevancyMetric(List.of())
                .measure(new LlmTestCase("", "Answer.", null));

        assertEquals(1.0, result.score());
    }

    @Test
    void measureRequiresActualOutput() {
        assertThrows(MissingTestCaseParamsException.class,
                () -> new StubAnswerRelevancyMetric(List.of()).measure(new LlmTestCase("Question?", "", null)));
    }

    @Test
    void measureUsesModelPromptsAndParsesJsonResponses() {
        var model = new ScriptedModel(List.of(
                "{\"statements\": [\"Returns are allowed.\", \"The lobby is blue.\"]}",
                "{\"verdicts\": [{\"verdict\": \"yes\"}, {\"verdict\": \"no\", \"reason\": \"Lobby color is unrelated.\"}]}",
                "{\"reason\": \"One statement is unrelated.\"}"));
        var metric = new AnswerRelevancyMetric(model);

        var result = metric.measure(new LlmTestCase("What is the return policy?", "Returns are allowed. The lobby is blue.", null));

        assertAll(
                () -> assertEquals(0.5, result.score()),
                () -> assertEquals("One statement is unrelated.", result.reason()),
                () -> assertEquals(List.of("Returns are allowed.", "The lobby is blue."), metric.statements()),
                () -> assertEquals(2, metric.verdicts().size()),
                () -> assertTrue(model.prompts().get(0).contains("Returns are allowed. The lobby is blue.")),
                () -> assertTrue(model.prompts().get(1).contains("What is the return policy?")),
                () -> assertTrue(model.prompts().get(2).contains("Lobby color is unrelated.")));
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

    private static final class StubAnswerRelevancyMetric extends AnswerRelevancyMetric {
        private final List<AnswerRelevancyVerdict> verdicts;

        StubAnswerRelevancyMetric(List<AnswerRelevancyVerdict> verdicts) {
            this(verdicts, false);
        }

        StubAnswerRelevancyMetric(List<AnswerRelevancyVerdict> verdicts, boolean strictMode) {
            super(0.5, true, strictMode);
            this.verdicts = verdicts;
        }

        @Override
        protected List<String> generateStatements(String actualOutput, boolean multimodal) {
            return List.of(actualOutput);
        }

        @Override
        protected List<AnswerRelevancyVerdict> generateVerdicts(String input, boolean multimodal) {
            return verdicts;
        }

        @Override
        protected String generateReason(String input, boolean multimodal) {
            return "reason";
        }
    }
}
