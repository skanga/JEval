package dev.jeval.metrics.dag;

import dev.jeval.SingleTurnParam;
import java.util.List;

public final class BinaryJudgementNode extends DagNode {
    private final String criteria;
    private final List<VerdictNode> children;
    private final List<SingleTurnParam> evaluationParams;
    private final String label;

    public BinaryJudgementNode(String criteria, List<VerdictNode> children) {
        this(criteria, children, List.of());
    }

    public BinaryJudgementNode(String criteria, List<VerdictNode> children, List<SingleTurnParam> evaluationParams) {
        this(criteria, children, evaluationParams, null);
    }

    public BinaryJudgementNode(
            String criteria,
            List<VerdictNode> children,
            List<SingleTurnParam> evaluationParams,
            String label) {
        if (children.size() != 2) {
            throw new IllegalArgumentException("BinaryJudgementNode requires exactly two verdict children");
        }
        if (!(children.get(0).verdict() instanceof Boolean) || !(children.get(1).verdict() instanceof Boolean)) {
            throw new IllegalArgumentException("BinaryJudgementNode verdicts must be boolean");
        }
        if (children.get(0).verdict().equals(children.get(1).verdict())) {
            throw new IllegalArgumentException("BinaryJudgementNode requires one true and one false verdict");
        }
        this.criteria = criteria;
        this.children = List.copyOf(children);
        this.evaluationParams = List.copyOf(evaluationParams);
        this.label = label;
        this.children.forEach(child -> {
            child.addParent(this);
            if (child.child() != null) {
                child.child().incrementIndegree();
            }
        });
    }

    public String criteria() {
        return criteria;
    }

    public List<VerdictNode> children() {
        return children;
    }

    public List<SingleTurnParam> evaluationParams() {
        return evaluationParams;
    }

    public String label() {
        return label;
    }
}
