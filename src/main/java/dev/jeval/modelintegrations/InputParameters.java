package dev.jeval.modelintegrations;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record InputParameters(
        String model,
        String input,
        List<Map<String, Object>> tools,
        String instructions,
        List<Map<String, Object>> messages,
        Map<String, String> toolDescriptions) {

    public InputParameters() {
        this(null, null, null, null, null, null);
    }

    public InputParameters {
        tools = copyMaps(tools);
        messages = copyMaps(messages);
        toolDescriptions = toolDescriptions == null
                ? null
                : Collections.unmodifiableMap(new LinkedHashMap<>(toolDescriptions));
    }

    private static List<Map<String, Object>> copyMaps(List<Map<String, Object>> values) {
        return values == null
                ? null
                : values.stream()
                        .map(value -> Collections.unmodifiableMap(new LinkedHashMap<>(value)))
                        .toList();
    }
}
