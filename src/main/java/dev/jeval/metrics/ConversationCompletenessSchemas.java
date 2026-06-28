package dev.jeval.metrics;

import java.util.List;

public final class ConversationCompletenessSchemas {
    private ConversationCompletenessSchemas() {
    }

    public static UserIntentions parseUserIntentions(String modelOutput) {
        return new UserIntentions(MetricUtils.requiredStringList(
                MetricUtils.trimAndLoadJson(modelOutput),
                "intentions"));
    }

    public static ConversationCompletenessVerdict parseVerdict(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new ConversationCompletenessVerdict(
                MetricUtils.requiredText(node, "verdict"),
                MetricUtils.optionalText(node, "reason"));
    }

    public static ConversationCompletenessScoreReason parseReason(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new ConversationCompletenessScoreReason(MetricUtils.requiredText(node, "reason"));
    }

    public record UserIntentions(List<String> intentions) {
        public UserIntentions {
            intentions = intentions == null ? List.of() : List.copyOf(intentions);
        }
    }

    public record ConversationCompletenessVerdict(String verdict, String reason) {
    }

    public record ConversationCompletenessScoreReason(String reason) {
    }
}
