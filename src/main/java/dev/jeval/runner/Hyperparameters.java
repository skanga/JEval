package dev.jeval.runner;

import dev.jeval.prompt.Prompt;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Hyperparameters {
    private Hyperparameters() {
    }

    public static Map<String, Object> process(Map<?, ?> hyperparameters) {
        if (hyperparameters == null) {
            return null;
        }
        var processed = new LinkedHashMap<String, Object>();
        for (var entry : hyperparameters.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException("Hyperparameter key '" + entry.getKey() + "' must be a string");
            }
            var value = entry.getValue();
            if (value == null) {
                continue;
            }
            if (value instanceof String || value instanceof Integer || value instanceof Long
                    || value instanceof Float || value instanceof Double) {
                processed.put(key, String.valueOf(value));
            } else if (!(value instanceof Prompt)) {
                throw new IllegalArgumentException("Hyperparameter value for key '" + key
                        + "' must be a string, integer, float, or Prompt");
            }
        }
        return Collections.unmodifiableMap(processed);
    }

    public static List<PromptData> processPrompts(Map<String, ?> hyperparameters) {
        if (hyperparameters == null || hyperparameters.isEmpty()) {
            return List.of();
        }
        var prompts = new ArrayList<PromptData>();
        var seen = new java.util.HashSet<String>();
        for (var value : hyperparameters.values()) {
            if (value instanceof Prompt prompt) {
                var key = prompt.alias() + "_null";
                if (seen.add(key)) {
                    prompts.add(new PromptData(
                            prompt.alias(),
                            null,
                            null,
                            prompt.textTemplate(),
                            prompt.messagesTemplate(),
                            prompt.modelSettings(),
                            prompt.outputType(),
                            prompt.interpolationType()));
                }
            }
        }
        return List.copyOf(prompts);
    }
}
