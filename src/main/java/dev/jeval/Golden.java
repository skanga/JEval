package dev.jeval;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public record Golden(
        String input,
        String actualOutput,
        String expectedOutput,
        List<String> context,
        List<Object> retrievalContext,
        Map<String, Object> additionalMetadata,
        String comments,
        List<ToolCall> toolsCalled,
        List<ToolCall> expectedTools,
        String sourceFile,
        String name,
        Map<String, String> customColumnKeyValues,
        Integer datasetRank,
        String datasetAlias,
        String datasetId,
        boolean multimodal) {
    private static final Pattern MULTIMODAL_PLACEHOLDER = Pattern.compile("\\[DEEPEVAL:(?:IMAGE|PDF):(.*?)]");

    public Golden {
        if (input == null) {
            throw new IllegalArgumentException("'input' must be a string");
        }
        context = copyStringList("'context' must be a list of strings", context);
        retrievalContext = retrievalContextText(retrievalContext);
        additionalMetadata = copyObjectMap(additionalMetadata);
        toolsCalled = copyToolCallList("'tools_called' must be a list of ToolCall", toolsCalled);
        expectedTools = copyToolCallList("'expected_tools' must be a list of ToolCall", expectedTools);
        customColumnKeyValues = copyStringMap("'custom_column_key_values' must contain string keys and values",
                customColumnKeyValues);
        multimodal = multimodal
                || containsPlaceholder(input)
                || containsPlaceholder(actualOutput)
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
        dump.put(key("additional_metadata", "additionalMetadata", byAlias), additionalMetadata);
        dump.put("comments", comments);
        dump.put(key("tools_called", "toolsCalled", byAlias), dumpTools(toolsCalled, byAlias));
        dump.put(key("expected_tools", "expectedTools", byAlias), dumpTools(expectedTools, byAlias));
        dump.put(key("source_file", "sourceFile", byAlias), sourceFile);
        dump.put("name", name);
        dump.put(key("custom_column_key_values", "customColumnKeyValues", byAlias), customColumnKeyValues);
        dump.put(key("images_mapping", "imagesMapping", byAlias), imagesMapping());
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

    public static Golden from(LlmTestCase testCase) {
        return Golden.builder(testCase.input())
                .actualOutput(testCase.actualOutput())
                .expectedOutput(testCase.expectedOutput())
                .context(testCase.context())
                .retrievalContext(testCase.retrievalContext())
                .additionalMetadata(testCase.additionalMetadata())
                .toolsCalled(testCase.toolsCalled())
                .expectedTools(testCase.expectedTools())
                .datasetRank(testCase.datasetRank())
                .datasetAlias(testCase.datasetAlias())
                .datasetId(testCase.datasetId())
                .multimodal(testCase.multimodal())
                .build();
    }

    public LlmTestCase toTestCase() {
        return toTestCase(datasetRank);
    }

    public LlmTestCase toTestCase(Integer datasetRank) {
        return LlmTestCase.builder(input)
                .actualOutput(actualOutput)
                .expectedOutput(expectedOutput)
                .context(context)
                .retrievalContext(retrievalContext)
                .additionalMetadata(additionalMetadata)
                .comments(comments)
                .toolsCalled(toolsCalled)
                .expectedTools(expectedTools)
                .name(name)
                .datasetRank(datasetRank)
                .datasetAlias(datasetAlias)
                .datasetId(datasetId)
                .build();
    }

    public static final class Builder {
        private final String input;
        private String actualOutput;
        private String expectedOutput;
        private List<String> context;
        private List<?> retrievalContext;
        private Map<String, Object> additionalMetadata;
        private String comments;
        private List<ToolCall> toolsCalled;
        private List<ToolCall> expectedTools;
        private String sourceFile;
        private String name;
        private Map<String, String> customColumnKeyValues;
        private Integer datasetRank;
        private String datasetAlias;
        private String datasetId;
        private boolean multimodal;

        private Builder(String input) {
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

        public Builder comments(String comments) {
            this.comments = comments;
            return this;
        }

        public Builder toolsCalled(List<ToolCall> toolsCalled) {
            this.toolsCalled = toolsCalled;
            return this;
        }

        public Builder expectedTools(List<ToolCall> expectedTools) {
            this.expectedTools = expectedTools;
            return this;
        }

        public Builder sourceFile(String sourceFile) {
            this.sourceFile = sourceFile;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder customColumnKeyValues(Map<String, String> customColumnKeyValues) {
            this.customColumnKeyValues = customColumnKeyValues;
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

        public Golden build() {
            return new Golden(input, actualOutput, expectedOutput, context, retrievalContextText(retrievalContext), additionalMetadata,
                    comments, toolsCalled, expectedTools, sourceFile, name, customColumnKeyValues,
                    datasetRank, datasetAlias, datasetId, multimodal);
        }
    }

    private static boolean containsPlaceholder(String value) {
        return value != null && MULTIMODAL_PLACEHOLDER.matcher(value).find();
    }

    private static boolean containsPlaceholder(List<?> values) {
        return values != null && values.stream()
                .map(RetrievedContextData::textValue)
                .anyMatch(Golden::containsPlaceholder);
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

    private static List<Map<String, Object>> dumpTools(List<ToolCall> tools, boolean byAlias) {
        return tools == null ? null : tools.stream().map(tool -> tool.modelDump(byAlias)).toList();
    }

    private static String key(String snakeCase, String camelCase, boolean byAlias) {
        return byAlias ? camelCase : snakeCase;
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
