package dev.jeval;

public record EvaluationCost(double value, int inputTokens, int outputTokens) {
    public EvaluationCost {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException("EvaluationCost value must be finite and non-negative");
        }
        if (inputTokens < 0 || outputTokens < 0) {
            throw new IllegalArgumentException("EvaluationCost token counts must be non-negative");
        }
    }
}
