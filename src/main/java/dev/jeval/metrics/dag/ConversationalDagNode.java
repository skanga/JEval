package dev.jeval.metrics.dag;

import java.util.ArrayList;
import java.util.List;

public abstract class ConversationalDagNode {
    private final List<ConversationalDagNode> parents = new ArrayList<>();
    private int indegree;

    public List<ConversationalDagNode> parents() {
        return List.copyOf(parents);
    }

    public int indegree() {
        return indegree;
    }

    final void addParent(ConversationalDagNode parent) {
        parents.add(parent);
        incrementIndegree();
    }

    final void incrementIndegree() {
        indegree++;
    }
}
