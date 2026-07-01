package dev.jeval.prompt;

public record PromptMessage(String role, String content) {
    public PromptMessage {
        if (role == null) {
            throw new IllegalArgumentException("PromptMessage role is required");
        }
        if (content == null) {
            throw new IllegalArgumentException("PromptMessage content is required");
        }
    }
}
