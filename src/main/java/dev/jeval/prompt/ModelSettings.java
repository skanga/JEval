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
        stopSequence = stopSequence == null ? null : List.copyOf(stopSequence);
    }
}
