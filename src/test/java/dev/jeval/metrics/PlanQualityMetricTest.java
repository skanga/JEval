package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.EvaluationModel;
import dev.jeval.LlmTestCase;
import dev.jeval.MissingTestCaseParamsException;
import dev.jeval.metrics.PlanQualitySchemas.AgentPlan;
import dev.jeval.metrics.PlanQualitySchemas.PlanQualityScore;
import dev.jeval.metrics.StepEfficiencySchemas.Task;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlanQualityMetricTest {

    @Test
    void measureExtractsTaskPlanAndScoresQuality() {
        var model = new ScriptedModel(List.of(
                "{\"task\":\"Book a flight\"}",
                "{\"plan\":[\"Search flights\",\"Book selected flight\"]}",
                "{\"score\":0.8,\"reason\":\"Plan omits payment confirmation.\"}"));
        var metric = new PlanQualityMetric(model);

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals("Plan Quality", result.name()),
                () -> assertEquals(0.8, result.score()),
                () -> assertTrue(result.success()),
                () -> assertEquals("Plan omits payment confirmation.", result.reason()),
                () -> assertEquals("Book a flight", metric.task()),
                () -> assertEquals(List.of("Search flights", "Book selected flight"), metric.plan()),
                () -> assertTrue(model.prompts().get(0).contains("\"input\":\"Book a flight\"")),
                () -> assertTrue(model.prompts().get(0).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(1).contains("\"name\":\"root\"")),
                () -> assertTrue(model.prompts().get(1).contains("Return a JSON object with exactly this structure")),
                () -> assertTrue(model.prompts().get(1).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(1).contains("\"plan\": [")),
                () -> assertTrue(model.prompts().get(1).contains("\"plan\": []")),
                () -> assertTrue(model.prompts().get(2).contains("You are a **plan quality evaluator**")),
                () -> assertTrue(model.prompts().get(2).contains("STRICT EVALUATION CRITERIA")),
                () -> assertTrue(model.prompts().get(2).contains("Missing even one critical subtask")),
                () -> assertTrue(model.prompts().get(2).contains("all prerequisite actions")),
                () -> assertTrue(model.prompts().get(2).contains("Every step must have a clear purpose")),
                () -> assertTrue(model.prompts().get(2).contains("If a more direct, simpler")),
                () -> assertTrue(model.prompts().get(2).contains("SCORING SCALE (STRICT)")),
                () -> assertTrue(model.prompts().get(2).contains("When in doubt, assign the lower score")),
                () -> assertTrue(model.prompts().get(2).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(2).contains("Return a JSON object with this exact structure")),
                () -> assertTrue(model.prompts().get(2).contains("\"score\": 0.0")),
                () -> assertTrue(model.prompts().get(2).contains("1-3 short, precise sentences")),
                () -> assertTrue(model.prompts().get(2).contains("Reference specific missing, unclear, or inefficient steps")),
                () -> assertTrue(model.prompts().get(2).contains("Book a flight")),
                () -> assertTrue(model.prompts().get(2).contains("Search flights")));
    }

    @Test
    void emptyPlanScoresOneWithoutCallingQualityJudge() {
        var model = new ScriptedModel(List.of(
                "{\"task\":\"Book a flight\"}",
                "{\"plan\":[]}"));
        var metric = new PlanQualityMetric(model);

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals(1.0, result.score()),
                () -> assertTrue(result.success()),
                () -> assertEquals(2, model.prompts().size()),
                () -> assertTrue(result.reason().contains("no plans to evaluate")));
    }

    @Test
    void strictModeZerosScoresBelowThreshold() {
        var metric = new StubPlanQualityMetric(
                new Task("Book a flight"),
                new AgentPlan(List.of("Search flights")),
                new PlanQualityScore(0.5, "Too vague."),
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
                        () -> new PlanQualityMetric(Double.NaN, true, false)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new PlanQualityMetric(Double.POSITIVE_INFINITY, true, false)));
    }

    @Test
    void measureAllowsEmptyInputLikeDeepEval() {
        var metric = new StubPlanQualityMetric(
                new Task("Task"),
                new AgentPlan(List.of("Do it")),
                new PlanQualityScore(1.0, "Complete."),
                false);

        var result = metric.measure(LlmTestCase.builder("")
                .actualOutput("output")
                .trace(Map.of("name", "root"))
                .build());

        assertEquals(1.0, result.score());
    }

    @Test
    void measureRequiresActualOutputAndTrace() {
        var metric = new StubPlanQualityMetric(
                new Task("Task"),
                new AgentPlan(List.of("Do it")),
                new PlanQualityScore(1.0, "Complete."),
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

    private static final class StubPlanQualityMetric extends PlanQualityMetric {
        private final Task task;
        private final AgentPlan plan;
        private final PlanQualityScore score;

        StubPlanQualityMetric(Task task, AgentPlan plan, PlanQualityScore score, boolean strictMode) {
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
        protected PlanQualityScore generateScore(String task, List<String> plan) {
            return score;
        }
    }
}
