package dev.jeval.prompt;

public record PromptCommit(String id, String hash, String message) {
    public PromptCommit {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("PromptCommit id is required");
        }
        if (hash == null || hash.isBlank()) {
            throw new IllegalArgumentException("PromptCommit hash is required");
        }
    }
}
