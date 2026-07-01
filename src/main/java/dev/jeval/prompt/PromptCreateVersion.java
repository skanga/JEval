package dev.jeval.prompt;

public record PromptCreateVersion(String hash) {
    public PromptCreateVersion {
        if (hash == null || hash.isBlank()) {
            throw new IllegalArgumentException("PromptCreateVersion hash is required");
        }
    }
}
