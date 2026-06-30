package dev.jeval.runner;

public enum TestRunResultDisplay {
    ALL("all"),
    FAILING("failing"),
    PASSING("passing");

    private final String value;

    TestRunResultDisplay(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
