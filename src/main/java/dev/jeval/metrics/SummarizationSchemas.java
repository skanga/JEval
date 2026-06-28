package dev.jeval.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class SummarizationSchemas {
    private static final Set<String> VALID_VERDICTS = Set.of("yes", "no", "idk");

    private SummarizationSchemas() {}

    public static Verdicts parseVerdicts(String modelOutput) {
        var node = MetricUtils.required(MetricUtils.trimAndLoadJson(modelOutput), "verdicts");
        if (!node.isArray()) {
            throw new IllegalArgumentException("Schema field must be a list: verdicts");
        }
        var values = new ArrayList<SummarizationAlignmentVerdict>();
        node.forEach(value -> values.add(new SummarizationAlignmentVerdict(
                MetricUtils.requiredText(value, "verdict"),
                MetricUtils.optionalText(value, "reason"))));
        return new Verdicts(values);
    }

    public static Questions parseQuestions(String modelOutput) {
        return new Questions(MetricUtils.requiredStringList(
                MetricUtils.trimAndLoadJson(modelOutput),
                "questions"));
    }

    public static Answers parseAnswers(String modelOutput) {
        return new Answers(MetricUtils.requiredStringList(
                MetricUtils.trimAndLoadJson(modelOutput),
                "answers"));
    }

    public static SummarizationScoreReason parseScoreReason(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new SummarizationScoreReason(MetricUtils.requiredText(node, "reason"));
    }

    public enum ScoreType {
        ALIGNMENT("Alignment"),
        COVERAGE("Coverage");

        private final String value;

        ScoreType(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    public record SummarizationAlignmentVerdict(String verdict, String reason) {
        public SummarizationAlignmentVerdict {
            if (!VALID_VERDICTS.contains(verdict)) {
                throw new IllegalArgumentException("Invalid verdict: " + verdict);
            }
        }
    }

    public record SummarizationCoverageVerdict(String summaryVerdict, String originalVerdict, String question) {
    }

    public record Verdicts(List<SummarizationAlignmentVerdict> verdicts) {
        public Verdicts {
            verdicts = verdicts == null ? null : List.copyOf(verdicts);
        }
    }

    public record Questions(List<String> questions) {
        public Questions {
            questions = questions == null ? null : List.copyOf(questions);
        }
    }

    public record Answers(List<String> answers) {
        public Answers {
            answers = answers == null ? null : List.copyOf(answers);
        }
    }

    public record SummarizationScoreReason(String reason) {
    }
}
