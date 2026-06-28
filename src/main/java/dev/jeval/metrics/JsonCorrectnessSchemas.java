package dev.jeval.metrics;

public final class JsonCorrectnessSchemas {
    private JsonCorrectnessSchemas() {
    }

    public static JsonCorrectnessScoreReason parseScoreReason(String modelOutput) {
        return new JsonCorrectnessScoreReason(MetricUtils.requiredText(
                MetricUtils.trimAndLoadJson(modelOutput),
                "reason"));
    }

    public record JsonCorrectnessScoreReason(String reason) {
    }
}
