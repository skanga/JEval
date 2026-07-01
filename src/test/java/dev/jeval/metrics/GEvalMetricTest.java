package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.EvaluationModel;
import dev.jeval.LlmTestCase;
import dev.jeval.MissingTestCaseParamsException;
import dev.jeval.SingleTurnParam;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GEvalMetricTest {

    @Test
    void constructorRejectsMissingEvaluationParamsAndCriteria() {
        assertThrows(IllegalArgumentException.class,
                () -> new GEvalMetric("Correctness", List.of(), "criteria"));
        assertThrows(IllegalArgumentException.class,
                () -> new GEvalMetric("Correctness", List.of(SingleTurnParam.INPUT), null));
    }

    @Test
    void measureGeneratesStepsAndNormalizesScore() {
        var model = new ScriptedModel(List.of(
                "{\"steps\":[\"Check the output answers the input.\"]}",
                "{\"score\":8,\"reason\":\"The answer is mostly correct.\"}"));
        var metric = new GEvalMetric(
                model,
                "Correctness",
                List.of(SingleTurnParam.INPUT, SingleTurnParam.ACTUAL_OUTPUT),
                "Judge correctness.");

        var result = metric.measure(testCase(true));

        assertAll(
                () -> assertEquals("Correctness [GEval]", result.name()),
                () -> assertEquals(0.8, result.score()),
                () -> assertEquals("The answer is mostly correct.", result.reason()),
                () -> assertEquals(List.of("Check the output answers the input."), metric.evaluationSteps()),
                () -> assertTrue(model.prompts().getFirst().contains("Judge correctness.")),
                () -> assertTrue(model.prompts().getFirst().contains("relation to one another")),
                () -> assertTrue(model.prompts().getFirst().contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().getFirst().contains("with the \"steps\" key")),
                () -> assertTrue(model.prompts().getFirst().contains("Example JSON")),
                () -> assertTrue(model.prompts().get(1).contains("Do **not** quote the score itself")),
                () -> assertTrue(model.prompts().get(1).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(1).contains("**Example JSON:**")),
                () -> assertTrue(model.prompts().get(1).contains("input")),
                () -> assertTrue(model.prompts().get(1).contains("output")));
    }

    @Test
    void suppliedStepsSkipStepGeneration() {
        var model = new ScriptedModel(List.of(
                "{\"score\":5,\"reason\":\"Half aligned.\"}"));
        var metric = new GEvalMetric(
                model,
                "Correctness",
                List.of(SingleTurnParam.INPUT),
                null,
                List.of("Check the input."),
                null,
                0.5,
                false);

        var result = metric.measure(testCase(true));

        assertAll(
                () -> assertEquals(0.5, result.score()),
                () -> assertEquals(1, model.prompts().size()),
                () -> assertTrue(model.prompts().getFirst().contains("Check the input.")));
    }

    @Test
    void strictModeUsesBinaryScore() {
        var model = new ScriptedModel(List.of(
                "{\"score\":1,\"reason\":\"Fully follows.\"}"));
        var metric = new GEvalMetric(
                model,
                "Binary",
                List.of(SingleTurnParam.INPUT),
                null,
                List.of("Check binary pass."),
                null,
                0.5,
                true);

        var result = metric.measure(testCase(true));

        assertAll(
                () -> assertEquals(1.0, result.score()),
                () -> assertEquals(1.0, result.threshold()),
                () -> assertTrue(result.success()),
                () -> assertTrue(model.prompts().getFirst().contains("STRICTLY EITHER 1")),
                () -> assertTrue(model.prompts().getFirst().contains("DO NOT QUOTE THE SCORE")),
                () -> assertTrue(model.prompts().getFirst().contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().getFirst().contains("with the \"score\" and \"reason\" key")),
                () -> assertTrue(model.prompts().getFirst().contains("Example JSON")));
    }

    @Test
    void measureRequiresConfiguredTestCaseParams() {
        var metric = new GEvalMetric(
                new ScriptedModel(List.of("{\"score\":1,\"reason\":\"ok\"}")),
                "Correctness",
                List.of(SingleTurnParam.ACTUAL_OUTPUT),
                null,
                List.of("Check output."),
                null,
                0.5,
                false);

        assertThrows(MissingTestCaseParamsException.class,
                () -> metric.measure(LlmTestCase.builder("input").actualOutput("").build()));
    }

    @Test
    void constructorRejectsNonFiniteThresholds() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new GEvalMetric(
                                null,
                                "Correctness",
                                List.of(SingleTurnParam.INPUT),
                                null,
                                List.of("Check input."),
                                null,
                                Double.NaN,
                                false)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new GEvalMetric(
                                null,
                                "Correctness",
                                List.of(SingleTurnParam.INPUT),
                                null,
                                List.of("Check input."),
                                null,
                                Double.POSITIVE_INFINITY,
                                false)));
    }

    private static LlmTestCase testCase() {
        return testCase(false);
    }

    private static LlmTestCase testCase(boolean multimodal) {
        return LlmTestCase.builder("input").actualOutput("output").multimodal(multimodal).build();
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
}
