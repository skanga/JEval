package dev.jeval.synthesizer;

public record ConversationalStylingConfig(
        String scenarioContext,
        String conversationalTask,
        String participantRoles,
        String scenarioFormat,
        String expectedOutcomeFormat) {
    public ConversationalStylingConfig(
            String scenarioContext,
            String conversationalTask,
            String participantRoles,
            String expectedOutcomeFormat) {
        this(scenarioContext, conversationalTask, participantRoles, null, expectedOutcomeFormat);
    }
}
