package dev.jeval.optimizer.algorithms;

import dev.jeval.ConversationalGolden;
import dev.jeval.DeepEvalException;
import dev.jeval.Golden;
import dev.jeval.Turn;
import dev.jeval.optimizer.OptimizerScorer;
import dev.jeval.prompt.Prompt;
import dev.jeval.prompt.PromptMessage;
import dev.jeval.prompt.PromptType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

public final class MIPROV2Bootstrapper {
    private final OptimizerScorer scorer;
    private final int maxBootstrappedDemonstrations;
    private final int maxLabeledDemonstrations;
    private final int numDemonstrationSets;
    private final Random randomState;

    public MIPROV2Bootstrapper(
            int maxBootstrappedDemonstrations,
            int maxLabeledDemonstrations,
            int numDemonstrationSets,
            Random randomState) {
        this(null, maxBootstrappedDemonstrations, maxLabeledDemonstrations, numDemonstrationSets, randomState);
    }

    public MIPROV2Bootstrapper(
            OptimizerScorer scorer,
            int maxBootstrappedDemonstrations,
            int maxLabeledDemonstrations,
            int numDemonstrationSets,
            Random randomState) {
        this.scorer = scorer;
        this.maxBootstrappedDemonstrations = maxBootstrappedDemonstrations;
        this.maxLabeledDemonstrations = maxLabeledDemonstrations;
        this.numDemonstrationSets = numDemonstrationSets;
        this.randomState = randomState == null ? new Random() : randomState;
    }

    public List<MIPROV2DemonstrationSet> bootstrap(Prompt prompt, List<?> goldens) {
        if (scorer == null) {
            throw new DeepEvalException("MIPROv2 bootstrapper requires an OptimizerScorer to bootstrap demonstrations.");
        }
        var allDemonstrations = new ArrayList<MIPROV2Demonstration>();
        var labeledDemonstrations = new ArrayList<MIPROV2Demonstration>();
        var items = List.copyOf(Objects.requireNonNull(goldens, "goldens"));
        var shuffledIndices = new ArrayList<Integer>();
        for (var i = 0; i < items.size(); i++) {
            shuffledIndices.add(i);
        }
        Collections.shuffle(shuffledIndices, randomState);

        var maxAttempts = Math.min(items.size(), maxBootstrappedDemonstrations * 3);
        var prompts = Map.of(OptimizerScorer.DEFAULT_MODULE_ID, prompt);

        for (var idx : shuffledIndices.subList(0, maxAttempts)) {
            var golden = items.get(idx);
            var inputText = extractInput(golden);
            var expected = extractExpectedOutput(golden);

            if (!expected.isBlank()
                    && labeledDemonstrations.size() < maxLabeledDemonstrations * numDemonstrationSets) {
                labeledDemonstrations.add(new MIPROV2Demonstration(inputText, expected, idx));
            }

            if (allDemonstrations.size() < maxBootstrappedDemonstrations * numDemonstrationSets) {
                try {
                    var actual = scorer.generate(prompts, golden);
                    var evaluation = scorer.evaluateGenerated(golden, actual);
                    if (evaluation.success()) {
                        allDemonstrations.add(new MIPROV2Demonstration(inputText, actual, idx));
                    }
                } catch (RuntimeException ignored) {
                    // DeepEval skips failed generation/evaluation attempts during bootstrap.
                }
            }

            if (allDemonstrations.size() >= maxBootstrappedDemonstrations * numDemonstrationSets
                    && labeledDemonstrations.size() >= maxLabeledDemonstrations * numDemonstrationSets) {
                break;
            }
        }

        var demoSets = createDemonstrationSets(allDemonstrations, labeledDemonstrations);
        if (demoSets.isEmpty() || demoSets.stream().allMatch(set -> set.demonstrations().isEmpty())) {
            throw new DeepEvalException(
                    "Bootstrapper failed to generate any demonstrations. "
                            + "Please ensure your goldens contain an 'expected_output' for labeled demonstrations.");
        }
        return demoSets;
    }

    public List<MIPROV2DemonstrationSet> createDemonstrationSets(
            List<MIPROV2Demonstration> bootstrappedDemonstrations,
            List<MIPROV2Demonstration> labeledDemonstrations) {
        var bootstrapped = List.copyOf(Objects.requireNonNull(
                bootstrappedDemonstrations, "bootstrappedDemonstrations"));
        var labeled = List.copyOf(Objects.requireNonNull(labeledDemonstrations, "labeledDemonstrations"));
        var demoSets = new ArrayList<MIPROV2DemonstrationSet>();
        demoSets.add(new MIPROV2DemonstrationSet(List.of(), "0-shot"));

        for (var i = 0; i < numDemonstrationSets; i++) {
            var demos = new ArrayList<MIPROV2Demonstration>();

            if (!bootstrapped.isEmpty()) {
                demos.addAll(sample(bootstrapped, Math.min(maxBootstrappedDemonstrations, bootstrapped.size())));
            }

            if (!labeled.isEmpty()) {
                var existingIndices = new HashSet<Integer>();
                for (var demo : demos) {
                    existingIndices.add(demo.goldenIndex());
                }
                for (var demo : sample(labeled, Math.min(maxLabeledDemonstrations, labeled.size()))) {
                    if (existingIndices.add(demo.goldenIndex())) {
                        demos.add(demo);
                    }
                }
            }

            if (!demos.isEmpty()) {
                Collections.shuffle(demos, randomState);
                demoSets.add(new MIPROV2DemonstrationSet(demos));
            }
        }

        return List.copyOf(demoSets);
    }

