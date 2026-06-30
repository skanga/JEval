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
        Turn lastAssistantTurn) {

    public SimulationContext {
        turns = turns == null ? List.of() : List.copyOf(turns);
    }
}
