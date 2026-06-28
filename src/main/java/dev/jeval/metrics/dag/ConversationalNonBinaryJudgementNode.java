package dev.jeval.metrics.dag;

import dev.jeval.MultiTurnParam;
import java.util.HashSet;
import java.util.List;

public final class ConversationalNonBinaryJudgementNode extends ConversationalDagNode {
    private final String criteria;
    private final List<ConversationalVerdictNode> children;
    private final List<MultiTurnParam> evaluationParams;
    private final TurnWindow turnWindow;
    private final String label;
    private final List<String> verdictOptions;

    public ConversationalNonBinaryJudgementNode(String criteria, List<ConversationalVerdictNode> children) {
        this(criteria, children, List.of(), null, null);
    }

    public ConversationalNonBinaryJudgementNode(
            String criteria,
            List<ConversationalVerdictNode> children,
            List<MultiTurnParam> evaluationParams,
            TurnWindow turnWindow,
            String label) {
        if (children.isEmpty()) {
            throw new IllegalArgumentException("ConversationalNonBinaryJudgementNode requires verdict children");
        }
        var options = children.stream().map(ConversationalVerdictNode::verdict).toList();
        if (options.stream().anyMatch(option -> !(option instanceof String))) {
            throw new IllegalArgumentException("ConversationalNonBinaryJudgementNode verdicts must be strings");
        }
        if (new HashSet<>(options).size() != options.size()) {
            throw new IllegalArgumentException("ConversationalNonBinaryJudgementNode verdicts must be unique");
        }
        this.criteria = criteria;
        this.children = List.copyOf(children);
        this.evaluationParams = List.copyOf(evaluationParams);
        this.turnWindow = turnWindow;
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

    public List<String> verdictOptions() {
        return verdictOptions;
    }
}
