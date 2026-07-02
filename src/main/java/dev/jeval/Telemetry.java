package dev.jeval;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class Telemetry {
    public static final String TELEMETRY_DATA_FILE = ".deepeval_telemetry.txt";

    private final Path directory;
    private final Map<String, String> env;

    public Telemetry() {
        this(Path.of(Constants.HIDDEN_DIR), System.getenv());
    }

    Telemetry(Path directory, Map<String, String> env) {
        this.directory = directory;
        this.env = env;
    }

    public Path path() {
        return directory.resolve(TELEMETRY_DATA_FILE);
    }

    public boolean telemetryOptOut() {
        return "true".equalsIgnoreCase(env.get("DEEPEVAL_TELEMETRY_OPT_OUT"));
    }

    public Map<String, String> readTelemetryFile() throws IOException {
        var data = new LinkedHashMap<String, String>();
        if (!Files.exists(path())) {
            return data;
        }
        for (var line : Files.readAllLines(path())) {
            var stripped = line.strip();
            var index = stripped.indexOf('=');
            data.put(index < 0 ? stripped : stripped.substring(0, index),
                    index < 0 ? "" : stripped.substring(index + 1));
        }
        return data;
    }

    public void writeTelemetryFile(Map<String, String> data) throws IOException {
        if (telemetryOptOut()) {
            return;
        }
        Files.createDirectories(directory);
        var lines = data.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .toList();
        Files.write(path(), lines);
    }

    public String getStatus() throws IOException {
        return readTelemetryFile().getOrDefault("DEEPEVAL_STATUS", "new");
    }

    public String getUniqueId() throws IOException {
        if (telemetryOptOut()) {
            return "telemetry-opted-out";
        }
        var data = new LinkedHashMap<>(readTelemetryFile());
        var uniqueId = data.get("DEEPEVAL_ID");
        if (uniqueId == null || uniqueId.isBlank()) {
            uniqueId = UUID.randomUUID().toString();
            data.put("DEEPEVAL_ID", uniqueId);
            data.put("DEEPEVAL_STATUS", "new");
        } else {
            data.put("DEEPEVAL_STATUS", "old");
        }
        writeTelemetryFile(data);
        return uniqueId;
    }

    public Feature getLastFeature() throws IOException {
        return Feature.fromValue(readTelemetryFile().get("DEEPEVAL_LAST_FEATURE"));
    }

    public void setLastFeature(Feature feature) throws IOException {
        var data = new LinkedHashMap<>(readTelemetryFile());
        data.put("DEEPEVAL_LAST_FEATURE", feature.value());
        data.put(featureStatusKey(feature), "old");
        writeTelemetryFile(data);
    }

    public String getFeatureStatus(Feature feature) throws IOException {
        return readTelemetryFile().getOrDefault(featureStatusKey(feature), "new");
    }

    private static String featureStatusKey(Feature feature) {
        return "DEEPEVAL_" + feature.value().toUpperCase(Locale.ROOT) + "_STATUS";
    }

    public void setLoggedInWith(String loggedInWith) throws IOException {
        var data = new LinkedHashMap<>(readTelemetryFile());
        data.put("LOGGED_IN_WITH", loggedInWith);
        writeTelemetryFile(data);
    }

    public String getLoggedInWith() throws IOException {
        return readTelemetryFile().getOrDefault("LOGGED_IN_WITH", "NA");
    }

    public enum Feature {
        REDTEAMING("redteaming"),
        SYNTHESIZER("synthesizer"),
        EVALUATION("evaluation"),
        COMPONENT_EVALUATION("component_evaluation"),
        GUARDRAIL("guardrail"),
        BENCHMARK("benchmark"),
        CONVERSATION_SIMULATOR("conversation_simulator"),
        UNKNOWN("unknown"),
        TRACING_INTEGRATION("tracing_integration");

        private final String value;

        Feature(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }

        static Feature fromValue(String value) {
            for (var feature : values()) {
                if (feature.value.equals(value)) {
                    return feature;
                }
            }
            return UNKNOWN;
        }
    }
}
