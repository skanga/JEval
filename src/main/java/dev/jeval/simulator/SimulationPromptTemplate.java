package dev.jeval.simulator;

import dev.jeval.ConversationalGolden;
import dev.jeval.Turn;
import java.util.List;

public interface SimulationPromptTemplate {
    String simulateFirstUserTurn(ConversationalGolden golden, String language);

    String simulateUserTurn(ConversationalGolden golden, List<Turn> turns, String language);
}
