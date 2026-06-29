package dev.jeval.synthesizer;

public record SynthesizerOptions(boolean asyncMode, int maxConcurrent, boolean costTracking) {
    public static final SynthesizerOptions DEFAULT = new SynthesizerOptions(true, 100, false);

    public SynthesizerOptions {
        if (maxConcurrent < 1) {
            throw new IllegalArgumentException("max_concurrent must be at least 1");
        }
    }
}
