package dev.jeval;

public record MetricData(
        String name,
        double threshold,
        boolean success,
        Double score,
        String reason,
        Boolean strictMode,
        String evaluationModel,
        String error,
        Double evaluationCost,
        Integer inputTokenCount,
        Integer outputTokenCount,
        String verboseLogs) {
    public MetricData {
        if (!Double.isFinite(threshold)) {
            throw new IllegalArgumentException("MetricData threshold must be finite");
        }
        if (score != null && !Double.isFinite(score)) {
            throw new IllegalArgumentException("MetricData score must be finite");
        }
        if (evaluationCost != null && (!Double.isFinite(evaluationCost) || evaluationCost < 0.0)) {
            throw new IllegalArgumentException("MetricData evaluationCost must be finite and non-negative");
        }
        if ((inputTokenCount != null && inputTokenCount < 0)
                || (outputTokenCount != null && outputTokenCount < 0)) {
            throw new IllegalArgumentException("MetricData token counts must be non-negative");
        }
    }
}
