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
        String verboseLogs) {}
