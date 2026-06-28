package dev.jeval;

public class ArenaEvaluationAssertionError extends AssertionError {
    private final ArenaEvaluationResult result;

    public ArenaEvaluationAssertionError(ArenaEvaluationResult result) {
        super(result.failureMessage());
        this.result = result;
    }

    public ArenaEvaluationResult result() {
        return result;
    }
}
