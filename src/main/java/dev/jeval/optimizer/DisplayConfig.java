package dev.jeval.optimizer;

public record DisplayConfig(boolean showIndicator, boolean announceTies) {

    public DisplayConfig() {
        this(true, false);
    }
}
