package dev.jeval.metrics.dag;

import dev.jeval.Metric;

public final class VerdictNode extends DagNode {
    private final Object verdict;
    private final Integer score;
    private final DagNode child;
    private final Metric metric;

    public VerdictNode(Object verdict, int score) {
        this(verdict, score, null, null);
    }

    public VerdictNode(Object verdict, Metric metric) {
        this(verdict, null, null, metric);
    }

    public VerdictNode(Object verdict, Integer score, DagNode child) {
        this(verdict, score, child, null);
    }

    private VerdictNode(Object verdict, Integer score, DagNode child, Metric metric) {
        var set = (score == null ? 0 : 1) + (child == null ? 0 : 1) + (metric == null ? 0 : 1);
        if (set != 1) {
            throw new IllegalArgumentException("VerdictNode requires exactly one of score, child, or metric");
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

    public DagNode child() {
        return child;
    }

    public Metric metric() {
        return metric;
    }

    public DagNode parent() {
        return parents().isEmpty() ? null : parents().getFirst();
    }
}
