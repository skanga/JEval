package dev.jeval;

import java.util.Collections;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record Turn(
        String role,
        String content,
        String userId,
        List<Object> retrievalContext,
        List<ToolCall> toolsCalled,
        List<Map<String, Object>> mcpToolsCalled,
        List<Map<String, Object>> mcpResourcesCalled,
        List<Map<String, Object>> mcpPromptsCalled,
        Map<String, Object> metadata) {

    public Turn(String role, String content) {
        this(role, content, null, null, null, null, null, null, null);
    }

    public Turn {
        if (!"user".equals(role) && !"assistant".equals(role)) {
            throw new IllegalArgumentException("'role' must be 'user' or 'assistant'");
        }
        if (content == null) {
            throw new IllegalArgumentException("'content' must be a string");
        }
        retrievalContext = retrievalContextText(retrievalContext);
        toolsCalled = copyToolCallList("'tools_called' must be a list of ToolCall", toolsCalled);
        mcpToolsCalled = copyMaps(mcpToolsCalled);
        mcpResourcesCalled = copyMaps(mcpResourcesCalled);
        mcpPromptsCalled = copyMaps(mcpPromptsCalled);
        metadata = copyObjectMap(metadata);
    }

    public static Builder builder(String role, String content) {
        return new Builder(role, content);
    }

    public Map<String, Object> additionalMetadata() {
        return metadata;
    }

    public boolean mcpInteraction() {
        return mcpToolsCalled != null || mcpResourcesCalled != null || mcpPromptsCalled != null;
    }

    public Map<String, Object> modelDump() {
        return modelDump(false);
    }

    public Map<String, Object> modelDump(boolean excludeNulls) {
        return modelDump(excludeNulls, false);
    }

    public Map<String, Object> modelDump(boolean excludeNulls, boolean byAlias) {
        var dump = new LinkedHashMap<String, Object>();
        put(dump, "role", role, excludeNulls);
        put(dump, "content", content, excludeNulls);
        put(dump, "user_id", userId, excludeNulls);
        put(dump, "retrieval_context", RetrievedContextData.modelDumpValues(retrievalContext), excludeNulls);
        put(dump, "tools_called", dumpTools(toolsCalled, byAlias), excludeNulls);
        put(dump, "mcp_tools_called", mcpToolsCalled, excludeNulls);
        put(dump, "mcp_resources_called", mcpResourcesCalled, excludeNulls);
        put(dump, "mcp_prompts_called", mcpPromptsCalled, excludeNulls);
        put(dump, "metadata", metadata, excludeNulls);
        return dump;
    }

    @Override
    public String toString() {
        var fields = new ArrayList<String>();
        fields.add("role=" + repr(role));
        fields.add("content=" + repr(content));
        if (userId != null) {
            fields.add("user_id=" + repr(userId));
        }
        if (retrievalContext != null) {
            fields.add("retrieval_context=" + repr(retrievalContext));
        }
        if (toolsCalled != null) {
            fields.add("tools_called=" + toolsCalled);
        }
        if (mcpToolsCalled != null) {
            fields.add("mcp_tools_called=" + repr(mcpToolsCalled));
        }
        if (mcpResourcesCalled != null) {
            fields.add("mcp_resources_called=" + repr(mcpResourcesCalled));
        }
        if (mcpPromptsCalled != null) {
            fields.add("mcp_prompts_called=" + repr(mcpPromptsCalled));
        }
        if (metadata != null) {
            fields.add("metadata=" + repr(metadata));
        }
        return "Turn(" + String.join(", ", fields) + ")";
    }

    public static final class Builder {
        private final String role;
        private final String content;
        private String userId;
        private List<?> retrievalContext;
        private List<ToolCall> toolsCalled;
        private List<Map<String, Object>> mcpToolsCalled;
        private List<Map<String, Object>> mcpResourcesCalled;
        private List<Map<String, Object>> mcpPromptsCalled;
        private Map<String, Object> metadata;

        private Builder(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder retrievalContext(List<?> retrievalContext) {
            this.retrievalContext = retrievalContext;
            return this;
        }

        public Builder toolsCalled(List<ToolCall> toolsCalled) {
            this.toolsCalled = toolsCalled;
            return this;
        }

        public Builder mcpToolsCalled(List<Map<String, Object>> mcpToolsCalled) {
            this.mcpToolsCalled = mcpToolsCalled;
            return this;
        }

        public Builder mcpResourcesCalled(List<Map<String, Object>> mcpResourcesCalled) {
            this.mcpResourcesCalled = mcpResourcesCalled;
            return this;
        }

        public Builder mcpPromptsCalled(List<Map<String, Object>> mcpPromptsCalled) {
            this.mcpPromptsCalled = mcpPromptsCalled;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder additionalMetadata(Map<String, Object> additionalMetadata) {
            this.metadata = additionalMetadata;
            return this;
        }

        public Turn build() {
            return new Turn(role, content, userId, retrievalContextText(retrievalContext), toolsCalled,
                    mcpToolsCalled, mcpResourcesCalled, mcpPromptsCalled, metadata);
        }
    }

    private static List<Object> retrievalContextText(List<?> values) {
        if (values == null) {
            return null;
        }
        return values.stream().map(value -> {
            if (value instanceof String text) {
                return RetrievedContextData.fromMarker(text);
            }
            if (value instanceof RetrievedContextData) {
                return value;
            }
            throw new IllegalArgumentException("'retrieval_context' must contain strings or RetrievedContextData");
        }).toList();
    }

    private static List<Map<String, Object>> copyMaps(List<?> values) {
        if (values == null) {
            return null;
        }
        return values.stream().map(value -> {
            if (!(value instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("'mcp' fields must be lists of maps");
            }
            var copied = new LinkedHashMap<String, Object>();
            for (var entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("'mcp' fields must be lists of maps");
                }
                copied.put(key, entry.getValue());
            }
            return Collections.unmodifiableMap(copied);
        }).toList();
    }

    private static void put(Map<String, Object> dump, String key, Object value, boolean excludeNulls) {
        if (value != null || !excludeNulls) {
            dump.put(key, value);
        }
    }

    private static List<Map<String, Object>> dumpTools(List<ToolCall> tools, boolean byAlias) {
        return tools == null ? null : tools.stream().map(tool -> tool.modelDump(byAlias)).toList();
    }

    private static <T> List<T> copyList(String message, List<T> values) {
        if (values == null) {
            return null;
        }
        for (var value : values) {
            if (value == null) {
                throw new IllegalArgumentException(message);
            }
        }
        return List.copyOf(values);
    }

    private static List<ToolCall> copyToolCallList(String message, List<?> values) {
        if (values == null) {
            return null;
        }
        for (var value : values) {
            if (!(value instanceof ToolCall)) {
                throw new IllegalArgumentException(message);
            }
        }
        return values.stream().map(ToolCall.class::cast).toList();
    }

    private static Map<String, Object> copyObjectMap(Map<String, Object> values) {
        return values == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static String repr(String value) {
        var escaped = value
                .replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
        if (value.contains("'") && !value.contains("\"")) {
            return "\"" + escaped.replace("\"", "\\\"") + "\"";
        }
        return "'" + escaped.replace("'", "\\'") + "'";
    }

    private static String repr(Object value) {
        if (value == null) {
            return "None";
        }
        if (value instanceof String text) {
            return repr(text);
        }
        if (value instanceof Boolean bool) {
            return bool ? "True" : "False";
        }
        if (value instanceof RetrievedContextData data) {
            return "RetrievedContextData(context=" + repr(data.context()) + ", source=" + repr(data.source()) + ")";
        }
        if (value instanceof Map<?, ?> map) {
            var fields = new ArrayList<String>();
            for (var entry : map.entrySet()) {
                fields.add(repr(entry.getKey()) + ": " + repr(entry.getValue()));
            }
            return "{" + String.join(", ", fields) + "}";
        }
        if (value instanceof Iterable<?> values) {
            var items = new ArrayList<String>();
            values.forEach(item -> items.add(repr(item)));
            return "[" + String.join(", ", items) + "]";
        }
        return String.valueOf(value);
    }
}
