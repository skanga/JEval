package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.EvaluationModel;
import dev.jeval.LlmTestCase;
import dev.jeval.MissingTestCaseParamsException;
import dev.jeval.metrics.PlanAdherenceSchemas.PlanAdherenceScore;
import dev.jeval.metrics.PlanQualitySchemas.AgentPlan;
import dev.jeval.metrics.StepEfficiencySchemas.Task;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlanAdherenceMetricTest {

    @Test
    void measureExtractsTaskPlanAndScoresTraceAdherence() {
        var model = new ScriptedModel(List.of(
                "{\"task\":\"Book a flight\"}",
                "{\"plan\":[\"Search flights\",\"Book selected flight\"]}",
                "{\"score\":0.75,\"reason\":\"The trace includes an extra search.\"}"));
        var metric = new PlanAdherenceMetric(model);

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals("Plan Adherence", result.name()),
                () -> assertEquals(0.75, result.score()),
                () -> assertTrue(result.success()),
                () -> assertEquals("The trace includes an extra search.", result.reason()),
                () -> assertEquals("Book a flight", metric.task()),
                () -> assertEquals(List.of("Search flights", "Book selected flight"), metric.plan()),
                () -> assertTrue(model.prompts().get(0).contains("\"input\":\"Book a flight\"")),
                () -> assertTrue(model.prompts().get(0).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(1).contains("\"name\":\"root\"")),
                () -> assertTrue(model.prompts().get(1).contains("systems analyst")),
                () -> assertTrue(model.prompts().get(1).contains("Every plan step you include")),
                () -> assertTrue(model.prompts().get(1).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(1).contains("Return a JSON object with exactly this structure")),
                () -> assertTrue(model.prompts().get(1).contains("\"plan\": [")),
                () -> assertTrue(model.prompts().get(1).contains("\"plan\": []")),
                () -> assertTrue(model.prompts().get(1).contains("Do not include commentary")),
                () -> assertTrue(model.prompts().get(2).contains("adversarial plan adherence evaluator")),
                () -> assertTrue(model.prompts().get(2).contains("non-adherence by default")),
                () -> assertTrue(model.prompts().get(2).contains("as written")),
                () -> assertTrue(model.prompts().get(2).contains("STRICT ADHERENCE RULES")),
                () -> assertTrue(model.prompts().get(2).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(2).contains("This should be the highest score possible when there are any deviations")),
                () -> assertTrue(model.prompts().get(2).contains("Return a JSON object with exactly this structure")),
                () -> assertTrue(model.prompts().get(2).contains("\"score\": 0.0")),
                () -> assertTrue(model.prompts().get(2).contains("1-3 concise, factual sentences")),
                () -> assertTrue(model.prompts().get(2).contains("Requirements for \"reason\"")),
                () -> assertTrue(model.prompts().get(2).contains("Reference specific plan step numbers")),
                () -> assertTrue(model.prompts().get(2).contains("SCORING SCALE")),
                () -> assertTrue(model.prompts().get(2).contains("Book a flight")),
                () -> assertTrue(model.prompts().get(2).contains("Search flights")),
                () -> assertTrue(model.prompts().get(2).contains("\"name\":\"root\"")));
    }

    @Test
    void emptyPlanScoresOneWithoutCallingAdherenceJudge() {
        var model = new ScriptedModel(List.of(
                "{\"task\":\"Book a flight\"}",
                "{\"plan\":[]}"));
        var metric = new PlanAdherenceMetric(model);

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals(1.0, result.score()),
                () -> assertTrue(result.success()),
                () -> assertEquals(2, model.prompts().size()),
                () -> assertTrue(result.reason().contains("no plans to evaluate")));
    }

    @Test
    void strictModeZerosScoresBelowThreshold() {
        var metric = new StubPlanAdherenceMetric(
                new Task("Book a flight"),
                new AgentPlan(List.of("Search flights")),
                new PlanAdherenceScore(0.5, "Extra step."),
                true);

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals(0.0, result.score()),
                () -> assertFalse(result.success()),
                () -> assertEquals(1.0, result.threshold()));
    }

    @Test
    void measureAllowsEmptyInputLikeDeepEval() {
        var metric = new StubPlanAdherenceMetric(
                new Task("Task"),
                new AgentPlan(List.of("Do it")),
                new PlanAdherenceScore(1.0, "Followed."),
                false);

        var result = metric.measure(LlmTestCase.builder("")
                .actualOutput("output")
                .trace(Map.of("name", "root"))
                .build());

        assertEquals(1.0, result.score());
    }

    @Test
    void measureRequiresActualOutputAndTrace() {
        var metric = new StubPlanAdherenceMetric(
                new Task("Task"),
                new AgentPlan(List.of("Do it")),
                new PlanAdherenceScore(1.0, "Followed."),
                false);

        assertThrows(MissingTestCaseParamsException.class,
                () -> metric.measure(LlmTestCase.builder("input")
                        .actualOutput("")
                        .trace(Map.of("name", "root"))
                        .build()));
        assertThrows(MissingTestCaseParamsException.class,
                () -> metric.measure(LlmTestCase.builder("input").actualOutput("output").build()));
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

    private static final class StubPlanAdherenceMetric extends PlanAdherenceMetric {
        private final Task task;
        private final AgentPlan plan;
        private final PlanAdherenceScore score;

        StubPlanAdherenceMetric(Task task, AgentPlan plan, PlanAdherenceScore score, boolean strictMode) {
            super(0.5, true, strictMode);
            this.task = task;
            this.plan = plan;
            this.score = score;
        }

        @Override
        protected Task extractTask(Map<String, Object> trace) {
            return task;
        }

        @Override
        protected AgentPlan extractPlan(Map<String, Object> trace) {
            return plan;
        }

        @Override
        protected PlanAdherenceScore generateScore(String task, List<String> plan, Map<String, Object> trace) {
            return score;
        }
    }
}
