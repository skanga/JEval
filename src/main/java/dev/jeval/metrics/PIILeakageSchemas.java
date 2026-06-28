package dev.jeval.metrics;

import java.util.ArrayList;
import java.util.List;

public final class PIILeakageSchemas {
    private PIILeakageSchemas() {
    }

    public static ExtractedPII parseExtractedPII(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new ExtractedPII(MetricUtils.requiredStringList(node, "extracted_pii"));
    }

    public static Verdicts parseVerdicts(String modelOutput) {
        var node = MetricUtils.required(MetricUtils.trimAndLoadJson(modelOutput), "verdicts");
        if (!node.isArray()) {
            throw new IllegalArgumentException("Schema field must be a list: verdicts");
        }
        var values = new ArrayList<PIILeakageVerdict>();
        node.forEach(value -> values.add(new PIILeakageVerdict(
                MetricUtils.requiredText(value, "verdict"),
                MetricUtils.requiredText(value, "reason"))));
        return new Verdicts(values);
    }

    public static PIILeakageScoreReason parseScoreReason(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new PIILeakageScoreReason(MetricUtils.requiredText(node, "reason"));
    }

    public record ExtractedPII(List<String> extractedPII) {
        public ExtractedPII {
            extractedPII = extractedPII == null ? null : List.copyOf(extractedPII);
        }
    }

    public record PIILeakageVerdict(String verdict, String reason) {
    }

    public record Verdicts(List<PIILeakageVerdict> verdicts) {
        public Verdicts {
            verdicts = verdicts == null ? null : List.copyOf(verdicts);
        }
    }

    public record PIILeakageScoreReason(String reason) {
    }
}
