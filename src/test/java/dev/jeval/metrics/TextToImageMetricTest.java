package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.EvaluationModel;
import dev.jeval.LlmTestCase;
import dev.jeval.MetricResult;
import dev.jeval.MllmImage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TextToImageMetricTest {

    @Test
    void measureCombinesLowestSemanticAndPerceptualScoresLikeDeepEval() {
        var image = new MllmImage("YWJj", "image/png");
        var testCase = LlmTestCase.builder("Draw a red car")
                .actualOutput("Here is the image " + image)
                .build();
        var metric = new StubTextToImageMetric(List.of(8.0, 6.0), "matches prompt", List.of(9.0, 4.0), "clear image");

        MetricResult result = metric.measure(testCase);

        assertAll(
                () -> assertEquals(Math.sqrt(6.0 * 4.0) / 10.0, result.score()),
                () -> assertTrue(result.reason().contains("lowest score from semantic consistency was 6.0")),
                () -> assertTrue(result.reason().contains("lowest score from perceptual quality was 4.0")),
                () -> assertTrue(testCase.multimodal()),
                () -> assertFalse(result.success()));
    }

    @Test
    void strictModeZerosScoresBelowThresholdLikeDeepEval() {
        var image = new MllmImage("YWJj", "image/png");
        var testCase = LlmTestCase.builder("Draw a red car")
                .actualOutput("Here is the image " + image)
                .build();
        var metric = new StubTextToImageMetric(0.9, true,
                List.of(8.0), "matches prompt", List.of(8.0), "clear image");

        MetricResult result = metric.measure(testCase);

        assertAll(
                () -> assertEquals(0.0, result.score()),
                () -> assertFalse(result.success()));
    }

    @Test
    void measureRequiresZeroInputImagesLikeDeepEval() {
        var inputImage = new MllmImage("aW5wdXQ=", "image/png");
        var outputImage = new MllmImage("b3V0cHV0", "image/png");
        var testCase = LlmTestCase.builder("Draw this differently " + inputImage)
                .actualOutput("Here is the image " + outputImage)
                .build();
        var metric = new StubTextToImageMetric(List.of(8.0), "matches prompt", List.of(8.0), "clear image");

        var error = assertThrows(IllegalArgumentException.class, () -> metric.measure(testCase));

        assertTrue(error.getMessage().contains("0 input images"));
    }

    @Test
    void modelBackedMetricBuildsDeepEvalPromptsAndParsesScoreReasons() {
        var image = new MllmImage("YWJj", "image/png");
        var model = new ScriptedModel(
                "{\"score\": [8], \"reasoning\": \"Image follows the prompt.\"}",
                "{\"score\": [9, 7], \"reasoning\": \"Image quality is good.\"}");
        var metric = new TextToImageMetric(model, 0.7, false);
        var testCase = LlmTestCase.builder("Draw a red car")
                .actualOutput("Here is the image " + image)
                .build();

        var result = metric.measure(testCase);

        assertAll(
                () -> assertEquals(Math.sqrt(8.0 * 7.0) / 10.0, result.score(), 1e-12),
                () -> assertTrue(result.success()),
                () -> assertTrue(result.reason().contains("Image follows the prompt.")),
                () -> assertTrue(result.reason().contains("Image quality is good.")),
                () -> assertEquals(2, model.prompts.size()),
                () -> assertTrue(model.prompts.get(0).contains("image is an AI-generated image according to the text prompt")),
                () -> assertTrue(model.prompts.get(0).contains("Text Prompt: Draw a red car")),
                () -> assertTrue(model.prompts.get(0).contains("Images: " + image)),
                () -> assertTrue(model.prompts.get(1).contains("image naturalness")),
                () -> assertTrue(model.prompts.get(1).contains("Images: " + image)));
    }

    @Test
    void modelBackedMetricRejectsInvalidScoreReasonSchemaLikeDeepEval() {
        var image = new MllmImage("YWJj", "image/png");
        var testCase = LlmTestCase.builder("Draw a red car")
                .actualOutput("Here is the image " + image)
                .build();

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new TextToImageMetric(new ScriptedModel(
                                "{\"score\": [8]}",
                                "{\"score\": [9, 7], \"reasoning\": \"valid\"}"))
                                .measure(testCase)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new TextToImageMetric(new ScriptedModel(
                                "{\"score\": 8, \"reasoning\": \"not a list\"}",
                                "{\"score\": [9, 7], \"reasoning\": \"valid\"}"))
                                .measure(testCase)));
    }

    @Test
    void constructorRejectsNonFiniteThresholds() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new TextToImageMetric(Double.NaN, false)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new TextToImageMetric(Double.POSITIVE_INFINITY, false)));
    }

    private static final class StubTextToImageMetric extends TextToImageMetric {
        private final List<Double> semanticScores;
        private final String semanticReasoning;
        private final List<Double> perceptualScores;
        private final String perceptualReasoning;

        private StubTextToImageMetric(
                List<Double> semanticScores,
                String semanticReasoning,
                List<Double> perceptualScores,
                String perceptualReasoning) {
            this(0.5, false, semanticScores, semanticReasoning, perceptualScores, perceptualReasoning);
        }

        private StubTextToImageMetric(
                double threshold,
                boolean strictMode,
                List<Double> semanticScores,
                String semanticReasoning,
                List<Double> perceptualScores,
                String perceptualReasoning) {
            super(threshold, strictMode);
            this.semanticScores = semanticScores;
            this.semanticReasoning = semanticReasoning;
            this.perceptualScores = perceptualScores;
            this.perceptualReasoning = perceptualReasoning;
        }

        @Override
        protected ScoreReason evaluateSemanticConsistency(String textPrompt, MllmImage actualImageOutput) {
            return new ScoreReason(semanticScores, semanticReasoning);
        }

        @Override
        protected ScoreReason evaluatePerceptualQuality(MllmImage actualImageOutput) {
            return new ScoreReason(perceptualScores, perceptualReasoning);
        }
    }

    private static final class ScriptedModel implements EvaluationModel {
        private final List<String> responses;
        private final List<String> prompts = new ArrayList<>();

        private ScriptedModel(String... responses) {
            this.responses = List.of(responses);
        }

        @Override
        public String generate(String prompt) {
            prompts.add(prompt);
            return responses.get(prompts.size() - 1);
        }
    }
}
