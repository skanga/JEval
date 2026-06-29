package dev.jeval.optimizer.algorithms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jeval.ConversationalGolden;
import dev.jeval.Golden;
import dev.jeval.Turn;
import dev.jeval.optimizer.OptimizerUtils;
import dev.jeval.prompt.Prompt;
import dev.jeval.prompt.PromptType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.Function;

public final class MIPROV2InstructionProposer {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<String> INSTRUCTION_TIPS = List.of(
            "Be creative and think outside the box.",
            "Be concise and direct.",
            "Use step-by-step reasoning.",
            "Focus on clarity and precision.",
            "Include specific examples where helpful.",
            "Emphasize the most important aspects.",
            "Consider edge cases and exceptions.",
            "Use structured formatting when appropriate.",
            "Be thorough but avoid unnecessary details.",
            "Prioritize accuracy over creativity.",
            "Make the instruction self-contained.",
            "Use natural, conversational language.",
            "Be explicit about expected output format.",
            "Include context about common mistakes to avoid.",
            "Focus on the user's intent and goals.");

    private final Function<String, String> generateCallback;
    private final Random randomState;

    public MIPROV2InstructionProposer(Function<String, String> generateCallback) {
        this(generateCallback, new Random());
    }

    public MIPROV2InstructionProposer(Function<String, String> generateCallback, int randomState) {
        this(generateCallback, new Random(randomState));
    }

    public MIPROV2InstructionProposer(Function<String, String> generateCallback, Random randomState) {
        this.generateCallback = Objects.requireNonNull(generateCallback, "generateCallback");
        this.randomState = randomState == null ? new Random() : randomState;
    }

    public List<Prompt> propose(Prompt prompt, List<?> goldens, int numCandidates) {
        var candidates = new ArrayList<Prompt>();
        candidates.add(prompt);
        if (numCandidates <= 1) {
            return List.copyOf(candidates);
        }

        var examplesText = formatExamples(goldens, 5);
        var promptText = OptimizerUtils.parsePrompt(prompt);
        var datasetSummary = datasetSummary(examplesText);
        var tips = selectTips(numCandidates - 1);

        for (var i = 0; i < tips.size(); i++) {
            try {
                var raw = generateCallback.apply(MIPROV2Template.generateInstructionProposal(
                        promptText,
                        datasetSummary,
                        examplesText,
                        tips.get(i),
                        i,
                        prompt.type() == PromptType.LIST));
                var revisedInstruction = revisedInstruction(raw);
                if (revisedInstruction == null || revisedInstruction.isBlank()) {
                    continue;
                }
                var candidate = OptimizerUtils.createPrompt(prompt, revisedInstruction);
                if (!isDuplicate(candidate, candidates)) {
                    candidates.add(candidate);
                }
            } catch (RuntimeException ignored) {
                // Match DeepEval's proposer: skip malformed candidate generations.
            }
        }

        return List.copyOf(candidates);
    }

    private String datasetSummary(String examplesText) {
        try {
            var raw = generateCallback.apply(MIPROV2Template.generateDatasetSummary(examplesText));
            var summary = MAPPER.readTree(raw).get("summary");
            if (summary != null && summary.isTextual() && !summary.asText().isBlank()) {
                return summary.asText();
            }
        } catch (Exception ignored) {
            // Fall through to DeepEval's generic summary fallback.
        }
        return "A standard text processing task based on the provided inputs.";
    }

    private List<String> selectTips(int count) {
        if (count <= 0) {
            return List.of();
        }
        if (count >= INSTRUCTION_TIPS.size()) {
            var tips = new ArrayList<>(INSTRUCTION_TIPS);
            while (tips.size() < count) {
                tips.add(INSTRUCTION_TIPS.get(randomState.nextInt(INSTRUCTION_TIPS.size())));
            }
            return List.copyOf(tips.subList(0, count));
        }
        var tips = new ArrayList<>(INSTRUCTION_TIPS);
        Collections.shuffle(tips, randomState);
        return List.copyOf(tips.subList(0, count));
    }

    private static String formatExamples(List<?> goldens, int maxExamples) {
        if (goldens == null || goldens.isEmpty()) {
            return "No examples available.";
        }
        var examples = new ArrayList<String>();
        for (var i = 0; i < Math.min(maxExamples, goldens.size()); i++) {
            var golden = goldens.get(i);
            if (golden instanceof Golden singleTurnGolden) {
                examples.add("Example " + (i + 1) + ":\n  Input: " + singleTurnGolden.input()
                        + "\n  Expected: " + (singleTurnGolden.expectedOutput() == null
                                ? ""
                                : singleTurnGolden.expectedOutput()));
            } else if (golden instanceof ConversationalGolden conversationalGolden) {
                var turns = conversationalGolden.turns() == null ? List.<Turn>of() : conversationalGolden.turns();
                examples.add("Example " + (i + 1) + ": "
                        + String.join(" | ", turns.stream().map(Turn::toString).toList()));
            }
        }
        return examples.isEmpty() ? "No examples available." : String.join("\n", examples);
    }

    private static String revisedInstruction(String raw) {
        try {
            var revised = MAPPER.readTree(raw).get("revised_instruction");
            if (revised == null || revised.isNull()) {
                return null;
            }
            if (revised.isTextual()) {
                return revised.asText();
            }
            if (revised.isArray()) {
                return MAPPER.writeValueAsString(revised);
            }
            return null;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Failed to parse MIPROv2 instruction proposal response.", exception);
        }
    }

    private static boolean isDuplicate(Prompt candidate, List<Prompt> existing) {
        var candidateText = OptimizerUtils.parsePrompt(candidate).strip().toLowerCase();
        for (var prompt : existing) {
            var existingText = OptimizerUtils.parsePrompt(prompt).strip().toLowerCase();
            if (candidateText.equals(existingText) || similarity(candidateText, existingText) > 0.90) {
                return true;
            }
        }
        return false;
    }

    private static double similarity(String left, String right) {
        if (left.isEmpty() && right.isEmpty()) {
            return 1.0;
        }
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }
        var distance = levenshtein(left, right);
        return 1.0 - (distance / (double) Math.max(left.length(), right.length()));
    }

    private static int levenshtein(String left, String right) {
        var previous = new int[right.length() + 1];
        var current = new int[right.length() + 1];
        for (var j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }
        for (var i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (var j = 1; j <= right.length(); j++) {
                var cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(
                        Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost);
            }
            var temp = previous;
            previous = current;
            current = temp;
        }
        return previous[right.length()];
    }
}
