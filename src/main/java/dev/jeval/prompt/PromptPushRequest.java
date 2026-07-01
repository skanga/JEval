package dev.jeval.prompt;

import java.util.List;

public record PromptPushRequest(
        String alias,
        String text,
        List<PromptMessage> messages,
        List<Tool> tools,
        PromptInterpolationType interpolationType,
        ModelSettings modelSettings,
        OutputSchema outputSchema,
        OutputType outputType,
        String branch) {

    public PromptPushRequest {
        if (alias == null || alias.isBlank()) {
            throw new IllegalArgumentException("PromptPushRequest alias is required");
        }
        if (text != null && messages != null) {
            throw new IllegalArgumentException("PromptPushRequest cannot include both text and messages");
        }
        messages = messages == null ? null : List.copyOf(messages);
        tools = tools == null ? null : List.copyOf(tools);
    }
}
