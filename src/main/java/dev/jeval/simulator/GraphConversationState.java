package dev.jeval.simulator;

import java.util.IdentityHashMap;
import java.util.Map;

public final class GraphConversationState {
    private SimulationNode current;
    private final Map<SimulationNode, Integer> visits = new IdentityHashMap<>();

    GraphConversationState(SimulationNode current) {
        this.current = current;
    }

    public SimulationNode current() {
        return current;
    }

    void current(SimulationNode current) {
        this.current = current;
    }

    int visits(SimulationNode node) {
        return visits.getOrDefault(node, 0);
    }

    void incrementVisits(SimulationNode node) {
        visits.put(node, visits(node) + 1);
    }
}
