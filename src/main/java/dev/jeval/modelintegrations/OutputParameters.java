package dev.jeval.modelintegrations;

import dev.jeval.ToolCall;
import java.util.List;

public record OutputParameters(
        Object output,
        Integer promptTokens,
        Integer completionTokens,
        List<ToolCall> toolsCalled) {

    public OutputParameters() {
        this(null, null, null, null);
    }

    public OutputParameters {
        toolsCalled = toolsCalled == null ? null : List.copyOf(toolsCalled);
    }
}
