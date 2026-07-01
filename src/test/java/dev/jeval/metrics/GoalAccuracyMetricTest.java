package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.ConversationalTestCase;
import dev.jeval.EvaluationModel;
import dev.jeval.Turn;
import dev.jeval.metrics.GoalAccuracySchemas.GoalScore;
import dev.jeval.metrics.GoalAccuracySchemas.GoalSteps;
import dev.jeval.metrics.GoalAccuracySchemas.PlanScore;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GoalAccuracyMetricTest {

    @Test
    void measureBuildsGoalStepsScoresGoalAndPlanAndGeneratesReason() {
        var model = new ScriptedModel(List.of(
                "{\"score\":0.75,\"reason\":\"Hotel booked but flight omitted.\"}",
                "{\"score\":0.5,\"reason\":\"Plan did not cover all requested parts.\"}",
                "The goal was partially completed and planning was incomplete."));
        var metric = new GoalAccuracyMetric(model);

        var result = metric.measure(ConversationalTestCase.builder(List.of(
                new Turn("user", "Need a hotel and flight."),
                new Turn("assistant", "I booked the hotel.")))
                .multimodal(true)
                .build());

        assertAll(
                () -> assertEquals("Goal Accuracy", result.name()),
                () -> assertEquals(0.625, result.score()),
                () -> assertTrue(result.success()),
                () -> assertEquals("The goal was partially completed and planning was incomplete.", result.reason()),
                () -> assertEquals(1, metric.goalSteps().size()),
                () -> assertEquals(1, metric.goalScores().size()),
                () -> assertEquals(1, metric.planScores().size()),
                () -> assertTrue(model.prompts().get(0).contains("goal accuracy")),
                () -> assertTrue(model.prompts().get(0).contains("User-visible fulfillment only")),
                () -> assertTrue(model.prompts().get(0).contains("If even one subpart of the task is missing")),
                () -> assertTrue(model.prompts().get(0).contains("When in doubt, choose the lower score")),
                () -> assertTrue(model.prompts().get(0).contains("Return only a valid JSON object with this structure")),
                () -> assertTrue(model.prompts().get(0).contains("\"score\": 0.0")),
                () -> assertTrue(model.prompts().get(0).contains("1-3 factual sentences explaining what parts")),
                () -> assertTrue(model.prompts().get(0).contains("specific missing or incorrect elements")),
                () -> assertTrue(model.prompts().get(0).contains("*** END OF EXAMPLES ***")),
                () -> assertTrue(model.prompts().get(0).contains("SCORING GUIDE")),
                () -> assertTrue(model.prompts().get(0).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(0).contains("Need a hotel and flight.")),
                () -> assertTrue(model.prompts().get(0).contains("I booked the hotel.")),
                () -> assertTrue(model.prompts().get(1).contains("planning quality")),
                () -> assertTrue(model.prompts().get(1).contains("Plan Quality")),
                () -> assertTrue(model.prompts().get(1).contains("Plan Adherence")),
                () -> assertTrue(model.prompts().get(1).contains("Tool use should be coherent")),
                () -> assertTrue(model.prompts().get(1).contains("This evaluation excludes correctness or efficiency")),
                () -> assertTrue(model.prompts().get(1).contains("Identify the agent's plan")),
                () -> assertTrue(model.prompts().get(1).contains("Return only a valid JSON object with exactly two fields")),
                () -> assertTrue(model.prompts().get(1).contains("\"score\": 0.0")),
                () -> assertTrue(model.prompts().get(1).contains("1-3 concise sentences explaining the quality")),
                () -> assertTrue(model.prompts().get(1).contains("**** END OF EXAMPLE ****")),
                () -> assertTrue(model.prompts().get(1).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(1).contains("Need a hotel and flight.")),
                () -> assertTrue(model.prompts().get(2).contains("final justification")),
                () -> assertTrue(model.prompts().get(2).contains("both must be addressed")),
                () -> assertTrue(model.prompts().get(2).contains("If the agent **passed**")),
                () -> assertTrue(model.prompts().get(2).contains("Do **not** include JSON")),
                () -> assertTrue(model.prompts().get(2).contains("Result: PASS")),
                () -> assertTrue(model.prompts().get(2).contains("0.625")),
                () -> assertTrue(model.prompts().get(2).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(2).contains("Hotel booked but flight omitted.")));
    }

    @Test
    void includeReasonFalseSkipsFinalReasonGeneration() {
        var model = new ScriptedModel(List.of(
                "{\"score\":1.0,\"reason\":\"Complete.\"}",
                "{\"score\":1.0,\"reason\":\"Clear plan.\"}"));
        var metric = new GoalAccuracyMetric(model, 0.5, false, false);

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals(1.0, result.score()),
                () -> assertTrue(result.success()),
                () -> assertNull(result.reason()),
                () -> assertEquals(2, model.prompts().size()));
    }

    @Test
    void strictModeZerosScoresBelowThreshold() {
        var metric = new StubGoalAccuracyMetric(
                List.of(new GoalSteps("Book travel", List.of("Booked hotel only"))),
                List.of(new GoalScore(0.5, "Incomplete.")),
                List.of(new PlanScore(0.5, "Vague.")),
                "Incomplete goal and plan.",
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
                        () -> new GoalAccuracyMetric(Double.NaN, true, false)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new GoalAccuracyMetric(Double.POSITIVE_INFINITY, true, false)));
    }

    @Test
    void noUnitInteractionsScoresZero() {
        var metric = new StubGoalAccuracyMetric(List.of(), List.of(), List.of(), "No steps.", false);
        var testCase = ConversationalTestCase.builder(List.of(new Turn("user", "Book travel."))).build();

        var result = metric.measure(testCase);

        assertAll(
                () -> assertEquals(0.0, result.score()),
                () -> assertFalse(result.success()));
    }

    private static ConversationalTestCase testCase() {
        return ConversationalTestCase.builder(List.of(
                new Turn("user", "Need a hotel and flight."),
                new Turn("assistant", "I booked the hotel."))).build();
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

    private static final class StubGoalAccuracyMetric extends GoalAccuracyMetric {
        private final List<GoalSteps> goalSteps;
        private final List<GoalScore> goalScores;
        private final List<PlanScore> planScores;
        private final String reason;

        StubGoalAccuracyMetric(
                List<GoalSteps> goalSteps,
                List<GoalScore> goalScores,
                List<PlanScore> planScores,
                String reason,
                boolean strictMode) {
            super(0.5, true, strictMode);
            this.goalSteps = goalSteps;
            this.goalScores = goalScores;
            this.planScores = planScores;
            this.reason = reason;
        }

        @Override
        protected List<GoalSteps> buildGoalSteps(List<List<Turn>> unitInteractions) {
            return goalSteps;
        }

        @Override
        protected GoalScore getGoalScore(String userGoal, List<String> stepsTaken, boolean multimodal) {
            return goalScores.get(goalSteps.indexOf(new GoalSteps(userGoal, stepsTaken)));
        }

        @Override
        protected PlanScore getPlanScore(String userGoal, List<String> stepsTaken, boolean multimodal) {
            return planScores.get(goalSteps.indexOf(new GoalSteps(userGoal, stepsTaken)));
        }

        @Override
        protected String generateReason(boolean multimodal) {
            return reason;
        }
    }
}
