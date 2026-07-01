package dev.jeval;

import java.util.List;
import java.util.regex.Pattern;

public record RetrievedContextData(String context, String source) {
    private static final Pattern MARKER = Pattern.compile("^deepeval_source=(.*?),deepeval_context=(.*)$");

    public RetrievedContextData {
        if (context == null) {
            throw new IllegalArgumentException("'context' must be a string");
        }
        if (source == null) {
            throw new IllegalArgumentException("'source' must be a string");
        }
    }

    public String marker() {
        return "deepeval_source=" + source + ",deepeval_context=" + context;
    }

    public String modelDump() {
        return toString();
    }

    public static Object fromMarker(String value) {
        var matcher = MARKER.matcher(value);
        return matcher.matches() ? new RetrievedContextData(matcher.group(2), matcher.group(1)) : value;
    }

    public static List<Object> values(List<?> values) {
        if (values == null) {
            return null;
        }
        return values.stream().map(value -> {
            if (value instanceof String text) {
                return fromMarker(text);
            }
            if (value instanceof RetrievedContextData) {
                return value;
            }
            throw new IllegalArgumentException("'retrieval_context' must contain strings or RetrievedContextData");
        }).toList();
    }

    public static List<String> textValues(List<?> values) {
        return values == null || values.isEmpty() ? null : values.stream().map(RetrievedContextData::textValue).toList();
    }

    public static List<String> markerValues(List<?> values) {
        return values == null ? null : values.stream().map(RetrievedContextData::markerValue).toList();
    }

    public static List<String> modelDumpValues(List<?> values) {
        return values == null ? null : values.stream().map(RetrievedContextData::modelDumpValue).toList();
    }

    public static String textValue(Object value) {
        return value instanceof RetrievedContextData data ? data.context() : (String) value;
    }

    public static String markerValue(Object value) {
        return value instanceof RetrievedContextData data ? data.marker() : (String) value;
    }

    public static String modelDumpValue(Object value) {
        return value instanceof RetrievedContextData data ? data.modelDump() : (String) value;
    }

    @Override
    public String toString() {
        return source + ": " + context;
    }
}
