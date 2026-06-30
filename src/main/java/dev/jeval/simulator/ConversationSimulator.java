package dev.jeval.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jeval.ConversationalGolden;
import dev.jeval.EvaluationModel;
import dev.jeval.Turn;
import java.util.List;
import java.util.Objects;

public final class ConversationSimulator {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ModelCallback modelCallback;
    private final EvaluationModel simulatorModel;
    private final String language;

    public ConversationSimulator(ModelCallback modelCallback, EvaluationModel simulatorModel) {
        this(modelCallback, simulatorModel, "English");
    }

    public ConversationSimulator(ModelCallback modelCallback, EvaluationModel simulatorModel, String language) {
        this.modelCallback = Objects.requireNonNull(modelCallback, "modelCallback");
        this.simulatorModel = Objects.requireNonNull(simulatorModel, "simulatorModel");
        this.language = language == null || language.isBlank() ? "English" : language;
    }

    public String language() {
        return language;
    }

    public String generateFirstUserInput(ConversationalGolden golden) {
        var prompt = SimulationTemplate.simulateFirstUserTurn(golden, language);
        return generateSchema(prompt).simulatedInput();
    }

    public String generateNextUserInput(ConversationalGolden golden, List<Turn> turns) {
        var prompt = SimulationTemplate.simulateUserTurn(golden, turns, language);
        return generateSchema(prompt).simulatedInput();
    }

    public SimulatedInput generateSchema(String prompt) {
        try {
            var input = JSON.readValue(simulatorModel.generate(prompt), SimulatedInput.class);
            if (input.simulatedInput() == null) {
                throw new IllegalArgumentException("Simulator response must contain `simulated_input`.");
            }
            return input;
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalArgumentException("Unable to parse simulated input JSON", error);
        }
    }

    public Turn generateTurnFromCallback(String input, List<Turn> turns, String threadId) {
        var turn = modelCallback.generate(new CallbackContext(input, turns, threadId));
        if (turn == null) {
            throw new IllegalArgumentException("model_callback must return a Turn.");
        }
        return turn;
    }

    @FunctionalInterface
    public interface ModelCallback {
        Turn generate(CallbackContext context);
    }

    public record CallbackContext(
            String input,
            List<Turn> turns,
            String threadId) {
        public CallbackContext {
            turns = turns == null ? List.of() : List.copyOf(turns);
        }
    }
}
