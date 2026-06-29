package dev.jeval.optimizer.algorithms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jeval.optimizer.OptimizerUtils;
import dev.jeval.prompt.Prompt;
import dev.jeval.prompt.PromptMessage;
import dev.jeval.prompt.PromptType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

public final class SIMBAProposer {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Function<String, String> generateCallback;

    public SIMBAProposer(Function<String, String> generateCallback) {
        this.generateCallback = Objects.requireNonNull(generateCallback, "generateCallback");
    }

    public Prompt rewriteFromIntrospection(
            Prompt originalPrompt,
            String betterInputs,
            String betterOutputs,
            double betterScore,
            String betterFeedback,
            String worseInputs,
            String worseOutputs,
            double worseScore,
            String worseFeedback) {
        var promptText = OptimizerUtils.parsePrompt(originalPrompt);
        var worseTrajectory = formatTrajectory(worseInputs, worseOutputs, worseScore, worseFeedback);
        var betterTrajectory = formatTrajectory(betterInputs, betterOutputs, betterScore, betterFeedback);
        var template = SIMBATemplate.generateIntrospectionRewrite(
                promptText,
                worseTrajectory,
                betterTrajectory,
                originalPrompt.type() == PromptType.LIST);

        try {
            var revisedPrompt = revisedPrompt(generateCallback.apply(template));
            if (revisedPrompt == null || revisedPrompt.isBlank()) {
                return originalPrompt;
            }
            return OptimizerUtils.createPrompt(originalPrompt, revisedPrompt);
        } catch (RuntimeException exception) {
            return originalPrompt;
        }
    }

    public Prompt appendADemo(Prompt originalPrompt, String inputs, String outputs) {
        var demoText = "\n\n[Example]\nInput: " + inputs + "\nOutput: " + outputs;
        return injectText(originalPrompt, demoText);
    }

    private static String formatTrajectory(String inputs, String outputs, double score, String feedback) {
        return "Inputs: " + inputs + "\n"
                + "Model Output: " + outputs + "\n"
                + "Score: " + String.format(Locale.ROOT, "%.4f", score) + "\n"
                + "Evaluation Feedback: " + feedback;
    }

    private static String revisedPrompt(String response) {
        try {
            var node = MAPPER.readTree(response);
            var revised = node.get("revised_prompt");
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
            throw new IllegalArgumentException("Failed to parse SIMBA rewrite response.", exception);
        }
    }

    private static Prompt injectText(Prompt prompt, String injection) {
        if (prompt.type() == PromptType.TEXT) {
            return OptimizerUtils.createPrompt(prompt, prompt.textTemplate() + injection);
        }
        if (prompt.type() == PromptType.LIST) {
            var newMessages = new ArrayList<PromptMessage>();
            var injected = false;
            for (var message : prompt.messagesTemplate()) {
                if (!injected && "system".equals(message.role())) {
                    newMessages.add(new PromptMessage(message.role(), message.content() + injection));
                    injected = true;
                } else {
                    newMessages.add(message);
                }
            }
            if (!injected && !newMessages.isEmpty()) {
                var first = newMessages.getFirst();
                newMessages.set(0, new PromptMessage(first.role(), first.content() + injection));
            }
            return new Prompt(
                    prompt.alias(),
                    null,
                    List.copyOf(newMessages),
                    prompt.modelSettings(),
                    prompt.outputType(),
                    prompt.outputSchema(),
                    prompt.interpolationType(),
                    prompt.confidentApiKey(),
                    prompt.branch());
        }
        return prompt;
    }
}
