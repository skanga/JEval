package dev.jeval.metrics.dag;

import dev.jeval.SingleTurnParam;
import java.util.HashSet;
import java.util.List;

public final class NonBinaryJudgementNode extends DagNode {
    private final String criteria;
    private final List<VerdictNode> children;
    private final List<SingleTurnParam> evaluationParams;
    private final String label;
    private final List<String> verdictOptions;

    public NonBinaryJudgementNode(String criteria, List<VerdictNode> children) {
        this(criteria, children, List.of());
    }

    public NonBinaryJudgementNode(String criteria, List<VerdictNode> children, List<SingleTurnParam> evaluationParams) {
        this(criteria, children, evaluationParams, null);
    }

    public NonBinaryJudgementNode(
            String criteria,
            List<VerdictNode> children,
            List<SingleTurnParam> evaluationParams,
            String label) {
        if (children.isEmpty()) {
            throw new IllegalArgumentException("NonBinaryJudgementNode requires verdict children");
        }
        var options = children.stream().map(VerdictNode::verdict).toList();
        if (options.stream().anyMatch(option -> !(option instanceof String))) {
            throw new IllegalArgumentException("NonBinaryJudgementNode verdicts must be strings");
        }
        if (new HashSet<>(options).size() != options.size()) {
            throw new IllegalArgumentException("NonBinaryJudgementNode verdicts must be unique");
        }
        this.criteria = criteria;
        this.children = List.copyOf(children);
        this.evaluationParams = List.copyOf(evaluationParams);
        this.label = label;
        this.verdictOptions = options.stream().map(String.class::cast).toList();
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

    public List<String> verdictOptions() {
        return verdictOptions;
    }
}
