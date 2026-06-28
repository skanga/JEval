package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.EvaluationModel;
import dev.jeval.LlmTestCase;
import dev.jeval.MissingTestCaseParamsException;
import dev.jeval.metrics.ContextualRelevancySchemas.ContextualRelevancyVerdict;
import dev.jeval.metrics.ContextualRelevancySchemas.ContextualRelevancyVerdicts;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContextualRelevancyMetricTest {

    @Test
    void measureScoresOnlyYesVerdictsAsRelevant() {
        var metric = new StubContextualRelevancyMetric(List.of(
                new ContextualRelevancyVerdicts(List.of(
                        new ContextualRelevancyVerdict("Refunds last 30 days.", "yes", null),
                        new ContextualRelevancyVerdict("The lobby is blue.", "no", "Unrelated."))),
                new ContextualRelevancyVerdicts(List.of(
                        new ContextualRelevancyVerdict("Support is available.", "idk", "Ambiguous.")))));

        var result = metric.measure(testCase(true));

        assertAll(
                () -> assertEquals("Contextual Relevancy", result.name()),
                () -> assertEquals(1.0 / 3.0, result.score()),
                () -> assertFalse(result.success()),
                () -> assertEquals("reason", result.reason()),
                () -> assertEquals(2, metric.verdictsList().size()));
    }

    @Test
    void measureScoresZeroWhenThereAreNoVerdicts() {
        var result = new StubContextualRelevancyMetric(List.of()).measure(testCase());

        assertEquals(0.0, result.score());
    }

    @Test
    void strictModeZerosScoresBelowPerfect() {
        var metric = new StubContextualRelevancyMetric(List.of(new ContextualRelevancyVerdicts(List.of(
                new ContextualRelevancyVerdict("Refunds last 30 days.", "yes", null),
                new ContextualRelevancyVerdict("The lobby is blue.", "no", "Unrelated.")))),
                true);

        var result = metric.measure(testCase(true));

        assertAll(
                () -> assertEquals(0.0, result.score()),
                () -> assertFalse(result.success()),
                () -> assertEquals(1.0, result.threshold()));
    }

    @Test
    void measureAllowsEmptyInputLikeDeepEval() {
        var result = new StubContextualRelevancyMetric(List.of())
                .measure(LlmTestCase.builder("").retrievalContext(List.of("ctx")).build());

        assertEquals(0.0, result.score());
    }

    @Test
    void measureRequiresRetrievalContext() {
        var metric = new StubContextualRelevancyMetric(List.of());

        assertThrows(MissingTestCaseParamsException.class,
                () -> metric.measure(LlmTestCase.builder("input").build()));
    }

    @Test
    void measureUsesModelPromptsAndParsesJsonResponses() {
        var model = new ScriptedModel(List.of(
                "{\"verdicts\": [{\"statement\": \"Refunds last 30 days.\", \"verdict\": \"yes\"}]}",
                "{\"verdicts\": [{\"statement\": \"The lobby is blue.\", \"verdict\": \"no\", \"reason\": \"Lobby color is unrelated.\"}]}",
                "{\"reason\": \"One context is irrelevant.\"}"));
        var metric = new ContextualRelevancyMetric(model);

        var result = metric.measure(testCase(true));

        assertAll(
                () -> assertEquals(0.5, result.score()),
                () -> assertEquals("One context is irrelevant.", result.reason()),
                () -> assertEquals(2, metric.verdictsList().size()),
                () -> assertTrue(model.prompts().get(0).contains("context one")),
                () -> assertTrue(model.prompts().get(0).contains("context (image or string)")),
                () -> assertTrue(model.prompts().get(1).contains("context two")),
                () -> assertTrue(model.prompts().get(2).contains("Lobby color is unrelated.")),
                () -> assertTrue(model.prompts().get(2).contains("--- MULTIMODAL INPUT RULES ---")));
    }

    private static LlmTestCase testCase() {
        return testCase(false);
    }

    private static LlmTestCase testCase(boolean multimodal) {
        return LlmTestCase.builder("input")
                .retrievalContext(List.of("context one", "context two"))
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

    private static final class StubContextualRelevancyMetric extends ContextualRelevancyMetric {
        private final List<ContextualRelevancyVerdicts> verdicts;
        private int index;

        StubContextualRelevancyMetric(List<ContextualRelevancyVerdicts> verdicts) {
            this(verdicts, false);
        }

        StubContextualRelevancyMetric(List<ContextualRelevancyVerdicts> verdicts, boolean strictMode) {
            super(0.5, true, strictMode);
            this.verdicts = verdicts;
        }

        @Override
        protected ContextualRelevancyVerdicts generateVerdicts(String input, String context, boolean multimodal) {
            if (verdicts.isEmpty()) {
                return new ContextualRelevancyVerdicts(List.of());
            }
            return verdicts.get(Math.min(index++, verdicts.size() - 1));
        }

        @Override
        protected String generateReason(String input, boolean multimodal) {
            return "reason";
        }
    }
}
