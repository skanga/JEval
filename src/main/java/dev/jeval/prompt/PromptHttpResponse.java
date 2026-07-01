package dev.jeval.prompt;

import java.util.List;

public record PromptHttpResponse(
        String id,
        String hash,
        String version,
        String label,
        String text,
        List<PromptMessage> messages,
        PromptInterpolationType interpolationType,
        PromptType type,
        ModelSettings modelSettings,
        OutputType outputType,
        OutputSchema outputSchema,
        List<Tool> tools,
        String branch) {

    public PromptHttpResponse {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("PromptHttpResponse id is required");
        }
        if (hash == null || hash.isBlank()) {
            throw new IllegalArgumentException("PromptHttpResponse hash is required");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("PromptHttpResponse version is required");
        }
        if (type == null) {
            throw new IllegalArgumentException("PromptHttpResponse type is required");
        }
        messages = messages == null ? null : List.copyOf(messages);
        tools = tools == null ? null : List.copyOf(tools);
    }
}
