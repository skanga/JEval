package dev.jeval.optimizer.rewriter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jeval.DeepEvalException;
import dev.jeval.optimizer.OptimizerUtils;
import dev.jeval.optimizer.ScorerDiagnosisResult;
import dev.jeval.prompt.Prompt;
import dev.jeval.prompt.PromptType;
import java.util.Objects;
import java.util.function.Function;

public final class Rewriter {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Function<String, String> rewriteCallback;

    public Rewriter(Function<String, String> rewriteCallback) {
        this.rewriteCallback = Objects.requireNonNull(rewriteCallback, "rewriteCallback");
    }

    public Prompt rewrite(Prompt oldPrompt, ScorerDiagnosisResult feedbackDiagnosis) {
        if (feedbackDiagnosis == null || feedbackDiagnosis.analysis().isBlank()) {
            return oldPrompt;
        }
        var mutationPrompt = RewriterTemplate.generateMutation(
                OptimizerUtils.parsePrompt(oldPrompt),
                feedbackDiagnosis.failures(),
                feedbackDiagnosis.successes(),
                String.join("\n\n---\n\n", feedbackDiagnosis.results()),
                feedbackDiagnosis.analysis(),
                oldPrompt.type() == PromptType.LIST);
        return OptimizerUtils.createPrompt(oldPrompt, revisedPromptText(rewriteCallback.apply(mutationPrompt)));
    }

    private static String revisedPromptText(String rawResponse) {
        try {
            var node = MAPPER.readTree(rawResponse);
            if (node.isObject() && node.has("revised_prompt")) {
                var revised = node.get("revised_prompt");
                return revised.isTextual() ? revised.asText() : MAPPER.writeValueAsString(revised);
            }
        } catch (JsonProcessingException ignored) {
            return rawResponse;
        }
        throw new DeepEvalException("Rewriter response must contain `revised_prompt`.");
    }
}
