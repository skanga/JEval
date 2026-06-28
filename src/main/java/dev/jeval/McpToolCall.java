package dev.jeval;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record McpToolCall(
        String name,
        Map<String, Object> args,
        Object result) {

    public McpToolCall {
        if (name == null) {
            throw new IllegalArgumentException("'name' must be a string");
        }
        if (args == null) {
            throw new IllegalArgumentException("'args' must be a map");
        }
        args = Collections.unmodifiableMap(new LinkedHashMap<>(args));
    }
}
