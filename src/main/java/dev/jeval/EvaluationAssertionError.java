package dev.jeval;

public final class EvaluationAssertionError extends AssertionError {
    private final EvaluationResult result;

    public EvaluationAssertionError(EvaluationResult result) {
        super(result.failureMessage());
        this.result = result;
    }

    public EvaluationResult result() {
        return result;
    }
}
