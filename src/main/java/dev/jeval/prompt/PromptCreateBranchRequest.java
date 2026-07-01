package dev.jeval.prompt;

public record PromptCreateBranchRequest(String branch) {
    public PromptCreateBranchRequest {
        if (branch == null || branch.isBlank()) {
            throw new IllegalArgumentException("PromptCreateBranchRequest branch is required");
        }
    }
}
