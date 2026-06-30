package dev.jeval;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ToolCall {
    private final String name;
    private final String description;
    private final String reasoning;
    private final Map<String, Object> inputParameters;
    private final Object output;

    public ToolCall(String name) {
        this(name, null, null, null, null);
    }

    public ToolCall(String name, Map<?, ?> inputParameters, Object output) {
        this(name, null, null, inputParameters, output);
    }

    @JsonCreator
    public ToolCall(
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("reasoning") String reasoning,
            @JsonProperty("inputParameters") @JsonAlias("input_parameters") Map<?, ?> inputParameters,
            @JsonProperty("output") Object output) {
        if (name == null) {
            throw new IllegalArgumentException("'name' is required");
        }
        this.name = name;
        this.description = description;
        this.reasoning = reasoning;
        this.inputParameters = copyInputParameters(inputParameters);
        this.output = output;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public String reasoning() {
        return reasoning;
    }

    public Map<String, Object> inputParameters() {
        return inputParameters;
    }

    public Object output() {
        return output;
    }

    @JsonValue
    public Map<String, Object> modelDump() {
        return modelDump(false);
    }

    public Map<String, Object> modelDump(boolean byAlias) {
        var dump = new LinkedHashMap<String, Object>();
        dump.put("name", name);
        dump.put("description", description);
        dump.put("reasoning", reasoning);
        dump.put(byAlias ? "inputParameters" : "input_parameters", inputParameters);
        dump.put("output", output);
        return dump;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ToolCall toolCall
                && Objects.equals(name, toolCall.name)
                && Objects.equals(inputParameters, toolCall.inputParameters)
                && Objects.equals(output, toolCall.output);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, inputParameters, output);
    }

    @Override
    public String toString() {
        var fields = new ArrayList<String>();
        if (name != null) {
            fields.add("name=\"" + name + "\"");
        }
        if (description != null) {
            fields.add("description=\"" + description + "\"");
        }
        if (reasoning != null) {
            fields.add("reasoning=\"" + reasoning + "\"");
        }
        if (inputParameters != null && !inputParameters.isEmpty()) {
            fields.add(indentNestedField("input_parameters", prettyJson(inputParameters, 0)));
        }
        if (output != null) {
            fields.add(output instanceof Map<?, ?>
                    ? indentNestedField("output", prettyJson(output, 0))
                    : "output=" + formatValue(output));
        }
        return "ToolCall(\n    " + String.join(",\n    ", fields) + "\n)";
    }

    private static String formatValue(Object value) {
        if (value == null) {
            return "None";
        }
        if (value instanceof String text) {
            var escaped = text
                    .replace("\\", "\\\\")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
            if (text.contains("'") && !text.contains("\"")) {
                return "\"" + escaped.replace("\"", "\\\"") + "\"";
            }
            return "'" + escaped.replace("'", "\\'") + "'";
        }
        if (value instanceof Boolean bool) {
            return bool ? "True" : "False";
        }
        if (value instanceof Map<?, ?> map) {
            var fields = new ArrayList<String>();
            for (var entry : map.entrySet()) {
                fields.add(formatValue(entry.getKey()) + ": " + formatValue(entry.getValue()));
            }
            return "{" + String.join(", ", fields) + "}";
        }
        if (value instanceof Iterable<?> values) {
            var items = new ArrayList<String>();
            values.forEach(item -> items.add(formatValue(item)));
            return "[" + String.join(", ", items) + "]";
        }
        return String.valueOf(value);
    }

    private static String indentNestedField(String fieldName, String formatted) {
        var lines = formatted.split("\\R", -1);
        var value = new StringBuilder(fieldName).append("=").append(lines[0]);
        for (var i = 1; i < lines.length; i++) {
            value.append("\n    ").append(lines[i]);
        }
        return value.toString();
    }

    private static String prettyJson(Object value, int indent) {
        if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                return "{}";
            }
            var fields = new ArrayList<String>();
            for (var entry : map.entrySet()) {
                fields.add(" ".repeat(indent + 4)
                        + Utils.serializeToJson(String.valueOf(entry.getKey()))
                        + ": "
                        + prettyJson(entry.getValue(), indent + 4));
            }
            return "{\n" + String.join(",\n", fields) + "\n" + " ".repeat(indent) + "}";
        }
        if (value instanceof Iterable<?> values) {
            var items = new ArrayList<String>();
            values.forEach(item -> items.add(" ".repeat(indent + 4) + prettyJson(item, indent + 4)));
            return items.isEmpty() ? "[]" : "[\n" + String.join(",\n", items) + "\n" + " ".repeat(indent) + "]";
        }
        return Utils.serializeToJson(value);
    }

    private static Map<String, Object> copyInputParameters(Map<?, ?> values) {
        if (values == null) {
            return null;
        }
        var copied = new LinkedHashMap<String, Object>();
        for (var entry : values.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException("'input_parameters' must contain string keys");
            }
            copied.put(key, entry.getValue());
        }
        return Collections.unmodifiableMap(copied);
    }
}
