package dev.jeval.runner;

import dev.jeval.prompt.ModelSettings;
import dev.jeval.prompt.OutputType;
import dev.jeval.prompt.PromptInterpolationType;
import dev.jeval.prompt.PromptMessage;
import java.util.List;

public record PromptData(
        String alias,
        String hash,
        String version,
        String textTemplate,
        List<PromptMessage> messagesTemplate,
        ModelSettings modelSettings,
        OutputType outputType,
        PromptInterpolationType interpolationType) {

    public PromptData {
        messagesTemplate = messagesTemplate == null ? null : List.copyOf(messagesTemplate);
    }
}
