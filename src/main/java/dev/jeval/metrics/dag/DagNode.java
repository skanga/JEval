package dev.jeval.metrics.dag;

import java.util.ArrayList;
import java.util.List;

public abstract class DagNode {
    private final List<DagNode> parents = new ArrayList<>();
    private int indegree;

    public List<DagNode> parents() {
        return List.copyOf(parents);
    }

    public int indegree() {
        return indegree;
    }

    final void addParent(DagNode parent) {
        parents.add(parent);
        incrementIndegree();
    }

    final void incrementIndegree() {
        indegree++;
    }
}
