package dev.jeval.benchmarks;

public enum MathQATask {
    PROBABILITY("probability"),
    GEOMETRY("geometry"),
    PHYSICS("physics"),
    GAIN("gain"),
    GENERAL("general"),
    OTHER("other");

    private final String value;

    MathQATask(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
