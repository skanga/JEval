package dev.jeval;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public record ConversationalTestCase(
        List<Turn> turns,
        String scenario,
        List<String> context,
        String name,
        String userDescription,
        String expectedOutcome,
        String chatbotRole,
        List<Map<String, Object>> mcpServers,
        Map<String, Object> metadata,
        String comments,
        List<String> tags,
        Integer datasetRank,
        String datasetAlias,
        String datasetId,
        boolean multimodal) {
    private static final Pattern MULTIMODAL_PLACEHOLDER = Pattern.compile("\\[DEEPEVAL:(?:IMAGE|PDF):(.*?)]");

    public ConversationalTestCase {
        if (turns == null || turns.isEmpty()) {
            throw new IllegalArgumentException("'turns' must not be empty");
        }
        turns = copyTurns(turns);
        context = copyContext(context);
        mcpServers = copyMaps(mcpServers);
        metadata = copyObjectMap(metadata);
        tags = copyStringList("'tags' must be a list of strings", tags);
        if (datasetRank != null && datasetRank < 0) {
            throw new IllegalArgumentException("ConversationalTestCase datasetRank must be non-negative");
        }
        multimodal = multimodal
                || containsPlaceholder(scenario)
                || containsPlaceholder(expectedOutcome)
                || containsPlaceholder(userDescription)
                || turns.stream().anyMatch(ConversationalTestCase::containsPlaceholder);
    }

    public static Builder builder(List<Turn> turns) {
        return new Builder(turns);
    }

    public Map<String, Object> additionalMetadata() {
        return metadata;
    }

    public Map<String, Object> modelDump() {
        return modelDump(false);
    }

    public Map<String, Object> modelDump(boolean byAlias) {
        var dump = new LinkedHashMap<String, Object>();
        dump.put("turns", turns.stream().map(turn -> turn.modelDump(false, byAlias)).toList());
        dump.put("scenario", scenario);
        dump.put("context", context);
        dump.put("name", name);
        dump.put(key("user_description", "userDescription", byAlias), userDescription);
        dump.put(key("expected_outcome", "expectedOutcome", byAlias), expectedOutcome);
        dump.put(key("chatbot_role", "chatbotRole", byAlias), chatbotRole);
        dump.put("metadata", metadata);
        dump.put("comments", comments);
        dump.put("tags", tags);
        dump.put("mcp_servers", mcpServers);
        dump.put("multimodal", multimodal);
        return dump;
    }

    public Map<String, MllmImage> imagesMapping() {
        var mapping = new HashMap<String, MllmImage>();
        addImages(scenario, mapping);
        addImages(expectedOutcome, mapping);
        addImages(context, mapping);
        addImages(userDescription, mapping);
        turns.forEach(turn -> {
            addImages(turn.content(), mapping);
            addImages(turn.retrievalContext(), mapping);
        });
        return mapping.isEmpty() ? null : mapping;
    }

    public ConversationalTestCase withDatasetRank(Integer datasetRank) {
        return new ConversationalTestCase(turns, scenario, context, name, userDescription,
                expectedOutcome, chatbotRole, mcpServers, metadata, comments, tags,
                datasetRank, datasetAlias, datasetId, multimodal);
    }

    public static final class Builder {
        private final List<Turn> turns;
        private String scenario;
        private List<?> context;
        private String name;
        private String userDescription;
        private String expectedOutcome;
        private String chatbotRole;
        private List<Map<String, Object>> mcpServers;
        private Map<String, Object> metadata;
        private String comments;
        private List<String> tags;
        private Integer datasetRank;
        private String datasetAlias;
        private String datasetId;
        private boolean multimodal;

        private Builder(List<Turn> turns) {
            this.turns = turns;
        }

        public Builder scenario(String scenario) {
            this.scenario = scenario;
            return this;
        }

        public Builder context(List<?> context) {
            this.context = context;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder userDescription(String userDescription) {
            this.userDescription = userDescription;
            return this;
        }

        public Builder expectedOutcome(String expectedOutcome) {
            this.expectedOutcome = expectedOutcome;
            return this;
        }

        public Builder chatbotRole(String chatbotRole) {
            this.chatbotRole = chatbotRole;
            return this;
        }

        public Builder mcpServers(List<Map<String, Object>> mcpServers) {
            this.mcpServers = mcpServers;
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

        public Builder comments(String comments) {
            this.comments = comments;
            return this;
        }

        public Builder tags(List<String> tags) {
            this.tags = tags;
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

        public ConversationalTestCase build() {
            return new ConversationalTestCase(turns, scenario, copyContext(context), name, userDescription,
                    expectedOutcome, chatbotRole, mcpServers, metadata, comments, tags,
                    datasetRank, datasetAlias, datasetId, multimodal);
        }
    }

    private static boolean containsPlaceholder(Turn turn) {
        return containsPlaceholder(turn.content()) || containsPlaceholder(turn.retrievalContext());
    }

    private static boolean containsPlaceholder(String value) {
        return value != null && MULTIMODAL_PLACEHOLDER.matcher(value).find();
    }

    private static boolean containsPlaceholder(List<?> values) {
        return values != null && values.stream()
                .map(RetrievedContextData::textValue)
                .anyMatch(ConversationalTestCase::containsPlaceholder);
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

    private static List<Turn> copyTurns(List<?> values) {
        return values.stream().map(value -> {
            if (value instanceof Turn turn) {
                return turn;
            }
            if (value instanceof Map<?, ?> map
                    && map.get("role") instanceof String role
                    && map.get("content") instanceof String content) {
                return turnFromMap(map, role, content);
            }
            throw new IllegalArgumentException("'turns' must be a list of Turn");
        }).toList();
    }

    @SuppressWarnings("unchecked")
    private static Turn turnFromMap(Map<?, ?> map, String role, String content) {
        return new Turn(
                role,
                content,
                (String) firstPresent(map, "user_id", "userId"),
                (List<Object>) firstPresent(map, "retrieval_context", "retrievalContext"),
                toolCalls(firstPresent(map, "tools_called", "toolsCalled")),
                (List<Map<String, Object>>) map.get("mcp_tools_called"),
                (List<Map<String, Object>>) map.get("mcp_resources_called"),
                (List<Map<String, Object>>) map.get("mcp_prompts_called"),
                (Map<String, Object>) firstPresent(map, "metadata", "additionalMetadata", "additional_metadata"));
    }

    private static List<ToolCall> toolCalls(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof List<?> values)) {
            throw new IllegalArgumentException("'tools_called' must be a list of ToolCall");
        }
        return values.stream().map(ConversationalTestCase::toolCall).toList();
    }

    private static ToolCall toolCall(Object value) {
        if (value instanceof ToolCall toolCall) {
            return toolCall;
        }
        if (!(value instanceof Map<?, ?> map) || !(map.get("name") instanceof String name)) {
            throw new IllegalArgumentException("'tools_called' must be a list of ToolCall");
        }
        return new ToolCall(
                name,
                stringOrNull(map, "description"),
                stringOrNull(map, "reasoning"),
                inputParameters(map),
                map.get("output"));
    }

    private static String stringOrNull(Map<?, ?> map, String key) {
        var value = map.get(key);
        if (value == null || value instanceof String) {
            return (String) value;
        }
        throw new IllegalArgumentException("'tools_called' entries must include string " + key);
    }

    private static Map<?, ?> inputParameters(Map<?, ?> map) {
        var value = firstPresent(map, "input_parameters", "inputParameters");
        if (value == null || value instanceof Map<?, ?>) {
            return (Map<?, ?>) value;
        }
        throw new IllegalArgumentException("'input_parameters' must be an object");
    }

    private static Object firstPresent(Map<?, ?> map, String... keys) {
        for (var key : keys) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }
        return null;
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

    private static List<String> copyContext(List<?> values) {
        if (values == null) {
            return null;
        }
        return values.stream().map(value -> {
            if (value instanceof String text) {
                return text;
            }
            if (value instanceof RetrievedContextData data) {
                return data.context();
            }
            throw new IllegalArgumentException(
                    "'context' must be a list of strings or RetrievedContextData");
        }).toList();
    }

    private static Map<String, Object> copyObjectMap(Map<String, Object> values) {
        return values == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
