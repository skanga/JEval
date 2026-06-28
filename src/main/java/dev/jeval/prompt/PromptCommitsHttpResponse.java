package dev.jeval.prompt;

import java.util.List;

public record PromptCommitsHttpResponse(List<PromptCommit> commits) {
    public PromptCommitsHttpResponse {
        commits = commits == null ? null : List.copyOf(commits);
    }
}
