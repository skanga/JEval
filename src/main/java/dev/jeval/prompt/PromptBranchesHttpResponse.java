package dev.jeval.prompt;

import java.util.List;

public record PromptBranchesHttpResponse(List<PromptBranch> branches) {
    public PromptBranchesHttpResponse {
        branches = branches == null ? null : List.copyOf(branches);
    }
}
