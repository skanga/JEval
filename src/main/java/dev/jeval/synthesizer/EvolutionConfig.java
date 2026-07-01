package dev.jeval.synthesizer;

import java.util.List;

public record EvolutionConfig(int numEvolutions, List<Evolution> evolutions) {
    public EvolutionConfig {
        if (numEvolutions < 0) {
            throw new IllegalArgumentException("numEvolutions must be non-negative");
        }
        if (evolutions != null && evolutions.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("evolutions must not contain null values");
        }
        evolutions = evolutions == null || evolutions.isEmpty() ? List.of(Evolution.REASONING) : List.copyOf(evolutions);
    }

    public EvolutionConfig() {
        this(1, List.of(
                Evolution.REASONING,
                Evolution.MULTICONTEXT,
                Evolution.CONCRETIZING,
                Evolution.CONSTRAINED,
                Evolution.COMPARATIVE,
                Evolution.HYPOTHETICAL,
                Evolution.IN_BREADTH));
    }
}
