package dev.jeval;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public record LlmTestCase(
        String input,
        String actualOutput,
        String expectedOutput,
        List<String> context,
        List<Object> retrievalContext,
        Map<String, Object> additionalMetadata,
        List<ToolCall> toolsCalled,
        String comments,
        List<ToolCall> expectedTools,
        Double tokenCost,
        Double completionTime,
        String name,
        List<String> tags,
        Map<String, String> customColumnKeyValues,
        List<Map<String, Object>> mcpServers,
        List<Map<String, Object>> mcpToolsCalled,
        List<Map<String, Object>> mcpResourcesCalled,
        List<Map<String, Object>> mcpPromptsCalled,
        Map<String, Object> trace,
        String identifier,
        Integer datasetRank,
        String datasetAlias,
        String datasetId,
        boolean multimodal) {
    private static final Pattern MULTIMODAL_PLACEHOLDER = Pattern.compile("\\[DEEPEVAL:(?:IMAGE|PDF):(.*?)]");

    public LlmTestCase(String input, String actualOutput, String expectedOutput) {
        this(input, actualOutput, expectedOutput, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, false);
    }

    public LlmTestCase {
        if (input == null) {
            throw new IllegalArgumentException("'input' must be a string");
        }
        context = copyStringList("'context' must be a list of strings", context);
        retrievalContext = retrievalContextText(retrievalContext);
        additionalMetadata = copyObjectMap(additionalMetadata);
        toolsCalled = copyToolCallList("'tools_called' must be a list of ToolCall", toolsCalled);
        expectedTools = copyToolCallList("'expected_tools' must be a list of ToolCall", expectedTools);
        tags = copyStringList("'tags' must be a list of strings", tags);
        customColumnKeyValues = copyStringMap("'custom_column_key_values' must contain string keys and values",
                customColumnKeyValues);
        mcpServers = copyMaps(mcpServers);
        mcpToolsCalled = copyMaps(mcpToolsCalled);
        mcpResourcesCalled = copyMaps(mcpResourcesCalled);
        mcpPromptsCalled = copyMaps(mcpPromptsCalled);
        trace = copyObjectMap(trace);
        identifier = identifier == null ? UUID.randomUUID().toString() : identifier;
        if (tokenCost != null && (!Double.isFinite(tokenCost) || tokenCost < 0.0)) {
            throw new IllegalArgumentException("LlmTestCase tokenCost must be finite and non-negative");
        }
        if (completionTime != null && (!Double.isFinite(completionTime) || completionTime < 0.0)) {
            throw new IllegalArgumentException("LlmTestCase completionTime must be finite and non-negative");
        }
        if (datasetRank != null && datasetRank < 0) {
            throw new IllegalArgumentException("LlmTestCase datasetRank must be non-negative");
        }
        multimodal = multimodal
                || containsPlaceholder(input)
                || containsPlaceholder(actualOutput)
                || containsPlaceholder(expectedOutput)
                || containsPlaceholder(context)
                || containsPlaceholder(retrievalContext);
    }

    public static Builder builder(String input) {
        return new Builder(input);
    }

    public Map<String, Object> metadata() {
        return additionalMetadata;
    }

    public Map<String, Object> modelDump() {
        return modelDump(false);
    }

    public Map<String, Object> modelDump(boolean byAlias) {
        var dump = new LinkedHashMap<String, Object>();
        dump.put("input", input);
        dump.put(key("actual_output", "actualOutput", byAlias), actualOutput);
        dump.put(key("expected_output", "expectedOutput", byAlias), expectedOutput);
        dump.put("context", context);
        dump.put(key("retrieval_context", "retrievalContext", byAlias), RetrievedContextData.modelDumpValues(retrievalContext));
        dump.put("metadata", additionalMetadata);
        dump.put(key("tools_called", "toolsCalled", byAlias), dumpTools(toolsCalled, byAlias));
        dump.put("comments", comments);
        dump.put(key("expected_tools", "expectedTools", byAlias), dumpTools(expectedTools, byAlias));
        dump.put(key("token_cost", "tokenCost", byAlias), tokenCost);
        dump.put(key("completion_time", "completionTime", byAlias), completionTime);
        dump.put("multimodal", multimodal);
        dump.put("name", name);
        dump.put("tags", tags);
        dump.put("mcp_servers", mcpServers);
        dump.put(key("mcp_tools_called", "mcpToolsCalled", byAlias), mcpToolsCalled);
        dump.put(key("mcp_resources_called", "mcpResourcesCalled", byAlias), mcpResourcesCalled);
        dump.put(key("mcp_prompts_called", "mcpPromptsCalled", byAlias), mcpPromptsCalled);
        dump.put(key("custom_column_key_values", "customColumnKeyValues", byAlias), customColumnKeyValues);
        return dump;
    }

    public Map<String, MllmImage> imagesMapping() {
        var mapping = new HashMap<String, MllmImage>();
        addImages(input, mapping);
        addImages(actualOutput, mapping);
        addImages(expectedOutput, mapping);
        addImages(context, mapping);
        addImages(retrievalContext, mapping);
        return mapping.isEmpty() ? null : mapping;
    }

    public LlmTestCase withDatasetRank(Integer datasetRank) {
        return new LlmTestCase(input, actualOutput, expectedOutput, context, retrievalContext,
                additionalMetadata, toolsCalled, comments, expectedTools, tokenCost, completionTime,
                name, tags, customColumnKeyValues, mcpServers, mcpToolsCalled, mcpResourcesCalled,
                mcpPromptsCalled, trace, identifier, datasetRank, datasetAlias, datasetId, multimodal);
    }

    public static final class Builder {
        private final String input;
        private String actualOutput;
        private String expectedOutput;
        private List<String> context;
        private List<?> retrievalContext;
        private Map<String, Object> additionalMetadata;
        private List<ToolCall> toolsCalled;
        private String comments;
        private List<ToolCall> expectedTools;
        private Double tokenCost;
        private Double completionTime;
        private String name;
        private List<String> tags;
        private Map<String, String> customColumnKeyValues;
        private List<Map<String, Object>> mcpServers;
        private List<Map<String, Object>> mcpToolsCalled;
        private List<Map<String, Object>> mcpResourcesCalled;
        private List<Map<String, Object>> mcpPromptsCalled;
        private Map<String, Object> trace;
        private Integer datasetRank;
        private String datasetAlias;
        private String datasetId;
        private boolean multimodal;

        private Builder(String input) {
            if (input == null) {
                throw new IllegalArgumentException("'input' must be a string");
            }
            this.input = input;
        }

        public Builder actualOutput(String actualOutput) {
            this.actualOutput = actualOutput;
            return this;
        }

        public Builder expectedOutput(String expectedOutput) {
            this.expectedOutput = expectedOutput;
            return this;
        }

        public Builder context(List<String> context) {
            this.context = context;
            return this;
        }

        public Builder retrievalContext(List<?> retrievalContext) {
            this.retrievalContext = retrievalContext;
            return this;
        }

        public Builder additionalMetadata(Map<String, Object> additionalMetadata) {
            this.additionalMetadata = additionalMetadata;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.additionalMetadata = metadata;
            return this;
        }

        public Builder toolsCalled(List<ToolCall> toolsCalled) {
            this.toolsCalled = toolsCalled;
            return this;
        }

        public Builder comments(String comments) {
            this.comments = comments;
            return this;
        }

        public Builder expectedTools(List<ToolCall> expectedTools) {
            this.expectedTools = expectedTools;
            return this;
        }

        public Builder tokenCost(Double tokenCost) {
            this.tokenCost = tokenCost;
            return this;
        }

        public Builder completionTime(Double completionTime) {
            this.completionTime = completionTime;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder tags(List<String> tags) {
            this.tags = tags;
            return this;
        }

        public Builder customColumnKeyValues(Map<String, String> customColumnKeyValues) {
            this.customColumnKeyValues = customColumnKeyValues;
            return this;
        }

        public Builder mcpServers(List<Map<String, Object>> mcpServers) {
            this.mcpServers = mcpServers;
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

        public Builder trace(Map<String, Object> trace) {
            this.trace = trace;
            return this;
        }

        public Builder datasetRank(Integer datasetRank) {
            this.datasetRank = datasetRank;
            return this;
        }

        public Builder datasetAlias(String datasetAlias) {
            this.datasetAlias = datasetAlias;
            return this;
        }

        public Builder datasetId(String datasetId) {
            this.datasetId = datasetId;
            return this;
        }

        public Builder multimodal(boolean multimodal) {
            this.multimodal = multimodal;
            return this;
        }

        public LlmTestCase build() {
            return new LlmTestCase(input, actualOutput, expectedOutput, context, retrievalContextText(retrievalContext),
                    additionalMetadata, toolsCalled, comments, expectedTools, tokenCost, completionTime,
                    name, tags, customColumnKeyValues, mcpServers, mcpToolsCalled, mcpResourcesCalled,
                    mcpPromptsCalled, trace, null, datasetRank, datasetAlias, datasetId, multimodal);
        }
    }

    private static boolean containsPlaceholder(String value) {
        return value != null && MULTIMODAL_PLACEHOLDER.matcher(value).find();
    }

    private static boolean containsPlaceholder(List<?> values) {
        return values != null && values.stream()
                .map(RetrievedContextData::textValue)
                .anyMatch(LlmTestCase::containsPlaceholder);
    }

    private static void addImages(String value, Map<String, MllmImage> mapping) {
        if (value == null) {
            return;
        }
        var matcher = MULTIMODAL_PLACEHOLDER.matcher(value);
        while (matcher.find()) {
            var image = MllmImage.registeredImage(matcher.group(1));
            if (image != null) {
                mapping.put(matcher.group(1), image);
            }
        }
    }

    private static void addImages(List<?> values, Map<String, MllmImage> mapping) {
        if (values != null) {
            values.forEach(value -> addImages(RetrievedContextData.textValue(value), mapping));
        }
    }

    private static String key(String snakeCase, String camelCase, boolean byAlias) {
        return byAlias ? camelCase : snakeCase;
    }

    private static List<Map<String, Object>> dumpTools(List<ToolCall> tools, boolean byAlias) {
        if (tools == null) {
            return null;
        }
        return tools.stream().map(tool -> tool.modelDump(byAlias)).toList();
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

    private static List<String> copyStringList(String message, List<?> values) {
        if (values == null) {
            return null;
        }
        for (var value : values) {
            if (!(value instanceof String)) {
                throw new IllegalArgumentException(message);
            }
        }
        return values.stream().map(String.class::cast).toList();
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

    private static Map<String, String> copyStringMap(String message, Map<?, ?> values) {
        if (values == null) {
            return null;
        }
        var copied = new LinkedHashMap<String, String>();
        for (var entry : values.entrySet()) {
            if (!(entry.getKey() instanceof String key) || !(entry.getValue() instanceof String value)) {
                throw new IllegalArgumentException(message);
            }
            copied.put(key, value);
        }
        return Collections.unmodifiableMap(copied);
    }

    private static Map<String, Object> copyObjectMap(Map<String, Object> values) {
        return values == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
