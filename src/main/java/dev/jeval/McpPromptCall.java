package dev.jeval;

public record McpPromptCall(
        String name,
        Object result) {

    public McpPromptCall {
        if (name == null) {
            throw new IllegalArgumentException("'name' must be a string");
        }
    }
}
