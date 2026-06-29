package dev.jeval.optimizer.algorithms;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record MIPROV2DemonstrationSet(List<MIPROV2Demonstration> demonstrations, String id) {

    public MIPROV2DemonstrationSet(List<MIPROV2Demonstration> demonstrations) {
        this(demonstrations, UUID.randomUUID().toString());
    }

    public MIPROV2DemonstrationSet {
        demonstrations = List.copyOf(Objects.requireNonNull(demonstrations, "demonstrations"));
        id = (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id;
    }

    public String toText() {
        return toText(0);
    }

    public String toText(int maxDemonstrations) {
        var demosToUse = maxDemonstrations > 0
                ? demonstrations.subList(0, Math.min(maxDemonstrations, demonstrations.size()))
                : demonstrations;
        if (demosToUse.isEmpty()) {
            return "";
        }

        var lines = new StringBuilder();
        lines.append("Here are some examples:\n\n");
        for (var i = 0; i < demosToUse.size(); i++) {
            var demo = demosToUse.get(i);
            lines.append("Example ").append(i + 1).append(":\n");
            lines.append("Input: ").append(demo.inputText()).append("\n");
            lines.append("Output: ").append(demo.outputText()).append("\n\n\n");
        }
        lines.append("Now, please respond to the following:");
        return lines.toString();
    }
}
