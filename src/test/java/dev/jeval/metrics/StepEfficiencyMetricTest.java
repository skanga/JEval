package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.EvaluationModel;
import dev.jeval.LlmTestCase;
import dev.jeval.MissingTestCaseParamsException;
import dev.jeval.metrics.StepEfficiencySchemas.EfficiencyVerdict;
import dev.jeval.metrics.StepEfficiencySchemas.Task;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StepEfficiencyMetricTest {

    @Test
    void measureExtractsTaskAndScoresTraceEfficiency() {
        var model = new ScriptedModel(List.of(
                "{\"task\":\"Book a flight\"}",
                "{\"score\":0.8,\"reason\":\"One extra search.\"}"));
        var metric = new StepEfficiencyMetric(model);
        var testCase = testCase();

        var result = metric.measure(testCase);

        assertAll(
                () -> assertEquals("Step Efficiency", result.name()),
                () -> assertEquals(0.8, result.score()),
                () -> assertTrue(result.success()),
                () -> assertEquals("One extra search.", result.reason()),
                () -> assertEquals("Book a flight", metric.task()),
                () -> assertTrue(model.prompts().get(0).contains("\"input\":\"Book a flight\"")),
                () -> assertTrue(model.prompts().get(0).contains("trace analyst")),
                () -> assertTrue(model.prompts().get(0).contains("Root-Level User Input")),
                () -> assertTrue(model.prompts().get(0).contains("Return **only** a JSON object")),
                () -> assertTrue(model.prompts().get(0).contains("\"task\": \"<a single clear sentence")),
                () -> assertTrue(model.prompts().get(0).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(0).contains("EXAMPLES")),
                () -> assertTrue(model.prompts().get(0).contains("When uncertain, extract **less rather than more**")),
                () -> assertTrue(model.prompts().get(1).contains("efficiency auditor")),
                () -> assertTrue(model.prompts().get(1).contains("as low as possible")),
                () -> assertTrue(model.prompts().get(1).contains("Return a single JSON object in this exact format")),
                () -> assertTrue(model.prompts().get(1).contains("\"score\": 0.0")),
                () -> assertTrue(model.prompts().get(1).contains("EXAMPLES")),
                () -> assertTrue(model.prompts().get(1).contains("The agent used redundant LLM calls")),
                () -> assertTrue(model.prompts().get(1).contains("doing exactly what's required, nothing more")),
                () -> assertTrue(model.prompts().get(1).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(1).contains("When uncertain, always assign the lower score")),
                () -> assertTrue(model.prompts().get(1).contains("A \"good answer\" can still score **0.0**")),
                () -> assertTrue(model.prompts().get(1).contains("STRICT EVALUATION RULES")),
                () -> assertTrue(model.prompts().get(1).contains("SCORING SCALE (STRICT)")),
                () -> assertTrue(model.prompts().get(1).contains("Book a flight")),
                () -> assertTrue(model.prompts().get(1).contains("\"name\":\"root\"")));
    }

    @Test
    void strictModeZerosScoresBelowThreshold() {
        var metric = new StubStepEfficiencyMetric(
                new Task("Book a flight"),
                new EfficiencyVerdict(0.5, "Redundant step."),
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
                        () -> new StepEfficiencyMetric(Double.NaN, true, false)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new StepEfficiencyMetric(Double.POSITIVE_INFINITY, true, false)));
    }

    @Test
    void measureRequiresTrace() {
        var metric = new StubStepEfficiencyMetric(
                new Task("Task"),
                new EfficiencyVerdict(1.0, "Minimal."),
                false);

        assertThrows(MissingTestCaseParamsException.class,
                () -> metric.measure(LlmTestCase.builder("input").actualOutput("output").build()));
    }

    @Test
    void measureAllowsEmptyInputLikeDeepEval() {
        var metric = new StubStepEfficiencyMetric(
                new Task("Task"),
                new EfficiencyVerdict(1.0, "Minimal."),
                false);

        var result = metric.measure(LlmTestCase.builder("")
                .actualOutput("output")
                .trace(Map.of("name", "root"))
                .build());

        assertEquals(1.0, result.score());
    }

    @Test
    void measureRequiresActualOutput() {
        var metric = new StubStepEfficiencyMetric(
                new Task("Task"),
                new EfficiencyVerdict(1.0, "Minimal."),
                false);

        assertThrows(MissingTestCaseParamsException.class,
                () -> metric.measure(LlmTestCase.builder("input")
                        .actualOutput("")
                        .trace(Map.of("name", "root"))
                        .build()));
    }

    private static LlmTestCase testCase() {
        return LlmTestCase.builder("input")
                .actualOutput("output")
                .trace(Map.of("name", "root", "input", "Book a flight"))
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

    private static final class StubStepEfficiencyMetric extends StepEfficiencyMetric {
        private final Task task;
        private final EfficiencyVerdict verdict;

        StubStepEfficiencyMetric(Task task, EfficiencyVerdict verdict, boolean strictMode) {
            super(0.5, true, strictMode);
            this.task = task;
            this.verdict = verdict;
        }

        @Override
        protected Task extractTask(Map<String, Object> trace) {
            return task;
        }

        @Override
        protected EfficiencyVerdict generateVerdict(String task, Map<String, Object> trace) {
            return verdict;
        }
    }
}
