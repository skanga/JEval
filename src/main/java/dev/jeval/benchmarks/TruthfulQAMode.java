package dev.jeval.benchmarks;

public enum TruthfulQAMode {
    MC1("mc1"),
    MC2("mc2");

    private final String value;

    TruthfulQAMode(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
