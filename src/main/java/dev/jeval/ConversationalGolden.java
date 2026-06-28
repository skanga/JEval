package dev.jeval;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public record ConversationalGolden(
        String scenario,
        String expectedOutcome,
        String userDescription,
        List<String> context,
        Map<String, Object> additionalMetadata,
        String comments,
        String name,
        Map<String, String> customColumnKeyValues,
        List<Turn> turns,
        Integer datasetRank,
        String datasetAlias,
        String datasetId,
        boolean multimodal) {
    private static final Pattern MULTIMODAL_PLACEHOLDER = Pattern.compile("\\[DEEPEVAL:(?:IMAGE|PDF):(.*?)]");

    public ConversationalGolden {
        if (scenario == null) {
            throw new IllegalArgumentException("'scenario' must be a string");
        }
        context = copyStringList("'context' must be a list of strings", context);
        additionalMetadata = copyObjectMap(additionalMetadata);
        customColumnKeyValues = copyStringMap("'custom_column_key_values' must contain string keys and values",
                customColumnKeyValues);
        turns = copyList("'turns' must be a list of Turn", turns);
        multimodal = multimodal
                || containsPlaceholder(scenario)
                || containsPlaceholder(expectedOutcome)
                || containsPlaceholder(userDescription)
                || containsPlaceholder(turns);
    }

    public static Builder builder(String scenario) {
        return new Builder(scenario);
    }

    public Map<String, Object> metadata() {
        return additionalMetadata;
    }

    public Map<String, Object> modelDump() {
        return modelDump(false);
    }

    public Map<String, Object> modelDump(boolean byAlias) {
        var dump = new LinkedHashMap<String, Object>();
        dump.put("scenario", scenario);
        dump.put(key("expected_outcome", "expectedOutcome", byAlias), expectedOutcome);
        dump.put(key("user_description", "userDescription", byAlias), userDescription);
        dump.put("context", context);
        dump.put(key("additional_metadata", "additionalMetadata", byAlias), additionalMetadata);
        dump.put("comments", comments);
        dump.put("name", name);
        dump.put(key("custom_column_key_values", "customColumnKeyValues", byAlias), customColumnKeyValues);
        dump.put("turns", turns == null ? null : turns.stream().map(turn -> turn.modelDump(false, byAlias)).toList());
        dump.put(key("images_mapping", "imagesMapping", byAlias), imagesMapping());
        return dump;
    }

    public Map<String, MllmImage> imagesMapping() {
        var mapping = new HashMap<String, MllmImage>();
        addImages(scenario, mapping);
        addImages(expectedOutcome, mapping);
        addImages(context, mapping);
        addImages(userDescription, mapping);
        if (turns != null) {
            turns.forEach(turn -> {
                addImages(turn.content(), mapping);
                addImages(turn.retrievalContext(), mapping);
            });
        }
        return mapping.isEmpty() ? null : mapping;
    }

    public static ConversationalGolden from(ConversationalTestCase testCase) {
        if (testCase.scenario() == null || testCase.scenario().isBlank()) {
            throw new IllegalArgumentException(
                    "Please provide a scenario in your 'ConversationalTestCase' to convert it to a 'ConversationalGolden'.");
        }
        return ConversationalGolden.builder(testCase.scenario())
                .turns(testCase.turns())
                .expectedOutcome(testCase.expectedOutcome())
                .userDescription(testCase.userDescription())
                .context(testCase.context())
                .additionalMetadata(testCase.metadata())
                .datasetRank(testCase.datasetRank())
                .datasetAlias(testCase.datasetAlias())
                .datasetId(testCase.datasetId())
                .multimodal(testCase.multimodal())
                .build();
    }

    public ConversationalTestCase toTestCase() {
        return toTestCase(datasetRank);
    }

    public ConversationalTestCase toTestCase(Integer datasetRank) {
        return ConversationalTestCase.builder(turns == null ? List.of() : turns)
                .scenario(scenario)
                .expectedOutcome(expectedOutcome)
                .userDescription(userDescription)
                .context(context)
                .metadata(additionalMetadata)
                .comments(comments)
                .name(name)
                .datasetRank(datasetRank)
                .datasetAlias(datasetAlias)
                .datasetId(datasetId)
                .build();
    }

    public static final class Builder {
        private final String scenario;
        private String expectedOutcome;
        private String userDescription;
        private List<String> context;
        private Map<String, Object> additionalMetadata;
        private String comments;
        private String name;
        private Map<String, String> customColumnKeyValues;
        private List<Turn> turns;
        private Integer datasetRank;
        private String datasetAlias;
        private String datasetId;
        private boolean multimodal;

        private Builder(String scenario) {
            this.scenario = scenario;
        }

        public Builder expectedOutcome(String expectedOutcome) {
            this.expectedOutcome = expectedOutcome;
            return this;
        }

        public Builder userDescription(String userDescription) {
            this.userDescription = userDescription;
            return this;
        }

        public Builder context(List<String> context) {
            this.context = context;
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

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder customColumnKeyValues(Map<String, String> customColumnKeyValues) {
            this.customColumnKeyValues = customColumnKeyValues;
            return this;
        }

        public Builder turns(List<Turn> turns) {
            this.turns = turns;
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

        public ConversationalGolden build() {
            return new ConversationalGolden(scenario, expectedOutcome, userDescription, context,
                    additionalMetadata, comments, name, customColumnKeyValues, turns,
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
        if (values == null) {
            return false;
        }
        return values.stream().anyMatch(value -> {
            if (value instanceof String string) {
                return containsPlaceholder(string);
            }
            if (value instanceof RetrievedContextData data) {
                return containsPlaceholder(data.context());
            }
            return value instanceof Turn turn && containsPlaceholder(turn);
        });
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
