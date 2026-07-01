package dev.jeval.prompt;

import java.util.List;

public record ModelSettings(
        ModelProvider provider,
        String name,
        Double temperature,
        Integer maxTokens,
        Double topP,
        Double frequencyPenalty,
        Double presencePenalty,
        List<String> stopSequence,
        ReasoningEffort reasoningEffort,
        Verbosity verbosity) {

    public ModelSettings {
        requireFinite(temperature, "temperature");
        requireFinite(topP, "topP");
        requireFinite(frequencyPenalty, "frequencyPenalty");
        requireFinite(presencePenalty, "presencePenalty");
        if (maxTokens != null && maxTokens < 0) {
            throw new IllegalArgumentException("ModelSettings maxTokens must be non-negative");
        }
        stopSequence = stopSequence == null ? null : List.copyOf(stopSequence);
    }

    private static void requireFinite(Double value, String name) {
        if (value != null && !Double.isFinite(value)) {
            throw new IllegalArgumentException("ModelSettings " + name + " must be finite");
        }
    }
}
