package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.EvaluationModel;
import dev.jeval.LlmTestCase;
import dev.jeval.MetricResult;
import dev.jeval.MllmImage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ImageEditingMetricTest {

    @Test
    void measureCombinesLowestScoresAndPassesInputImageLikeDeepEval() {
        var inputImage = new MllmImage("aW5wdXQ=", "image/png");
        var outputImage = new MllmImage("b3V0cHV0", "image/png");
        var testCase = LlmTestCase.builder("Edit this car " + inputImage + " to be red")
                .actualOutput("Here is the edited image " + outputImage)
                .build();
        var metric = new StubImageEditingMetric(List.of(9.0, 5.0), "edit matches", List.of(8.0, 4.0), "clean edit");

        MetricResult result = metric.measure(testCase);

        assertAll(
                () -> assertEquals("Image Editing", result.name()),
                () -> assertEquals(Math.sqrt(5.0 * 4.0) / 10.0, result.score()),
                () -> assertEquals("Edit this car \n to be red", metric.textPrompt),
                () -> assertSame(inputImage, metric.inputImage),
                () -> assertSame(outputImage, metric.outputImage),
                () -> assertTrue(result.reason().contains("lowest score from semantic consistency was 5.0")),
                () -> assertTrue(result.reason().contains("lowest score from perceptual quality was 4.0")),
                () -> assertFalse(result.success()));
    }

    @Test
    void strictModeZerosScoresBelowThresholdLikeDeepEval() {
        var inputImage = new MllmImage("aW5wdXQ=", "image/png");
        var outputImage = new MllmImage("b3V0cHV0", "image/png");
        var testCase = LlmTestCase.builder("Make it brighter " + inputImage)
                .actualOutput("Here is the edited image " + outputImage)
                .build();
        var metric = new StubImageEditingMetric(0.9, true,
                List.of(8.0), "edit matches", List.of(8.0), "clean edit");

        MetricResult result = metric.measure(testCase);

        assertAll(
                () -> assertEquals(0.0, result.score()),
                () -> assertFalse(result.success()));
    }

    @Test
    void measureRequiresExactlyOneInputImageLikeDeepEval() {
        var outputImage = new MllmImage("b3V0cHV0", "image/png");
        var testCase = LlmTestCase.builder("Make it brighter")
                .actualOutput("Here is the edited image " + outputImage)
                .build();
        var metric = new StubImageEditingMetric(List.of(8.0), "edit matches", List.of(8.0), "clean edit");

        var error = assertThrows(IllegalArgumentException.class, () -> metric.measure(testCase));

        assertTrue(error.getMessage().contains("1 input images"));
    }

    @Test
    void modelBackedMetricBuildsDeepEvalPromptsAndParsesScoreReasons() {
        var inputImage = new MllmImage("aW5wdXQ=", "image/png");
        var outputImage = new MllmImage("b3V0cHV0", "image/png");
        var model = new ScriptedModel(
                "{\"score\": [9, 8], \"reasoning\": \"Edit follows the instruction.\"}",
                "{\"score\": [7, 6], \"reasoning\": \"Image is mostly natural.\"}");
        var metric = new ImageEditingMetric(model, 0.7, false);
        var testCase = LlmTestCase.builder("Make the car red " + inputImage)
                .actualOutput("Here is the edited image " + outputImage)
                .build();

        var result = metric.measure(testCase);

        assertAll(
                () -> assertEquals(Math.sqrt(8.0 * 6.0) / 10.0, result.score(), 1e-12),
                () -> assertFalse(result.success()),
                () -> assertTrue(result.reason().contains("Edit follows the instruction.")),
                () -> assertTrue(result.reason().contains("Image is mostly natural.")),
                () -> assertEquals(2, model.prompts.size()),
                () -> assertTrue(model.prompts.get(0).contains("Two images will be provided")),
                () -> assertTrue(model.prompts.get(0).contains("Editing instruction: Make the car red")),
                () -> assertTrue(model.prompts.get(0).contains("Images: " + List.of(inputImage, outputImage))),
                () -> assertTrue(model.prompts.get(1).contains("image naturalness")),
                () -> assertTrue(model.prompts.get(1).contains("Images: " + outputImage)));
    }

    @Test
    void modelBackedMetricRejectsInvalidScoreReasonSchemaLikeDeepEval() {
        var inputImage = new MllmImage("aW5wdXQ=", "image/png");
        var outputImage = new MllmImage("b3V0cHV0", "image/png");
        var testCase = LlmTestCase.builder("Make the car red " + inputImage)
                .actualOutput("Here is the edited image " + outputImage)
                .build();

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ImageEditingMetric(new ScriptedModel(
                                "{\"score\": [9, 8]}",
                                "{\"score\": [8, 7], \"reasoning\": \"valid\"}"))
                                .measure(testCase)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ImageEditingMetric(new ScriptedModel(
                                "{\"score\": 9, \"reasoning\": \"not a list\"}",
                                "{\"score\": [8, 7], \"reasoning\": \"valid\"}"))
                                .measure(testCase)));
    }

    private static final class StubImageEditingMetric extends ImageEditingMetric {
        private final List<Double> semanticScores;
        private final String semanticReasoning;
        private final List<Double> perceptualScores;
        private final String perceptualReasoning;
        private String textPrompt;
        private MllmImage inputImage;
        private MllmImage outputImage;

        private StubImageEditingMetric(
                List<Double> semanticScores,
                String semanticReasoning,
                List<Double> perceptualScores,
                String perceptualReasoning) {
            this(0.5, false, semanticScores, semanticReasoning, perceptualScores, perceptualReasoning);
        }

        private StubImageEditingMetric(
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
        protected ScoreReason evaluateSemanticConsistency(
                String textPrompt,
                MllmImage inputImage,
                MllmImage actualImageOutput) {
            this.textPrompt = textPrompt;
            this.inputImage = inputImage;
            this.outputImage = actualImageOutput;
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
