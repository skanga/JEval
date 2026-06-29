package dev.jeval.optimizer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class OptimizerUtils {
    private OptimizerUtils() {
    }

    public static Map<String, PromptConfigSnapshot> buildPromptConfigSnapshots(
            Map<String, PromptConfiguration> promptConfigurationsById) {
        var snapshots = new LinkedHashMap<String, PromptConfigSnapshot>();
        for (var entry : Objects.requireNonNull(promptConfigurationsById, "promptConfigurationsById").entrySet()) {
            var config = entry.getValue();
            snapshots.put(entry.getKey(), new PromptConfigSnapshot(config.parent(), config.prompts()));
        }
        return Collections.unmodifiableMap(snapshots);
    }
}
