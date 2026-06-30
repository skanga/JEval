package dev.jeval.simulator;

import dev.jeval.ConversationalGolden;
import dev.jeval.Turn;
import java.util.List;

public record SimulationContext(
        List<Turn> turns,
        ConversationalGolden golden,
        int index,
        String threadId,
        int simulatedUserTurns,
        int maxUserSimulations,
        Turn lastUserTurn,
        Turn lastAssistantTurn,
        String language) {

    public SimulationContext {
        turns = turns == null ? List.of() : List.copyOf(turns);
        language = language == null || language.isBlank() ? "English" : language;
    }

    public SimulationContext(
            List<Turn> turns,
            ConversationalGolden golden,
            int index,
            String threadId,
            int simulatedUserTurns,
            int maxUserSimulations,
            Turn lastUserTurn,
            Turn lastAssistantTurn) {
        this(turns, golden, index, threadId, simulatedUserTurns, maxUserSimulations,
                lastUserTurn, lastAssistantTurn, "English");
    }
}
