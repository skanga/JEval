package dev.jeval.simulator;

import com.fasterxml.jackson.annotation.JsonAlias;

public record SimulatedInput(
        @JsonAlias("simulated_input") String simulatedInput) {
}