    public static Prompt renderPromptWithDemonstrations(
            Prompt prompt,
            MIPROV2DemonstrationSet demonstrationSet,
            int maxDemonstrations) {
        Objects.requireNonNull(prompt, "prompt");
        if (demonstrationSet == null || demonstrationSet.demonstrations().isEmpty()) {
            return prompt;
        }

        var demoText = demonstrationSet.toText(maxDemonstrations);
        if (prompt.type() == PromptType.TEXT) {
            return copyWithText(prompt, demoText + "\n\n" + prompt.textTemplate());
        }
        if (prompt.type() == PromptType.LIST) {
            return copyWithMessages(prompt, injectIntoMessages(prompt.messagesTemplate(), demoText));
        }
        throw new DeepEvalException("Unsupported prompt type: " + prompt.type());
    }

    private static List<PromptMessage> injectIntoMessages(List<PromptMessage> messages, String demoText) {
        var newMessages = new ArrayList<PromptMessage>();
        var demoAdded = false;
        for (var message : messages) {
            if (!demoAdded && "system".equals(message.role())) {
                newMessages.add(new PromptMessage(message.role(), message.content() + "\n\n" + demoText));
                demoAdded = true;
            } else {
                newMessages.add(message);
            }
        }

        if (!demoAdded && !newMessages.isEmpty()) {
            var first = newMessages.getFirst();
            newMessages.set(0, new PromptMessage(first.role(), demoText + "\n\n" + first.content()));
        }
        return newMessages;
    }

    private static Prompt copyWithText(Prompt prompt, String textTemplate) {
        return new Prompt(
                prompt.alias(),
                textTemplate,
                null,
                prompt.modelSettings(),
                prompt.outputType(),
                prompt.outputSchema(),
                prompt.interpolationType(),
                prompt.confidentApiKey(),
                prompt.branch());
    }

    private static Prompt copyWithMessages(Prompt prompt, List<PromptMessage> messages) {
        return new Prompt(
                prompt.alias(),
                null,
                List.copyOf(messages),
                prompt.modelSettings(),
                prompt.outputType(),
                prompt.outputSchema(),
                prompt.interpolationType(),
                prompt.confidentApiKey(),
                prompt.branch());
    }

    private List<MIPROV2Demonstration> sample(List<MIPROV2Demonstration> demonstrations, int size) {
        var shuffled = new ArrayList<>(demonstrations);
        Collections.shuffle(shuffled, randomState);
        return shuffled.subList(0, Math.max(0, size));
    }

    private static String extractInput(Object golden) {
        if (golden instanceof Golden singleTurnGolden) {
            if (singleTurnGolden.input() == null || singleTurnGolden.input().isBlank()) {
                throw new DeepEvalException("Golden must have a valid 'input' for MIPROv2 bootstrapping.");
            }
            return singleTurnGolden.input();
        }
        if (golden instanceof ConversationalGolden conversationalGolden) {
            var userTurns = (conversationalGolden.turns() == null ? List.<Turn>of() : conversationalGolden.turns())
                    .stream()
                    .filter(turn -> "user".equals(turn.role()))
                    .map(Turn::content)
                    .toList();
            if (userTurns.isEmpty()) {
                throw new DeepEvalException(
                        "ConversationalGolden must have at least one 'user' turn for MIPROv2 bootstrapping.");
            }
            return String.join("\n", userTurns);
        }
        throw new DeepEvalException("MIPROv2 bootstrapper expected Golden or ConversationalGolden.");
    }

    private static String extractExpectedOutput(Object golden) {
        if (golden instanceof Golden singleTurnGolden) {
            if (singleTurnGolden.expectedOutput() == null || singleTurnGolden.expectedOutput().isBlank()) {
                throw new DeepEvalException(
                        "Golden must have a valid 'expected_output' for MIPROv2 bootstrapping.");
            }
            return singleTurnGolden.expectedOutput();
        }
        if (golden instanceof ConversationalGolden conversationalGolden) {
            if (conversationalGolden.expectedOutcome() == null || conversationalGolden.expectedOutcome().isBlank()) {
                throw new DeepEvalException(
                        "ConversationalGolden must have a valid 'expected_outcome' for MIPROv2 bootstrapping.");
            }
            return conversationalGolden.expectedOutcome();
        }
        throw new DeepEvalException("MIPROv2 bootstrapper expected Golden or ConversationalGolden.");
    }
}
