package dev.jeval.prompt;

public record PromptApi(String id, PromptType type) {
    public PromptApi {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("PromptApi id is required");
        }
        if (type == null) {
            throw new IllegalArgumentException("PromptApi type is required");
        }
    }
}
