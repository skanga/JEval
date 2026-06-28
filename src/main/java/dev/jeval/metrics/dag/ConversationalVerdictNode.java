package dev.jeval.metrics.dag;

import dev.jeval.ConversationalMetric;

public final class ConversationalVerdictNode extends ConversationalDagNode {
    private final Object verdict;
    private final Integer score;
    private final ConversationalDagNode child;
    private final ConversationalMetric metric;

    public ConversationalVerdictNode(Object verdict, int score) {
        this(verdict, score, null, null);
    }

    public ConversationalVerdictNode(Object verdict, ConversationalMetric metric) {
        this(verdict, null, null, metric);
    }

    public ConversationalVerdictNode(Object verdict, Integer score, ConversationalDagNode child) {
        this(verdict, score, child, null);
    }

    private ConversationalVerdictNode(
            Object verdict,
            Integer score,
            ConversationalDagNode child,
            ConversationalMetric metric) {
        var set = (score == null ? 0 : 1) + (child == null ? 0 : 1) + (metric == null ? 0 : 1);
        if (set != 1) {
            throw new IllegalArgumentException(
                    "ConversationalVerdictNode requires exactly one of score, child, or metric");
        }
        if (score != null && (score < 0 || score > 10)) {
            throw new IllegalArgumentException("score must be between 0 and 10");
        }
        this.verdict = verdict;
        this.score = score;
        this.child = child;
        this.metric = metric;
    }

    public Object verdict() {
        return verdict;
    }

    public Integer score() {
        return score;
    }

    public ConversationalDagNode child() {
        return child;
    }

    public ConversationalMetric metric() {
        return metric;
    }

    public ConversationalDagNode parent() {
        return parents().isEmpty() ? null : parents().getFirst();
    }
}
