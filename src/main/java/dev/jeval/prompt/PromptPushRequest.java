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
        messages = messages == null ? null : List.copyOf(messages);
        tools = tools == null ? null : List.copyOf(tools);
    }
}
