package dev.jeval;

public final class ConversationalEvaluationAssertionError extends AssertionError {
    private final ConversationalEvaluationResult result;

    public ConversationalEvaluationAssertionError(ConversationalEvaluationResult result) {
        super(result.failureMessage());
        this.result = result;
    }

    public ConversationalEvaluationResult result() {
        return result;
    }
}
