package dev.jeval.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class TopicAdherenceSchemas {
    private static final Set<String> VALID_VERDICTS = Set.of("TP", "TN", "FP", "FN");

    private TopicAdherenceSchemas() {
    }

    public static QAPairs parseQaPairs(String modelOutput) {
        var node = MetricUtils.required(MetricUtils.trimAndLoadJson(modelOutput), "qa_pairs");
        if (!node.isArray()) {
            throw new IllegalArgumentException("Schema field must be a list: qa_pairs");
        }
        var pairs = new ArrayList<QAPair>();
        node.forEach(value -> pairs.add(new QAPair(
                MetricUtils.requiredText(value, "question"),
                MetricUtils.requiredText(value, "response"))));
        return new QAPairs(pairs);
    }

    public static RelevancyVerdict parseVerdict(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new RelevancyVerdict(
                MetricUtils.requiredText(node, "verdict"),
                MetricUtils.requiredText(node, "reason"));
    }

    public static TopicAdherenceReason parseReason(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new TopicAdherenceReason(MetricUtils.requiredText(node, "reason"));
    }

    public record QAPair(String question, String response) {
    }

    public record QAPairs(List<QAPair> qaPairs) {
        public QAPairs {
            qaPairs = qaPairs == null ? List.of() : List.copyOf(qaPairs);
        }
    }

    public record RelevancyVerdict(String verdict, String reason) {
        public RelevancyVerdict {
            if (!VALID_VERDICTS.contains(verdict)) {
                throw new IllegalArgumentException("Invalid verdict: " + verdict);
            }
        }
    }

    public record TopicAdherenceReason(String reason) {
    }
}
