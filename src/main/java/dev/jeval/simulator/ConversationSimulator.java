package dev.jeval.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jeval.ConversationalGolden;
import dev.jeval.ConversationalTestCase;
import dev.jeval.EvaluationModel;
import dev.jeval.Turn;
import dev.jeval.metrics.MetricUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class ConversationSimulator {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final SimulationPromptTemplate DEFAULT_TEMPLATE = new SimulationPromptTemplate() {
        @Override
        public String simulateFirstUserTurn(ConversationalGolden golden, String language) {
            return SimulationTemplate.simulateFirstUserTurn(golden, language);
        }

        @Override
        public String simulateUserTurn(ConversationalGolden golden, List<Turn> turns, String language) {
            return SimulationTemplate.simulateUserTurn(golden, turns, language);
        }
    };

    private final ModelCallback modelCallback;
    private final EvaluationModel simulatorModel;
    private final String language;
    private final SimulationGraphRunner graphRunner;
    private final SimulationController stoppingController;
    private List<ConversationalTestCase> simulatedConversations = List.of();

    public ConversationSimulator(ModelCallback modelCallback, EvaluationModel simulatorModel) {
        this(modelCallback, simulatorModel, "English");
    }

    public ConversationSimulator(ModelCallback modelCallback, EvaluationModel simulatorModel, String language) {
        this(modelCallback, simulatorModel, language, null, null);
    }

    public ConversationSimulator(
            ModelCallback modelCallback,
            EvaluationModel simulatorModel,
            String language,
            SimulationNode simulationGraph,
            SimulationController stoppingController) {
        this.modelCallback = Objects.requireNonNull(modelCallback, "modelCallback");
        this.simulatorModel = Objects.requireNonNull(simulatorModel, "simulatorModel");
        this.language = language == null || language.isBlank() ? "English" : language;
        this.graphRunner = new SimulationGraphRunner(
                simulationGraph == null ? defaultSimulationNode() : simulationGraph);
        this.stoppingController = stoppingController == null
                ? SimulationController.expectedOutcome(simulatorModel)
                : stoppingController;
    }

    public static SimulationNode defaultSimulationNode() {
        return defaultSimulationNode(DEFAULT_TEMPLATE, false);
    }

    public static SimulationNode defaultSimulationNode(boolean terminal) {
        return defaultSimulationNode(DEFAULT_TEMPLATE, terminal);
    }

    public static SimulationNode defaultSimulationNode(SimulationPromptTemplate template) {
        return defaultSimulationNode(template, false);
    }

    public static SimulationNode defaultSimulationNode(SimulationPromptTemplate template, boolean terminal) {
        return defaultSimulationNode(template, terminal, null, "default_simulation_node");
    }

    public static SimulationNode defaultSimulationNode(
            SimulationPromptTemplate template,
            boolean terminal,
            Integer maxVisits,
            String name) {
        var promptTemplate = template == null ? DEFAULT_TEMPLATE : template;
        return SimulationNode.ofText(context -> {
            var simulator = context.simulator();
            if (simulator == null) {
                throw new IllegalArgumentException("default simulation node requires a ConversationSimulator context.");
            }
            return context.turns().isEmpty()
                    ? simulator.generateFirstUserInput(context.golden(), promptTemplate)
                    : simulator.generateNextUserInput(context.golden(), context.turns(), promptTemplate);
        }, terminal, maxVisits, name);
    }

    public String language() {
        return language;
    }

    public List<ConversationalTestCase> simulatedConversations() {
        return simulatedConversations;
    }

    public String generateFirstUserInput(ConversationalGolden golden) {
        return generateFirstUserInput(golden, DEFAULT_TEMPLATE);
    }

    public String generateFirstUserInput(ConversationalGolden golden, SimulationPromptTemplate template) {
        var prompt = (template == null ? DEFAULT_TEMPLATE : template).simulateFirstUserTurn(golden, language);
        return generateSchema(prompt).simulatedInput();
    }

    public String generateNextUserInput(ConversationalGolden golden, List<Turn> turns) {
        return generateNextUserInput(golden, turns, DEFAULT_TEMPLATE);
    }

    public String generateNextUserInput(ConversationalGolden golden, List<Turn> turns, SimulationPromptTemplate template) {
        var prompt = (template == null ? DEFAULT_TEMPLATE : template).simulateUserTurn(golden, turns, language);
        return generateSchema(prompt).simulatedInput();
    }

    public SimulatedInput generateSchema(String prompt) {
        try {
            var input = JSON.treeToValue(MetricUtils.trimAndLoadJson(simulatorModel.generate(prompt)),
                    SimulatedInput.class);
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

    public List<ConversationalTestCase> simulate(List<ConversationalGolden> goldens) {
        return simulate(goldens, 10);
    }

    public List<ConversationalTestCase> simulate(List<ConversationalGolden> goldens, int maxUserSimulations) {
        return simulate(goldens, maxUserSimulations, null);
    }

    public List<ConversationalTestCase> simulate(
            List<ConversationalGolden> goldens,
            int maxUserSimulations,
            SimulationCompleteCallback onSimulationComplete) {
        if (maxUserSimulations <= 0) {
            throw new IllegalArgumentException("max_user_simulations must be a positive integer.");
        }
        Objects.requireNonNull(goldens, "goldens");
        var conversations = new ArrayList<ConversationalTestCase>();
        for (var index = 0; index < goldens.size(); index++) {
            var conversation = simulate(goldens.get(index), maxUserSimulations, index);
            conversations.add(conversation);
            if (onSimulationComplete != null) {
                onSimulationComplete.accept(conversation, index);
            }
        }
        simulatedConversations = List.copyOf(conversations);
        return simulatedConversations;
    }

    public ConversationalTestCase simulate(ConversationalGolden golden) {
        return simulate(golden, 10);
    }

    public ConversationalTestCase simulate(ConversationalGolden golden, int maxUserSimulations) {
        if (maxUserSimulations <= 0) {
            throw new IllegalArgumentException("max_user_simulations must be a positive integer.");
        }
        var conversation = simulate(golden, maxUserSimulations, 0);
        simulatedConversations = List.of(conversation);
        return conversation;
    }

    private ConversationalTestCase simulate(ConversationalGolden golden, int maxUserSimulations, int index) {
        Objects.requireNonNull(golden, "golden");
        var turns = new ArrayList<Turn>();
        if (golden.turns() != null) {
            turns.addAll(golden.turns());
        }
        var state = graphRunner.newConversationState();
        var threadId = UUID.randomUUID().toString();
        var simulatedUserTurns = 0;
        while (simulatedUserTurns < maxUserSimulations) {
            if (stoppingController.run(turns, golden, index, threadId,
                    simulatedUserTurns, maxUserSimulations, language)) {
                break;
            }
            var generatedUserTurn = false;
            var terminalEmission = false;
            String input;
            if (!turns.isEmpty() && "user".equals(turns.getLast().role())) {
                input = turns.getLast().content();
            } else {
                var emission = graphRunner.run(this, state, turns, golden, threadId, language);
                if (emission.turn() == null) {
                    break;
                }
                turns.add(emission.turn());
                input = emission.turn().content();
                generatedUserTurn = true;
                terminalEmission = emission.end();
            }

            var assistantTurn = generateTurnFromCallback(input, turns, threadId);
            turns.add(assistantTurn);
            if (generatedUserTurn) {
                simulatedUserTurns++;
            }
            graphRunner.advance(simulatorModel, state, assistantTurn.content());
            if (terminalEmission) {
                break;
            }
        }
        return toTestCase(golden, turns);
    }

    private static ConversationalTestCase toTestCase(ConversationalGolden golden, List<Turn> turns) {
        return ConversationalTestCase.builder(turns)
                .scenario(golden.scenario())
                .expectedOutcome(golden.expectedOutcome())
                .userDescription(golden.userDescription())
                .context(golden.context())
                .metadata(simulatedMetadata(golden))
                .comments(golden.comments())
                .name(golden.name())
                .datasetRank(golden.datasetRank())
                .datasetAlias(golden.datasetAlias())
                .datasetId(golden.datasetId())
                .multimodal(golden.multimodal())
                .build();
    }

    private static Map<String, Object> simulatedMetadata(ConversationalGolden golden) {
        var metadata = new LinkedHashMap<String, Object>();
        if (golden.additionalMetadata() != null) {
            metadata.putAll(golden.additionalMetadata());
        }
        if (golden.userDescription() != null) {
            metadata.put("User Description", golden.userDescription());
        }
        return metadata.isEmpty() ? null : metadata;
    }

    @FunctionalInterface
    public interface ModelCallback {
        Turn generate(CallbackContext context);
    }

    @FunctionalInterface
    public interface SimulationCompleteCallback {
        void accept(ConversationalTestCase testCase, int index);
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
