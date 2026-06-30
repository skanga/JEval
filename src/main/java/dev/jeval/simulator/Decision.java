package dev.jeval.simulator;

import com.fasterxml.jackson.annotation.JsonAlias;

public record Decision(
        @JsonAlias("should_end") boolean shouldEnd,
        String reason) {
}
