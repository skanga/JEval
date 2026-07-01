package dev.jeval.metrics;

import dev.jeval.LlmTestCase;
import dev.jeval.Metric;
import dev.jeval.MetricResult;
import dev.jeval.SingleTurnParam;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class PatternMatchMetric implements Metric {
    private final String pattern;
    private final Pattern compiledPattern;
    private final double threshold;
    private double score;
    private String reason;
    private boolean success;

    public PatternMatchMetric(String pattern) {
        this(pattern, false, 1.0);
    }

    public PatternMatchMetric(String pattern, boolean ignoreCase) {
        this(pattern, ignoreCase, 1.0);
    }

    public PatternMatchMetric(String pattern, boolean ignoreCase, double threshold) {
        if (pattern == null || pattern.isBlank()) {
            throw new IllegalArgumentException("Pattern match metric requires pattern");
        }
        this.pattern = pattern.strip();
        this.threshold = threshold;
        try {
            compiledPattern = Pattern.compile(this.pattern, ignoreCase ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0);
        } catch (PatternSyntaxException error) {
            throw new IllegalArgumentException("Invalid regex pattern: " + pattern + " - " + error.getMessage(), error);
        }
    }

    @Override
    public MetricResult measure(LlmTestCase testCase) {
        MetricUtils.checkLlmTestCaseParams(
                testCase,
                List.of(SingleTurnParam.INPUT, SingleTurnParam.ACTUAL_OUTPUT),
                name());

        var matches = compiledPattern.matcher(testCase.actualOutput().strip()).matches();
        score = matches ? 1.0 : 0.0;
        reason = matches
                ? "The actual output fully matches the pattern."
                : "The actual output does not match the pattern.";
        success = score >= threshold;
        return new MetricResult(name(), score, threshold, success, reason);
    }

    public String name() {
        return "Pattern Match";
    }

    public String pattern() {
        return pattern;
    }

    public double score() {
        return score;
    }

    public String reason() {
        return reason;
    }

    public boolean success() {
        return success;
    }
}
