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

public class TextToImageMetric implements Metric {
    private final double threshold;
    private final boolean strictMode;
    private final EvaluationModel model;
    private double score;
    private String reason;
    private boolean success;
    private List<Double> semanticScores;
    private String semanticReasoning;
    private List<Double> perceptualScores;
    private String perceptualReasoning;

    public TextToImageMetric() {
        this(null);
    }

    public TextToImageMetric(EvaluationModel model) {
        this(model, 0.5, false);
    }

    public TextToImageMetric(double threshold, boolean strictMode) {
        this(null, threshold, strictMode);
    }

    public TextToImageMetric(EvaluationModel model, double threshold, boolean strictMode) {
        if (!Double.isFinite(threshold)) {
            throw new IllegalArgumentException("Text to Image threshold must be finite");
        }
        this.threshold = strictMode ? 1.0 : threshold;
        this.strictMode = strictMode;
        this.model = model;
    }

    @Override
    public MetricResult measure(LlmTestCase testCase) {
        MetricUtils.checkLlmTestCaseParams(
                testCase,
                List.of(SingleTurnParam.INPUT, SingleTurnParam.ACTUAL_OUTPUT),
                name());

        var input = Utils.convertToMultiModalArray(testCase.input());
        var actualOutput = Utils.convertToMultiModalArray(testCase.actualOutput());
        var inputTexts = textParts(input);
        var inputImages = imageParts(input);
        var outputImages = imageParts(actualOutput);
        requireImageCount("input", 0, inputImages.size());
        requireImageCount("output", 1, outputImages.size());

        var semantic = evaluateSemanticConsistency(String.join("\n", inputTexts), outputImages.getFirst());
        var perceptual = evaluatePerceptualQuality(outputImages.getFirst());
        semanticScores = semantic.scores();
        semanticReasoning = semantic.reasoning();
        perceptualScores = perceptual.scores();
        perceptualReasoning = perceptual.reasoning();
        score = calculateScore();
        if (strictMode && score < threshold) {
            score = 0.0;
        }
        reason = generateReason();
        success = score >= threshold;
        return new MetricResult(name(), score, threshold, success, reason);
    }

    public String name() {
        return "Text to Image";
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

    protected ScoreReason evaluateSemanticConsistency(String textPrompt, MllmImage actualImageOutput) {
        if (model == null) {
            throw new UnsupportedOperationException(
                    "Text to Image semantic consistency generation requires a model provider");
        }
        return scoreReason(model.generate(semanticPrompt(textPrompt, actualImageOutput)));
    }

    protected ScoreReason evaluatePerceptualQuality(MllmImage actualImageOutput) {
        if (model == null) {
            throw new UnsupportedOperationException(
                    "Text to Image perceptual quality generation requires a model provider");
        }
        return scoreReason(model.generate(perceptualPrompt(actualImageOutput)));
    }

    private double calculateScore() {
        return Math.sqrt(min(semanticScores) * min(perceptualScores)) / 10.0;
    }

    private String generateReason() {
        return """
                The overall score is %.2f because the lowest score from semantic consistency was %.1f \
                and the lowest score from perceptual quality was %.1f. These scores were combined to reflect the \
                overall effectiveness and quality of the AI-generated image(s).
                Reason for Semantic Consistency score: %s
                Reason for Perceptual Quality score: %s
                """.formatted(score, min(semanticScores), min(perceptualScores), semanticReasoning, perceptualReasoning);
    }

    private static double min(List<Double> values) {
        return values.stream()
                .mapToDouble(Double::doubleValue)
                .min()
                .orElseThrow(() -> new IllegalArgumentException("score lists must not be empty"));
    }

    private static List<String> textParts(List<Object> values) {
        var texts = new ArrayList<String>();
        for (var value : values) {
            if (value instanceof String text) {
                texts.add(text);
            }
        }
        return List.copyOf(texts);
    }

    private static List<MllmImage> imageParts(List<Object> values) {
        var images = new ArrayList<MllmImage>();
        for (var value : values) {
            if (value instanceof MllmImage image) {
                images.add(image);
            }
        }
        return List.copyOf(images);
    }

    private static void requireImageCount(String field, int expected, int count) {
        if (count != expected) {
            throw new IllegalArgumentException(
                    "Can only evaluate test cases with '" + expected + " " + field
                            + " images' using the 'Text to Image' metric. `" + count + "` found.");
        }
    }

    private static ScoreReason scoreReason(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new ScoreReason(
                MetricUtils.requiredDoubleList(node, "score"),
                MetricUtils.requiredText(node, "reasoning"));
    }

    private static String semanticPrompt(String textPrompt, MllmImage actualImageOutput) {
        return """
                RULES:

                The image is an AI-generated image according to the text prompt.
                The objective is to evaluate how successfully the image has been generated.

                From scale 0 to 10:
                A score from 0 to 10 will be given based on the success in following the prompt.
                Put the score in a list such that output score = [score].

                Text Prompt: %s

                Provide your evaluation in JSON with keys score and reasoning.

                Images: %s
                """.formatted(textPrompt, actualImageOutput);
    }

    private static String perceptualPrompt(MllmImage actualImageOutput) {
        return """
                RULES:

                The image is an AI-generated image.
                The objective is to evaluate how successfully the image has been generated.

                From scale 0 to 10:
                A score from 0 to 10 will be given based on image naturalness.
                A second score from 0 to 10 will rate the image artifacts.
                Put the score in a list such that output score = [naturalness, artifacts]

                Provide your evaluation in JSON with keys score and reasoning.

                Images: %s
                """.formatted(actualImageOutput);
    }

    protected record ScoreReason(List<Double> scores, String reasoning) {
        protected ScoreReason {
            scores = List.copyOf(scores);
        }
    }
}
