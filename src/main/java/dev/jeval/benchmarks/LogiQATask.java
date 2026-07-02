package dev.jeval.benchmarks;

public enum LogiQATask {
    CATEGORICAL_REASONING("Categorical Reasoning"),
    SUFFICIENT_CONDITIONAL_REASONING("Sufficient Conditional Reasoning"),
    NECESSARY_CONDITIONAL_REASONING("Necessary Conditional Reasoning"),
    DISJUNCTIVE_REASONING("Disjunctive Reasoning"),
    CONJUNCTIVE_REASONING("Conjunctive Reasoning");

    private final String value;

    LogiQATask(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
