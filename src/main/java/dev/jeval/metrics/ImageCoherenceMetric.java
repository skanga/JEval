package dev.jeval.metrics;

import dev.jeval.EvaluationModel;
import dev.jeval.MllmImage;

public class ImageCoherenceMetric extends ImageReferenceMetric {
    private final EvaluationModel model;

    public ImageCoherenceMetric() {
        this(null);
    }

    public ImageCoherenceMetric(EvaluationModel model) {
        this(model, 0.5, false, null);
    }

    public ImageCoherenceMetric(double threshold, boolean strictMode) {
        this(null, threshold, strictMode, null);
    }

    public ImageCoherenceMetric(double threshold, boolean strictMode, Integer maxContextSize) {
        this(null, threshold, strictMode, maxContextSize);
    }

    public ImageCoherenceMetric(EvaluationModel model, double threshold, boolean strictMode, Integer maxContextSize) {
        super(threshold, strictMode, maxContextSize);
        this.model = model;
    }

    @Override
    public String name() {
        return "Image Coherence";
    }

    @Override
    protected ScoreReason evaluateImageReference(MllmImage image, String contextAbove, String contextBelow) {
        return evaluateImageCoherence(image, contextAbove, contextBelow);
    }

    protected ScoreReason evaluateImageCoherence(MllmImage image, String contextAbove, String contextBelow) {
        if (model == null) {
            throw new UnsupportedOperationException("Image Coherence generation requires a model provider");
        }
        return parseScoreReason(model.generate(prompt(image, contextAbove, contextBelow)));
    }

    private static String prompt(MllmImage image, String contextAbove, String contextBelow) {
        return """
                # Task Description
                You are a multi-modal document evaluation assistant. You will receive an image and its textual context.
                Your task is to evaluate the coherence between the image and the text (context above and below) it accompanies.

                # Context Above
                %s

                # Context Below
                %s

                # Image
                [The image is provided below this section.]

                # Scoring Criteria
                Assess how coherent the image is in relation to its accompanying text, assigning a score from 0 to 10.
                A higher score indicates stronger coherence between the image and the text. Be precise when assigning the score.

                # Output Instructions
                Provide your evaluation in JSON with keys score and reasoning.

                Images: %s
                """.formatted(contextAbove, contextBelow, image);
    }
}
