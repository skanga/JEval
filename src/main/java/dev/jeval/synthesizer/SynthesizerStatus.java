package dev.jeval.synthesizer;

public enum SynthesizerStatus {
    SUCCESS("success"),
    FAILURE("failure"),
    WARNING("warning");

    private final String value;

    SynthesizerStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
