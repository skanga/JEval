package dev.jeval;

public record EvaluationCost(Double value, Integer inputTokens, Integer outputTokens) {
    public EvaluationCost {
        if (value != null && (!Double.isFinite(value) || value < 0.0)) {
            throw new IllegalArgumentException("EvaluationCost value must be finite and non-negative");
        }
        if ((inputTokens != null && inputTokens < 0) || (outputTokens != null && outputTokens < 0)) {
            throw new IllegalArgumentException("EvaluationCost token counts must be non-negative");
        }
    }
}
