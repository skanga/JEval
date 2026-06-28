package dev.jeval.synthesizer;

public enum Evolution {
    REASONING("Reasoning"),
    MULTICONTEXT("Multi-context"),
    CONCRETIZING("Concretizing"),
    CONSTRAINED("Constrained"),
    COMPARATIVE("Comparative"),
    HYPOTHETICAL("Hypothetical"),
    IN_BREADTH("In-Breadth");

    private final String value;

    Evolution(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
