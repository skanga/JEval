package dev.jeval.metrics.dag;

import dev.jeval.MultiTurnParam;
import java.util.List;

public final class ConversationalTaskNode extends ConversationalDagNode {
    private final String instructions;
    private final String outputLabel;
    private final List<ConversationalDagNode> children;
    private final List<MultiTurnParam> evaluationParams;
    private final TurnWindow turnWindow;
    private final String label;

    public ConversationalTaskNode(String instructions, String outputLabel, List<? extends ConversationalDagNode> children) {
        this(instructions, outputLabel, children, List.of(), null, null);
    }

    public ConversationalTaskNode(
            String instructions,
            String outputLabel,
            List<? extends ConversationalDagNode> children,
            List<MultiTurnParam> evaluationParams,
            TurnWindow turnWindow,
            String label) {
        if (children.stream().anyMatch(ConversationalVerdictNode.class::isInstance)) {
            throw new IllegalArgumentException("ConversationalTaskNode children cannot be ConversationalVerdictNode");
        }
        this.instructions = instructions;
        this.outputLabel = outputLabel;
        this.children = List.copyOf(children);
        this.evaluationParams = List.copyOf(evaluationParams);
        this.turnWindow = turnWindow;
        this.label = label;
        this.children.forEach(child -> child.addParent(this));
    }

    public String instructions() {
        return instructions;
    }

    public String outputLabel() {
        return outputLabel;
    }

    public List<ConversationalDagNode> children() {
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
