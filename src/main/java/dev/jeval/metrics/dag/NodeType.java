package dev.jeval.metrics.dag;

public enum NodeType {
    TASK("TaskNode"),
    BINARY_JUDGEMENT("BinaryJudgementNode"),
    NON_BINARY_JUDGEMENT("NonBinaryJudgementNode"),
    VERDICT("VerdictNode");

    private final String value;

    NodeType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
