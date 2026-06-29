package dev.jeval.optimizer;

public enum RunnerStatusType {
    PROGRESS("progress"),
    TIE("tie"),
    ERROR("error");

    private final String value;

    RunnerStatusType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
