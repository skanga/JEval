package dev.jeval.prompt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class Prompt {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private String alias;
    private String textTemplate;
    private List<PromptMessage> messagesTemplate;
    private ModelSettings modelSettings;
    private OutputType outputType;
    private OutputSchema outputSchema;
    private PromptType type;
    private PromptInterpolationType interpolationType = PromptInterpolationType.FSTRING;
    private String confidentApiKey;
    private String branch;

    public Prompt() {
    }

    public Prompt(String alias, String textTemplate) {
        this(alias, textTemplate, null, null);
    }

    public Prompt(String alias, List<PromptMessage> messagesTemplate, PromptInterpolationType interpolationType) {
        this(alias, null, messagesTemplate, interpolationType);
    }

    public Prompt(
            String alias,
            String textTemplate,
            List<PromptMessage> messagesTemplate,
            PromptInterpolationType interpolationType) {
        this(alias, textTemplate, messagesTemplate, null, null, null, interpolationType, null, null);
    }

    public Prompt(
            String alias,
            String textTemplate,
            List<PromptMessage> messagesTemplate,
            ModelSettings modelSettings,
            OutputType outputType,
            OutputSchema outputSchema,
            PromptInterpolationType interpolationType,
            String confidentApiKey,
            String branch) {
        if (textTemplate != null && messagesTemplate != null) {
            throw new IllegalArgumentException(
                    "Unable to create Prompt where 'text_template' and 'messages_template' are both provided. Please provide only one to continue.");
        }
        this.alias = alias;
        this.modelSettings = modelSettings;
        this.outputType = outputType;
        this.outputSchema = outputSchema;
        this.interpolationType = interpolationType == null ? PromptInterpolationType.FSTRING : interpolationType;
        this.confidentApiKey = confidentApiKey;
        this.branch = branch;
        if (textTemplate != null) {
            setText(textTemplate);
        } else if (messagesTemplate != null) {
            this.messagesTemplate = List.copyOf(messagesTemplate);
            this.type = PromptType.LIST;
        }
    }

    public Object load(Path file) throws IOException {
        return load(file, null);
    }

    public Object load(Path file, String messagesKey) throws IOException {
        var fileName = file.getFileName().toString();
        if (!fileName.endsWith(".json") && !fileName.endsWith(".txt")) {
            throw new IllegalArgumentException("Only .json and .txt files are supported");
        }

        alias = aliasFrom(fileName);
        var content = Files.readString(file);
        JsonNode node;
        try {
            node = MAPPER.readTree(content);
        } catch (Exception ignored) {
            setText(content);
            return content;
        }

        if (node.isObject()) {
            if (messagesKey == null) {
                throw new IllegalArgumentException("messages `key` must be provided if file is a dictionary");
            }
            node = node.get(messagesKey);
            if (node == null) {
                throw new IllegalArgumentException("Missing messages key: " + messagesKey);
            }
        }

        if (!node.isArray()) {
            setText(content);
            return content;
        }

        var messages = messages(node);
        if (messages == null) {
            setText(content);
            return content;
        }
        messagesTemplate = List.copyOf(messages);
        textTemplate = null;
        type = PromptType.LIST;
        return messagesTemplate;
    }

    public Object interpolate(java.util.Map<String, ?> values) {
        if (type == PromptType.TEXT) {
            if (textTemplate == null) {
                throw new IllegalStateException(
                        "Unable to interpolate empty prompt template. Please pull a prompt from Confident AI or set template manually to continue.");
            }
            return PromptInterpolation.interpolateText(interpolationType, textTemplate, values);
        }
        if (type == PromptType.LIST) {
            if (messagesTemplate == null) {
                throw new IllegalStateException(
                        "Unable to interpolate empty prompt template messages. Please pull a prompt from Confident AI or set template manually to continue.");
            }
            return messagesTemplate.stream()
                    .map(message -> new PromptMessage(
                            message.role(),
                            PromptInterpolation.interpolateText(interpolationType, message.content(), values)))
                    .toList();
        }
        throw new IllegalStateException("Unsupported prompt type: " + type);
    }

    public String alias() {
        return alias;
    }

    public String textTemplate() {
        return textTemplate;
    }

    public List<PromptMessage> messagesTemplate() {
        return messagesTemplate;
    }

    public PromptInterpolationType interpolationType() {
        return interpolationType;
    }

    public ModelSettings modelSettings() {
        return modelSettings;
    }

    public OutputType outputType() {
        return outputType;
    }

    public OutputSchema outputSchema() {
        return outputSchema;
    }

    public String confidentApiKey() {
        return confidentApiKey;
    }

    public String branch() {
        return branch;
    }

    public PromptType type() {
        return type;
    }

    private static String aliasFrom(String fileName) {
        var dot = fileName.lastIndexOf('.');
        return dot == -1 ? fileName : fileName.substring(0, dot);
    }

    private static List<PromptMessage> messages(JsonNode array) {
        var messages = new ArrayList<PromptMessage>();
        for (var node : array) {
            var role = node.get("role");
            var content = node.get("content");
            if (role == null || content == null || !role.isTextual() || !content.isTextual()) {
                return null;
            }
            messages.add(new PromptMessage(role.asText(), content.asText()));
        }
        return messages;
    }

    private void setText(String content) {
        textTemplate = content;
        messagesTemplate = null;
        type = PromptType.TEXT;
    }
}
