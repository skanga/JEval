package dev.jeval.simulator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.jeval.ConversationalGolden;
import dev.jeval.EvaluationModel;
import dev.jeval.Turn;
import dev.jeval.metrics.MetricUtils;
import java.util.List;

public final class SimulationController {
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final ControllerFunction controller;
    private final EvaluationModel model;
    private final boolean expectedOutcomeController;

    private SimulationController(ControllerFunction controller, EvaluationModel model, boolean expectedOutcomeController) {
        this.controller = controller;
        this.model = model;
        this.expectedOutcomeController = expectedOutcomeController;
    }

    public static Decision proceed() {
        return new Decision(false, null);
    }

    public static Decision end(String reason) {
        return new Decision(true, reason);
    }

    public static SimulationController custom(ControllerFunction controller) {
        return new SimulationController(controller, null, false);
    }

    public static SimulationController expectedOutcome(EvaluationModel model) {
        return new SimulationController(null, model, true);
    }

    public boolean run(
            List<Turn> turns,
            ConversationalGolden golden,
            int index,
            String threadId,
            int simulationCounter,
            int maxUserSimulations) {
        return run(turns, golden, index, threadId, simulationCounter, maxUserSimulations, "English");
    }

    public boolean run(
            List<Turn> turns,
            ConversationalGolden golden,
            int index,
            String threadId,
            int simulationCounter,
            int maxUserSimulations,
            String language) {
        if (expectedOutcomeController) {
            return checkExpectedOutcome(turns, golden);
        }
        var decision = controller == null ? null : controller.apply(buildContext(
                turns, golden, index, threadId, simulationCounter, maxUserSimulations, language));
        return normalizeDecision(decision).shouldEnd();
    }

    public boolean checkExpectedOutcome(List<Turn> turns, ConversationalGolden golden) {
        if (golden.expectedOutcome() == null) {
            return false;
        }
        var prompt = SimulatorControllerTemplate.checkExpectedOutcome(
                serializeTurns(turns), golden.expectedOutcome());
        var completion = parseCompletion(model.generate(prompt));
        return completion.isComplete();
    }

    public SimulationContext buildContext(
            List<Turn> turns,
            ConversationalGolden golden,
            int index,
            String threadId,
            int simulationCounter,
            int maxUserSimulations) {
        return buildContext(turns, golden, index, threadId, simulationCounter, maxUserSimulations, "English");
    }

    public SimulationContext buildContext(
            List<Turn> turns,
            ConversationalGolden golden,
            int index,
            String threadId,
            int simulationCounter,
            int maxUserSimulations,
            String language) {
        return new SimulationContext(
                turns,
                golden,
                index,
                threadId,
                simulationCounter,
                maxUserSimulations,
                lastTurn(turns, "user"),
                lastTurn(turns, "assistant"),
                language);
    }

    private static Decision normalizeDecision(Decision decision) {
        return decision == null ? proceed() : decision;
    }

    private static ConversationCompletion parseCompletion(String json) {
        try {
            return JSON.treeToValue(MetricUtils.trimAndLoadJson(json), ConversationCompletion.class);
        } catch (Exception error) {
            throw new IllegalArgumentException("Unable to parse conversation completion JSON", error);
        }
    }

    private static String serializeTurns(List<Turn> turns) {
        try {
            var values = turns == null ? List.of() : turns.stream().map(turn -> turn.modelDump(false, false)).toList();
            return JSON.writeValueAsString(values);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Unable to serialize simulator turns", error);
        }
    }

    private static Turn lastTurn(List<Turn> turns, String role) {
        if (turns == null) {
            return null;
        }
        for (var index = turns.size() - 1; index >= 0; index--) {
            var turn = turns.get(index);
            if (role.equals(turn.role())) {
                return turn;
            }
        }
        return null;
    }

    @FunctionalInterface
    public interface ControllerFunction {
        Decision apply(SimulationContext context);
    }
}
