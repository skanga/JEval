package dev.jeval.prompt;

public record PromptUpdateBranchRequest(String name) {
    public PromptUpdateBranchRequest {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("PromptUpdateBranchRequest name is required");
        }
    }
}
