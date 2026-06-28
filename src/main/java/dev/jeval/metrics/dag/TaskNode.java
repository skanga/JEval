package dev.jeval.metrics.dag;

import dev.jeval.SingleTurnParam;
import java.util.List;

public final class TaskNode extends DagNode {
    private final String instructions;
    private final String outputLabel;
    private final List<DagNode> children;
    private final List<SingleTurnParam> evaluationParams;
    private final String label;

    public TaskNode(String instructions, String outputLabel, List<? extends DagNode> children) {
        this(instructions, outputLabel, children, List.of(), null);
    }

    public TaskNode(
            String instructions,
            String outputLabel,
            List<? extends DagNode> children,
            List<SingleTurnParam> evaluationParams,
            String label) {
        if (children.stream().anyMatch(VerdictNode.class::isInstance)) {
            throw new IllegalArgumentException("TaskNode children cannot be VerdictNode");
        }
        this.instructions = instructions;
        this.outputLabel = outputLabel;
        this.children = List.copyOf(children);
        this.evaluationParams = List.copyOf(evaluationParams);
        this.label = label;
        this.children.forEach(child -> child.addParent(this));
    }

    public String instructions() {
        return instructions;
    }

    public String outputLabel() {
        return outputLabel;
    }

    public List<DagNode> children() {
        return children;
    }

    public List<SingleTurnParam> evaluationParams() {
        return evaluationParams;
    }

    public String label() {
        return label;
    }
}
