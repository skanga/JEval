package dev.jeval.optimizer;

import dev.jeval.prompt.Prompt;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record PromptConfiguration(String id, String parent, Map<String, Prompt> prompts) {

    public PromptConfiguration {
        id = Objects.requireNonNull(id, "id");
        prompts = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(prompts, "prompts")));
    }

    public static PromptConfiguration create(Map<String, Prompt> prompts) {
        return create(prompts, null);
    }

    public static PromptConfiguration create(Map<String, Prompt> prompts, String parent) {
        return new PromptConfiguration(UUID.randomUUID().toString(), parent, prompts);
    }
}
