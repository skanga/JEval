package dev.jeval.prompt;

public record PromptVersion(String id, String version) {
    public PromptVersion {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("PromptVersion id is required");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("PromptVersion version is required");
        }
    }
}
