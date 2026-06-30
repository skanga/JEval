package dev.jeval.simulator;

import com.fasterxml.jackson.annotation.JsonAlias;

public record ConversationCompletion(
        @JsonAlias("is_complete") boolean isComplete,
        String reason) {
}
