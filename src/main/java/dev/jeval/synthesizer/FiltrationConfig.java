package dev.jeval.synthesizer;

import dev.jeval.EvaluationModel;

public record FiltrationConfig(
        double syntheticInputQualityThreshold,
        int maxQualityRetries,
        EvaluationModel criticModel) {
    public FiltrationConfig {
        if (!Double.isFinite(syntheticInputQualityThreshold)
                || syntheticInputQualityThreshold < 0.0
                || syntheticInputQualityThreshold > 1.0) {
            throw new IllegalArgumentException("synthetic_input_quality_threshold must be between 0 and 1.");
        }
        if (maxQualityRetries < 0) {
            throw new IllegalArgumentException("max_quality_retries must be non-negative.");
        }
    }

    public FiltrationConfig() {
        this(0.5, 3, null);
    }
}
