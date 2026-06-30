package dev.jeval.simulator;

import dev.jeval.Turn;

public record TurnEmission(
        Turn turn,
        boolean end) {
}
