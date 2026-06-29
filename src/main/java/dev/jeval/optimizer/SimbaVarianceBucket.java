package dev.jeval.optimizer;

import java.util.List;
import java.util.Objects;

public record SimbaVarianceBucket(
        Object golden,
        List<SimbaTraceRecord> traces,
        double maxToAvgGap,
        double maxScore,
        double minScore) {

    public SimbaVarianceBucket {
        traces = List.copyOf(Objects.requireNonNull(traces, "traces"));
    }
}
