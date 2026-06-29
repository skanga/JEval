package dev.jeval.synthesizer;

public record ConversationalStylingConfig(
        String scenarioContext,
        String conversationalTask,
        String participantRoles,
        String expectedOutcomeFormat) {
}
