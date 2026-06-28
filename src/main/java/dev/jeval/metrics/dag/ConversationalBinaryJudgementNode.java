package dev.jeval.metrics.dag;

import dev.jeval.MultiTurnParam;
import java.util.List;

public final class ConversationalBinaryJudgementNode extends ConversationalDagNode {
    private final String criteria;
    private final List<ConversationalVerdictNode> children;
    private final List<MultiTurnParam> evaluationParams;
    private final TurnWindow turnWindow;
    private final String label;

    public ConversationalBinaryJudgementNode(String criteria, List<ConversationalVerdictNode> children) {
        this(criteria, children, List.of(), null, null);
    }

    public ConversationalBinaryJudgementNode(
            String criteria,
            List<ConversationalVerdictNode> children,
            List<MultiTurnParam> evaluationParams,
            TurnWindow turnWindow,
            String label) {
        if (children.size() != 2) {
            throw new IllegalArgumentException("ConversationalBinaryJudgementNode requires exactly two verdict children");
        }
        if (!(children.get(0).verdict() instanceof Boolean) || !(children.get(1).verdict() instanceof Boolean)) {
            throw new IllegalArgumentException("ConversationalBinaryJudgementNode verdicts must be boolean");
        }
        if (children.get(0).verdict().equals(children.get(1).verdict())) {
            throw new IllegalArgumentException("ConversationalBinaryJudgementNode requires one true and one false verdict");
        }
        this.criteria = criteria;
        this.children = List.copyOf(children);
        this.evaluationParams = List.copyOf(evaluationParams);
        this.turnWindow = turnWindow;
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

    public List<ConversationalVerdictNode> children() {
        return children;
    }

    public List<MultiTurnParam> evaluationParams() {
        return evaluationParams;
    }

    public TurnWindow turnWindow() {
        return turnWindow;
    }

    public String label() {
        return label;
    }
}
