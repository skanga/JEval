package dev.jeval.metrics;

import dev.jeval.EvaluationModel;
import dev.jeval.MllmImage;

public class ImageHelpfulnessMetric extends ImageReferenceMetric {
    private final EvaluationModel model;

    public ImageHelpfulnessMetric() {
        this(null);
    }

    public ImageHelpfulnessMetric(EvaluationModel model) {
        this(model, 0.5, false, null);
    }

    public ImageHelpfulnessMetric(double threshold, boolean strictMode) {
        this(null, threshold, strictMode, null);
    }

    public ImageHelpfulnessMetric(double threshold, boolean strictMode, Integer maxContextSize) {
        this(null, threshold, strictMode, maxContextSize);
    }

    public ImageHelpfulnessMetric(EvaluationModel model, double threshold, boolean strictMode, Integer maxContextSize) {
        super(threshold, strictMode, maxContextSize);
        this.model = model;
    }

    @Override
    public String name() {
        return "Image Helpfulness";
    }

    @Override
    protected ScoreReason evaluateImageReference(MllmImage image, String contextAbove, String contextBelow) {
        return evaluateImageHelpfulness(image, contextAbove, contextBelow);
    }

    protected ScoreReason evaluateImageHelpfulness(MllmImage image, String contextAbove, String contextBelow) {
        if (model == null) {
            throw new UnsupportedOperationException("Image Helpfulness generation requires a model provider");
        }
        return parseScoreReason(model.generate(prompt(image, contextAbove, contextBelow)));
    }

    private static String prompt(MllmImage image, String contextAbove, String contextBelow) {
        return """
                # Task Description
                You are a multi-modal document evaluation assistant. You will receive an image and its textual context.
                Your task is to evaluate the helpfulness of the image in enabling human readers to comprehend the text (context above and below) it accompanies.

                # Context Above
                %s

                # Context Below
                %s

                # Image
                [The image is provided below this section.]

                # Scoring Criteria
                Evaluate how well the image helps human readers understand the content of its accompanying text, assigning a score from 0 to 10.

                # Output Instructions
                Provide your evaluation in JSON with keys score and reasoning.

                Images: %s
                """.formatted(contextAbove, contextBelow, image);
    }
}
