package dev.jeval.optimizer;

import dev.jeval.prompt.Prompt;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record PromptConfigSnapshot(String parent, Map<String, Prompt> prompts) {

    public PromptConfigSnapshot {
        prompts = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(prompts, "prompts")));
    }
}
