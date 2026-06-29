package dev.jeval.optimizer.algorithms;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jeval.DeepEvalException;
import dev.jeval.optimizer.OptimizerUtils;
import dev.jeval.prompt.Prompt;
import dev.jeval.prompt.PromptType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class COPROProposer {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Function<String, String> generateCallback;

    public COPROProposer(Function<String, String> generateCallback) {
        this.generateCallback = Objects.requireNonNull(generateCallback, "generateCallback");
    }

    public List<Prompt> proposeBootstrap(Prompt originalPrompt, int breadth) {
        var promptText = OptimizerUtils.parsePrompt(originalPrompt);
        var template = COPROTemplate.generateBootstrapGuidelines(promptText, breadth);
        return candidates(originalPrompt, promptText, guidelines(generateCallback.apply(template), breadth));
    }

    public List<Prompt> proposeFromHistory(Prompt originalPrompt, String historyText, int breadth) {
        var promptText = OptimizerUtils.parsePrompt(originalPrompt);
        var template = COPROTemplate.generateHistoryGuidelines(promptText, historyText, breadth);
        return candidates(originalPrompt, promptText, guidelines(generateCallback.apply(template), breadth));
    }

    private List<Prompt> candidates(Prompt originalPrompt, String promptText, List<String> guidelines) {
        var candidates = new ArrayList<Prompt>();
        var seen = new ArrayList<String>();
        var listFormat = originalPrompt.type() == PromptType.LIST;
        for (var guideline : guidelines) {
            var response = generateCallback.apply(COPROTemplate.generateCandidate(promptText, guideline, listFormat));
            var revisedPrompt = revisedPrompt(response);
            if (revisedPrompt.isBlank() || seen.contains(revisedPrompt)) {
                continue;
            }
            candidates.add(OptimizerUtils.createPrompt(originalPrompt, revisedPrompt));
            seen.add(revisedPrompt);
        }
        return List.copyOf(candidates);
    }

    private static List<String> guidelines(String rawResponse, int breadth) {
        try {
            var node = MAPPER.readTree(rawResponse).get("guidelines");
            if (node == null || !node.isArray()) {
                throw new DeepEvalException("COPRO guidelines response must contain `guidelines`.");
            }
            var guidelines = new ArrayList<String>();
            for (var item : node) {
                if (item.isTextual()) {
                    guidelines.add(item.asText());
                    if (guidelines.size() == breadth) {
                        break;
                    }
                }
            }
            return List.copyOf(guidelines);
        } catch (JsonProcessingException exception) {
            throw new DeepEvalException("Failed to parse COPRO guidelines response.", exception);
        }
    }

    private static String revisedPrompt(String rawResponse) {
        try {
            var node = MAPPER.readTree(rawResponse);
            JsonNode revised = node.get("revised_prompt");
            if (revised == null) {
                throw new DeepEvalException("COPRO candidate response must contain `revised_prompt`.");
            }
            return revised.isTextual() ? revised.asText() : MAPPER.writeValueAsString(revised);
        } catch (JsonProcessingException exception) {
            throw new DeepEvalException("Failed to parse COPRO candidate response.", exception);
        }
    }
}
