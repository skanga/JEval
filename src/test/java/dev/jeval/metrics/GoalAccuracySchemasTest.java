package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class GoalAccuracySchemasTest {

    @Test
    void parsesGoalAndPlanScores() {
        var goalScore = GoalAccuracySchemas.parseGoalScore("""
                {"score":0.75,"reason":"The visible answer missed one detail."}
                """);
        var planScore = GoalAccuracySchemas.parsePlanScore("""
                {"score":0.5,"reason":"The plan was vague."}
                """);
        var stringScore = GoalAccuracySchemas.parseGoalScore("""
                {"score":"0.75","reason":"The visible answer missed one detail."}
                """);

        assertEquals(0.75, goalScore.score());
        assertEquals("The visible answer missed one detail.", goalScore.reason());
        assertEquals(0.75, stringScore.score());
        assertEquals(0.5, planScore.score());
        assertEquals("The plan was vague.", planScore.reason());
    }

    @Test
    void rejectsMissingOrInvalidScoreReasonFieldsLikeDeepEval() {
        assertThrows(IllegalArgumentException.class, () -> GoalAccuracySchemas.parseGoalScore("{\"score\":0.75}"));
        assertThrows(IllegalArgumentException.class, () -> GoalAccuracySchemas.parseGoalScore("{\"reason\":\"ok\"}"));
        assertThrows(IllegalArgumentException.class, () -> GoalAccuracySchemas.parseGoalScore("{\"score\":\"bad\",\"reason\":\"ok\"}"));
        assertThrows(IllegalArgumentException.class, () -> GoalAccuracySchemas.parseGoalScore("{\"score\":0.75,\"reason\":1}"));
        assertThrows(IllegalArgumentException.class, () -> GoalAccuracySchemas.parsePlanScore("{\"score\":0.75}"));
        assertThrows(IllegalArgumentException.class, () -> GoalAccuracySchemas.parsePlanScore("{\"reason\":\"ok\"}"));
        assertThrows(IllegalArgumentException.class, () -> GoalAccuracySchemas.parsePlanScore("{\"score\":\"bad\",\"reason\":\"ok\"}"));
        assertThrows(IllegalArgumentException.class, () -> GoalAccuracySchemas.parsePlanScore("{\"score\":0.75,\"reason\":1}"));
    }
}
