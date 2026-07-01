package dev.jeval.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public final class GoalAccuracySchemas {
    private GoalAccuracySchemas() {
    }

    public static GoalScore parseGoalScore(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new GoalScore(requiredDouble(node, "score"), requiredText(node, "reason"));
    }

    public static PlanScore parsePlanScore(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new PlanScore(requiredDouble(node, "score"), requiredText(node, "reason"));
    }

    private static JsonNode required(JsonNode node, String field) {
        var value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            throw new IllegalArgumentException("Missing required goal accuracy schema field: " + field);
        }
        return value;
    }

    private static String requiredText(JsonNode node, String field) {
        var value = required(node, field);
        if (!value.isTextual()) {
            throw new IllegalArgumentException("Goal accuracy schema field must be a string: " + field);
        }
        return value.asText();
    }

    private static double requiredDouble(JsonNode node, String field) {
        var value = required(node, field);
        if (value.isNumber()) {
            return finiteDouble(value.asDouble(), field);
        }
        if (value.isTextual()) {
            try {
                return finiteDouble(Double.parseDouble(value.asText()), field);
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException("Goal accuracy schema field must be a number: " + field, error);
            }
        }
        throw new IllegalArgumentException("Goal accuracy schema field must be a number: " + field);
    }

    private static double finiteDouble(double value, String field) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Goal accuracy schema field must be a finite number: " + field);
        }
        return value;
    }

    public record GoalSteps(String userGoal, List<String> stepsTaken) {
        public GoalSteps {
            stepsTaken = stepsTaken == null ? List.of() : List.copyOf(stepsTaken);
        }
    }

    public record GoalScore(double score, String reason) {
        public GoalScore {
            if (!Double.isFinite(score)) {
                throw new IllegalArgumentException("GoalScore score must be finite");
            }
        }
    }

    public record PlanScore(double score, String reason) {
        public PlanScore {
            if (!Double.isFinite(score)) {
                throw new IllegalArgumentException("PlanScore score must be finite");
            }
        }
    }
}
