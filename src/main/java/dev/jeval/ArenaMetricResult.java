package dev.jeval;

public record ArenaMetricResult(String name, String winner, boolean success, String reason) {
    public ArenaMetricResult {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("ArenaMetricResult name is required");
        }
        if (winner == null || winner.isBlank()) {
            throw new IllegalArgumentException("ArenaMetricResult winner is required");
        }
    }
}
