package dev.jeval.modelintegrations;

import dev.jeval.EvaluationCost;
import dev.jeval.ToolCall;
import java.util.List;

public record OutputParameters(
        Object output,
        Integer promptTokens,
        Integer completionTokens,
        List<ToolCall> toolsCalled) {

    public OutputParameters() {
        this(null, null, null, null);
    }

    public OutputParameters {
        toolsCalled = toolsCalled == null ? null : List.copyOf(toolsCalled);
    }

    public EvaluationCost evaluationCost(Double costPerInputToken, Double costPerOutputToken) {
        if (costPerInputToken == null || costPerOutputToken == null) {
            return null;
        }
        var input = promptTokens == null ? 0 : promptTokens;
        var output = completionTokens == null ? 0 : completionTokens;
        return new EvaluationCost(
                input * costPerInputToken + output * costPerOutputToken,
                promptTokens,
                completionTokens);
    }
}
