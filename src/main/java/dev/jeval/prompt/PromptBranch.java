package dev.jeval.prompt;

public record PromptBranch(String id, String name) {
    public PromptBranch {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("PromptBranch id is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("PromptBranch name is required");
        }
    }
}
