package dev.jeval.prompt;

import java.util.List;

public record PromptVersionsHttpResponse(List<PromptVersion> textVersions, List<PromptVersion> messagesVersions) {
    public PromptVersionsHttpResponse {
        textVersions = textVersions == null ? null : List.copyOf(textVersions);
        messagesVersions = messagesVersions == null ? null : List.copyOf(messagesVersions);
    }
}
