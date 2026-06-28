package dev.jeval;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record TurnApi(
        String role,
        String content,
        Integer order,
        String userId,
        List<String> retrievalContext,
        List<ToolCall> toolsCalled,
        String comments,
        List<Map<String, Object>> mcpToolsCalled,
        List<Map<String, Object>> mcpResourcesCalled,
        List<Map<String, Object>> mcpPromptsCalled) {

    public TurnApi {
        retrievalContext = retrievalContext == null ? null : List.copyOf(retrievalContext);
        toolsCalled = toolsCalled == null ? null : List.copyOf(toolsCalled);
        mcpToolsCalled = copyMaps(mcpToolsCalled);
        mcpResourcesCalled = copyMaps(mcpResourcesCalled);
        mcpPromptsCalled = copyMaps(mcpPromptsCalled);
    }

    public Map<String, Object> modelDump() {
        return modelDump(false);
    }

    public Map<String, Object> modelDump(boolean byAlias) {
        var dump = new LinkedHashMap<String, Object>();
        dump.put("role", role);
        dump.put("content", content);
        dump.put("order", order);
        dump.put(key("user_id", "userId", byAlias), userId);
        dump.put(key("retrieval_context", "retrievalContext", byAlias), retrievalContext);
        dump.put(key("tools_called", "toolsCalled", byAlias), dumpTools(toolsCalled, byAlias));
        dump.put("comments", comments);
        dump.put(key("mcp_tools_called", "mcpToolsCalled", byAlias), mcpToolsCalled);
        dump.put(key("mcp_resources_called", "mcpResourcesCalled", byAlias), mcpResourcesCalled);
        dump.put(key("mcp_prompts_called", "mcpPromptsCalled", byAlias), mcpPromptsCalled);
        return dump;
    }

    private static List<Map<String, Object>> copyMaps(List<Map<String, Object>> values) {
        return values == null
                ? null
                : values.stream().map(value -> Collections.unmodifiableMap(new LinkedHashMap<>(value))).toList();
    }

    private static List<Map<String, Object>> dumpTools(List<ToolCall> tools, boolean byAlias) {
        return tools == null ? null : tools.stream().map(tool -> tool.modelDump(byAlias)).toList();
    }

    private static String key(String snakeCase, String camelCase, boolean byAlias) {
        return byAlias ? camelCase : snakeCase;
    }
}
