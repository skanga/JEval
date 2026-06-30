package dev.jeval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Utils {
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .configure(JsonWriteFeature.ESCAPE_NON_ASCII, true)
            .build();
    private static final String DEFAULT_SUFFIX = "...";
    private static final String ASCII_PUNCTUATION = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~";
    private static final Pattern MULTIMODAL_PLACEHOLDER = Pattern.compile("\\[DEEPEVAL:(?:IMAGE|PDF):(.*?)]");
    private static final int LEN_TINY = 40;
    private static final int LEN_SHORT = 60;
    private static final int LEN_MEDIUM = 120;
    private static final int LEN_LONG = 240;

    private Utils() {
    }

    public static String shorten(Object text) {
        return shorten(text, lenLong());
    }

    public static String shorten(Object text, int maxLength) {
        return shorten(text, maxLength, DEFAULT_SUFFIX);
    }

    public static String shorten(Object text, int maxLength, String suffix) {
        if (text == null || maxLength <= 0) {
            return "";
        }
        var value = text.toString();
        if (value.length() <= maxLength) {
            return value;
        }
        var marker = suffix == null ? DEFAULT_SUFFIX : suffix;
        var cut = maxLength - marker.length();
        if (cut <= 0) {
            return marker.substring(0, maxLength);
        }
        return value.substring(0, cut) + marker;
    }

    public static String normalizeText(String text) {
        var withoutPunctuation = new StringBuilder();
        for (var character : text.toLowerCase().toCharArray()) {
            if (ASCII_PUNCTUATION.indexOf(character) == -1) {
                withoutPunctuation.append(character);
            }
        }
        return withoutPunctuation.toString()
                .replaceAll("\\b(a|an|the)\\b", " ")
                .replaceAll("\\s+", " ")
                .strip();
    }

    public static boolean isMissing(String text) {
        return text == null || text.strip().isEmpty();
    }

    public static int readEnvInt(String name, int defaultValue) {
        return readEnvInt(System.getenv(), name, defaultValue, null);
    }

    public static int readEnvInt(String name, int defaultValue, int minValue) {
        return readEnvInt(System.getenv(), name, defaultValue, minValue);
    }

    static int readEnvInt(Map<String, String> env, String name, int defaultValue, Integer minValue) {
        var raw = env.get(name);
        if (raw == null) {
            return defaultValue;
        }
        try {
            var value = Integer.parseInt(raw.strip());
            return minValue == null || value >= minValue ? value : defaultValue;
        } catch (NumberFormatException error) {
            return defaultValue;
        }
    }

    public static double readEnvFloat(String name, double defaultValue) {
        return readEnvFloat(System.getenv(), name, defaultValue, null);
    }

    public static double readEnvFloat(String name, double defaultValue, double minValue) {
        return readEnvFloat(System.getenv(), name, defaultValue, minValue);
    }

    static double readEnvFloat(Map<String, String> env, String name, double defaultValue, Double minValue) {
        var raw = env.get(name);
        if (raw == null) {
            return defaultValue;
        }
        try {
            var value = Double.parseDouble(raw.strip());
            return Double.isFinite(value) && (minValue == null || value >= minValue) ? value : defaultValue;
        } catch (NumberFormatException error) {
            return defaultValue;
        }
    }

    public static boolean checkIfMultimodal(String input) {
        return input != null && MULTIMODAL_PLACEHOLDER.matcher(input).find();
    }

    public static List<Object> convertToMultiModalArray(String input) {
        return MllmImage.parseMultimodalString(input);
    }

    public static List<Object> convertToMultiModalArray(List<String> input) {
        var parts = new ArrayList<>();
        for (var context : input) {
            parts.addAll(MllmImage.parseMultimodalString(context));
        }
        return List.copyOf(parts);
    }

    public static String formatErrorText(Throwable error) {
        return formatErrorText(error, false);
    }

    public static String formatErrorText(Throwable error, boolean withStack) {
        var text = error.getClass().getSimpleName() + ": " + error.getMessage();
        if (!withStack) {
            return text;
        }
        var stack = new StringWriter();
        error.printStackTrace(new PrintWriter(stack));
        return text + "\n" + stack;
    }

    public static <T> T requireParam(T param, String providerLabel, String envVarName, String paramHint) {
        if (param == null) {
            throw new DeepEvalException(providerLabel
                    + " is missing a required parameter. Set "
                    + envVarName
                    + " in your environment or pass "
                    + paramHint
                    + ".");
        }
        return param;
    }

    public static Class<?> requireDependency(String className, String providerLabel, String installHint) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException error) {
            var hint = installHint == null ? "Add it to the classpath." : installHint;
            throw new DeepEvalException(
                    providerLabel + " requires the `" + className + "` package. " + hint,
                    error);
        }
    }

    public static void deleteFileIfExists(Path filePath) {
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException ignored) {
            // DeepEval treats this helper as best-effort cleanup.
        }
    }

    public static String generateUuid() {
        return UUID.randomUUID().toString();
    }

    public static int lenTiny() {
        return LEN_TINY;
    }

    public static int lenShort() {
        return LEN_SHORT;
    }

    public static int lenMedium() {
        return LEN_MEDIUM;
    }

    public static int lenLong() {
        return LEN_LONG;
    }

    public static List<List<Double>> softmax(List<? extends List<? extends Number>> rows) {
        var normalized = new ArrayList<List<Double>>();
        for (var row : rows) {
            var max = row.stream()
                    .mapToDouble(Number::doubleValue)
                    .max()
                    .orElseThrow(() -> new IllegalArgumentException("softmax rows must not be empty"));
            var exps = new ArrayList<Double>();
            var sum = 0.0;
            for (var value : row) {
                var exp = Math.exp(value.doubleValue() - max);
                exps.add(exp);
                sum += exp;
            }
            var probabilities = new ArrayList<Double>();
            for (var exp : exps) {
                probabilities.add(exp / sum);
            }
            normalized.add(List.copyOf(probabilities));
        }
        return List.copyOf(normalized);
    }

    public static double cosineSimilarity(List<? extends Number> first, List<? extends Number> second) {
        if (first.size() != second.size()) {
            throw new IllegalArgumentException("Vectors must have the same length");
        }
        var dotProduct = 0.0;
        var firstNorm = 0.0;
        var secondNorm = 0.0;
        for (var i = 0; i < first.size(); i++) {
            var firstValue = first.get(i).doubleValue();
            var secondValue = second.get(i).doubleValue();
            dotProduct += firstValue * secondValue;
            firstNorm += firstValue * firstValue;
            secondNorm += secondValue * secondValue;
        }
        return dotProduct / (Math.sqrt(firstNorm) * Math.sqrt(secondNorm));
    }

    public static String camelToSnake(String name) {
        return name
                .replaceAll("(.)([A-Z][a-z]+)", "$1_$2")
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .toLowerCase();
    }

    public static Object convertKeysToSnakeCase(Object data) {
        if (data instanceof Map<?, ?> map) {
            var converted = new LinkedHashMap<String, Object>();
            for (var entry : map.entrySet()) {
                var key = String.valueOf(entry.getKey());
                var snakeKey = camelToSnake(key);
                var value = entry.getValue();
                if ("additionalMetadata".equals(key) || "metadata".equals(key)) {
                    converted.put(snakeKey, value);
                } else {
                    converted.put(snakeKey, convertKeysToSnakeCase(value));
                }
            }
            return converted;
        }
        if (data instanceof List<?> list) {
            var converted = new ArrayList<>();
            for (var item : list) {
                converted.add(convertKeysToSnakeCase(item));
            }
            return converted;
        }
        return data;
    }

    public static String prettifyList(List<?> values) {
        if (values.isEmpty()) {
            return "[]";
        }
        var formatted = new ArrayList<String>();
        for (var value : values) {
            formatted.add(prettyValue(value));
        }
        return "[\n    " + String.join(",\n    ", formatted) + "\n]";
    }

    private static String prettyValue(Object value) {
        if (value instanceof String text) {
            return "\"" + text + "\"";
        }
        if (value instanceof Boolean bool) {
            return bool ? "True" : "False";
        }
        if (value instanceof Map<?, ?> map) {
            var entries = new ArrayList<String>();
            for (var entry : map.entrySet()) {
                entries.add(pythonRepr(entry.getKey()) + ": " + pythonRepr(entry.getValue()));
            }
            return "{" + String.join(", ", entries) + "}";
        }
        return value == null ? "None" : String.valueOf(value);
    }

    private static String pythonRepr(Object value) {
        if (value instanceof String text) {
            return "'" + text + "'";
        }
        return prettyValue(value);
    }

    public static <T> List<T> getLcs(List<T> first, List<T> second) {
        var lengths = new int[first.size() + 1][second.size() + 1];
        for (var i = 1; i <= first.size(); i++) {
            for (var j = 1; j <= second.size(); j++) {
                if (first.get(i - 1).equals(second.get(j - 1))) {
                    lengths[i][j] = lengths[i - 1][j - 1] + 1;
                } else {
                    lengths[i][j] = Math.max(lengths[i - 1][j], lengths[i][j - 1]);
                }
            }
        }

        var lcs = new ArrayList<T>();
        var i = first.size();
        var j = second.size();
        while (i > 0 && j > 0) {
            if (first.get(i - 1).equals(second.get(j - 1))) {
                lcs.addFirst(first.get(i - 1));
                i--;
                j--;
            } else if (lengths[i - 1][j] > lengths[i][j - 1]) {
                i--;
            } else {
                j--;
            }
        }
        return List.copyOf(lcs);
    }

    public static List<String> chunkText(String text, int chunkSize) {
        return chunkText(text, chunkSize, 0);
    }

    public static List<String> chunkText(String text, int chunkSize, int chunkOverlap) {
        if (chunkSize < 0) {
            return List.of();
        }
        if (chunkSize == 0) {
            throw new IllegalArgumentException("chunkSize must not be zero");
        }
        if (chunkOverlap < 0) {
            throw new IllegalArgumentException("chunkOverlap must not be negative");
        }
        if (chunkOverlap >= chunkSize) {
            throw new IllegalArgumentException("chunkOverlap must be less than chunkSize");
        }
        var words = text.split("\\s+");
        var chunks = new ArrayList<String>();
        var step = chunkSize - chunkOverlap;
        for (var i = 0; i < words.length; i += step) {
            if (i > 0 && words.length - i <= chunkOverlap) {
                break;
            }
            chunks.add(String.join(" ", List.of(words).subList(i, Math.min(i + chunkSize, words.length))));
        }
        return List.copyOf(chunks);
    }

    public static List<String> chunkText(String text) {
        return chunkText(text, 20);
    }

    public static Object cleanNestedDict(Object data) {
        if (data instanceof Map<?, ?> map) {
            var cleaned = new LinkedHashMap<Object, Object>();
            for (var entry : map.entrySet()) {
                cleaned.put(entry.getKey(), cleanNestedDict(entry.getValue()));
            }
            return cleaned;
        }
        if (data instanceof List<?> list) {
            var cleaned = new ArrayList<>();
            for (var item : list) {
                cleaned.add(cleanNestedDict(item));
            }
            return cleaned;
        }
        if (data instanceof String text) {
            return text.replace("\u0000", "");
        }
        return data;
    }

    public static String serialize(Object data) {
        try {
            return lowercaseUnicodeEscapes(writePythonJson(serializeWithSorting(data)));
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Unable to serialize value.", error);
        }
    }

    public static String serializeToJson(Object data) {
        try {
            return lowercaseUnicodeEscapes(MAPPER.writeValueAsString(makeJsonSerializable(data)));
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Unable to serialize value.", error);
        }
    }

    public static Object makeJsonSerializable(Object data) {
        return makeJsonSerializable(data, new IdentityHashMap<>());
    }

    public static Object makeJsonSerializableForMetadata(Object data) {
        return makeJsonSerializable(data);
    }

    private static Object makeJsonSerializable(Object data, IdentityHashMap<Object, Boolean> seen) {
        if (data == null || data instanceof Boolean || data instanceof Integer || data instanceof Long) {
            return data;
        }
        if (data instanceof Float value) {
            return Float.isFinite(value) ? value : null;
        }
        if (data instanceof Double value) {
            return Double.isFinite(value) ? value : null;
        }
        if (data instanceof Number) {
            return data;
        }
        if (data instanceof String text) {
            return text.replace("\u0000", "");
        }
        if (seen.containsKey(data)) {
            return "<circular>";
        }
        seen.put(data, true);
        try {
            if (data.getClass().isArray()) {
                var serialized = new ArrayList<>();
                for (var i = 0; i < Array.getLength(data); i++) {
                    serialized.add(makeJsonSerializable(Array.get(data, i), seen));
                }
                return Collections.unmodifiableList(serialized);
            }
            if (data instanceof Iterable<?> list) {
                var serialized = new ArrayList<>();
                for (var item : list) {
                    serialized.add(makeJsonSerializable(item, seen));
                }
                return Collections.unmodifiableList(serialized);
            }
            if (data instanceof Map<?, ?> map) {
                var serialized = new LinkedHashMap<String, Object>();
                for (var entry : map.entrySet()) {
                    serialized.put(String.valueOf(entry.getKey()), makeJsonSerializable(entry.getValue(), seen));
                }
                return serialized;
            }
            if (data.getClass().isRecord()) {
                var serialized = new LinkedHashMap<String, Object>();
                for (var component : data.getClass().getRecordComponents()) {
                    serialized.put(component.getName(), makeJsonSerializable(component.getAccessor().invoke(data), seen));
                }
                return serialized;
            }
            return String.valueOf(data).replace("\u0000", "");
        } catch (ReflectiveOperationException error) {
            throw new IllegalArgumentException("Unable to serialize record value.", error);
        }
    }

    private static String writePythonJson(Object data) throws JsonProcessingException {
        if (data instanceof Map<?, ?> map) {
            var fields = new ArrayList<String>();
            for (var entry : map.entrySet()) {
                fields.add(MAPPER.writeValueAsString(String.valueOf(entry.getKey()))
                        + ": "
                        + writePythonJson(entry.getValue()));
            }
            return "{" + String.join(", ", fields) + "}";
        }
        if (data instanceof List<?> list) {
            var items = new ArrayList<String>();
            for (var item : list) {
                items.add(writePythonJson(item));
            }
            return "[" + String.join(", ", items) + "]";
        }
        return MAPPER.writeValueAsString(data);
    }

    private static Object serializeWithSorting(Object data) {
        if (data instanceof Map<?, ?> map) {
            var sorted = new TreeMap<String, Object>();
            for (var entry : map.entrySet()) {
                sorted.put(String.valueOf(entry.getKey()), serializeWithSorting(entry.getValue()));
            }
            return sorted;
        }
        if (data instanceof List<?> list) {
            var sorted = new ArrayList<>();
            for (var item : list) {
                sorted.add(serializeWithSorting(item));
            }
            sorted.sort(Comparator.comparing(Utils::serialize));
            return sorted;
        }
        return data;
    }

    private static String lowercaseUnicodeEscapes(String json) {
        return Pattern.compile("\\\\u[0-9A-Fa-f]{4}")
                .matcher(json)
                .replaceAll(match -> Matcher.quoteReplacement(match.group().toLowerCase(Locale.ROOT)));
    }

    public static <T> List<List<T>> batcher(List<T> values, int batchSize) {
        if (batchSize <= 0) {
            return values.isEmpty() ? List.of() : List.of(List.copyOf(values));
        }
        var batches = new ArrayList<List<T>>();
        for (var i = 0; i < values.size(); i += batchSize) {
            batches.add(List.copyOf(values.subList(i, Math.min(i + batchSize, values.size()))));
        }
        return List.copyOf(batches);
    }

    public static <T> List<List<T>> batcher(List<T> values) {
        return batcher(values, 4);
    }
}
