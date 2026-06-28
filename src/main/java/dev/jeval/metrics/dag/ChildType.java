package dev.jeval.metrics.dag;

public enum ChildType {
    NODE("node"),
    GEVAL("geval"),
    METRIC("metric");

    private final String value;

    ChildType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
