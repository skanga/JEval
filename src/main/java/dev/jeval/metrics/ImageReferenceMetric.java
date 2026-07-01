package dev.jeval.metrics;

import dev.jeval.EvaluationModel;
import dev.jeval.LlmTestCase;
import dev.jeval.Metric;
import dev.jeval.MetricResult;
import dev.jeval.MllmImage;
import dev.jeval.SingleTurnParam;
import dev.jeval.Utils;
import java.util.ArrayList;
import java.util.List;

public class ImageReferenceMetric implements Metric {
    private final double threshold;
    private final boolean strictMode;
    private final Integer maxContextSize;
    private final EvaluationModel model;
    private double score;
    private String reason;
    private boolean success;

    public ImageReferenceMetric() {
        this(null);
    }

    public ImageReferenceMetric(EvaluationModel model) {
        this(model, 0.5, false, null);
    }

    public ImageReferenceMetric(double threshold, boolean strictMode) {
        this(null, threshold, strictMode, null);
    }

    public ImageReferenceMetric(double threshold, boolean strictMode, Integer maxContextSize) {
        this(null, threshold, strictMode, maxContextSize);
    }

    public ImageReferenceMetric(EvaluationModel model, double threshold, boolean strictMode, Integer maxContextSize) {
        if (!Double.isFinite(threshold)) {
            throw new IllegalArgumentException("Image Reference threshold must be finite");
        }
        this.threshold = strictMode ? 1.0 : threshold;
        this.strictMode = strictMode;
        this.maxContextSize = maxContextSize;
        this.model = model;
    }

    @Override
    public MetricResult measure(LlmTestCase testCase) {
        MetricUtils.checkLlmTestCaseParams(
                testCase,
                List.of(SingleTurnParam.INPUT, SingleTurnParam.ACTUAL_OUTPUT),
                name());

        var actualOutput = Utils.convertToMultiModalArray(testCase.actualOutput());
        var imageIndices = imageIndices(actualOutput);
        if (imageIndices.isEmpty()) {
            throw new IllegalArgumentException(
                    "'actual_output' must contain an MLLMImage for the '" + name() + "' metric");
        }

        var scores = new ArrayList<Double>();
        var reasons = new ArrayList<String>();
        for (int imageIndex : imageIndices) {
            var image = (MllmImage) actualOutput.get(imageIndex);
            var contexts = imageContext(imageIndex, actualOutput, maxContextSize);
            var scoreReason = evaluateImageReference(image, contexts.above(), contexts.below());
            scores.add(scoreReason.score() / 10.0);
            reasons.add(scoreReason.reasoning());
        }

        score = average(scores);
        if (strictMode && score < threshold) {
            score = 0.0;
        }
        reason = imageReasons(reasons);
        success = score >= threshold;
        return new MetricResult(name(), score, threshold, success, reason);
    }

    public String name() {
        return "Image Reference";
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

    protected ScoreReason evaluateImageReference(MllmImage image, String contextAbove, String contextBelow) {
        if (model == null) {
            throw new UnsupportedOperationException("Image Reference generation requires a model provider");
        }
        return parseScoreReason(model.generate(prompt(image, contextAbove, contextBelow)));
    }

    private static List<Integer> imageIndices(List<Object> values) {
        var indices = new ArrayList<Integer>();
        for (var i = 0; i < values.size(); i++) {
            if (values.get(i) instanceof MllmImage) {
                indices.add(i);
            }
        }
        return List.copyOf(indices);
    }

    private static ImageContext imageContext(int imageIndex, List<Object> actualOutput, Integer maxContextSize) {
        String contextAbove = null;
        String contextBelow = null;
        for (var i = imageIndex - 1; i >= 0; i--) {
            if (actualOutput.get(i) instanceof String text) {
                contextAbove = maxContextSize == null ? text : text.substring(Math.max(0, text.length() - maxContextSize));
                break;
            }
        }
        for (var i = imageIndex + 1; i < actualOutput.size(); i++) {
            if (actualOutput.get(i) instanceof String text) {
                contextBelow = maxContextSize == null ? text : text.substring(0, Math.min(text.length(), maxContextSize));
                break;
            }
        }
        return new ImageContext(contextAbove, contextBelow);
    }

    private static double average(List<Double> values) {
        return values.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElseThrow(() -> new IllegalArgumentException("score lists must not be empty"));
    }

    private static String imageReasons(List<String> reasons) {
        var lines = new ArrayList<String>();
        for (var i = 0; i < reasons.size(); i++) {
            lines.add("Reason for image " + i + ": " + reasons.get(i));
        }
        return String.join("\n", lines);
    }

    private static String prompt(MllmImage image, String contextAbove, String contextBelow) {
        return """
                # Task Description
                You are a multi-modal document quality assessment assistant. You will receive an image and its accompanying textual context.
                Your task is to determine whether the image is explicitly referenced or explained within the surrounding text (both above and below the image).

                # Context Above
                %s

                # Context Below
                %s

                # Image
                [The image is provided below this section.]

                # Scoring Criteria
                Evaluate the extent to which the image is referenced or explained in the text, assigning a score from 0 to 10.

                # Output Instructions
                Provide your evaluation in JSON with keys score and reasoning.

                Images: %s
                """.formatted(contextAbove, contextBelow, image);
    }

    protected static ScoreReason parseScoreReason(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new ScoreReason(
                MetricUtils.requiredDouble(node, "score"),
                MetricUtils.requiredText(node, "reasoning"));
    }

    private record ImageContext(String above, String below) {}

    protected record ScoreReason(double score, String reasoning) {}
}
