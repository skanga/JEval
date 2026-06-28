package dev.jeval.prompt;

import java.util.List;

public record PromptHttpResponse(
        String id,
        String hash,
        String version,
        String label,
        String text,
        List<PromptMessage> messages,
        PromptInterpolationType interpolationType,
        PromptType type,
        ModelSettings modelSettings,
        OutputType outputType,
        OutputSchema outputSchema,
        List<Tool> tools,
        String branch) {

    public PromptHttpResponse {
        messages = messages == null ? null : List.copyOf(messages);
        tools = tools == null ? null : List.copyOf(tools);
    }
}
