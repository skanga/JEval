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

class ImageHelpfulnessMetricTest {

    @Test
    void measureAveragesImageScoresAndPassesNearestContextLikeDeepEval() {
        var first = new MllmImage("Zmlyc3Q=", "image/png");
        var second = new MllmImage("c2Vjb25k", "image/png");
        var testCase = LlmTestCase.builder("What is shown?")
                .actualOutput("above first " + first + " between " + second + " below second")
                .build();
        var metric = new StubImageHelpfulnessMetric(List.of(9.0, 5.0), List.of("first helpful", "second helpful"));

        MetricResult result = metric.measure(testCase);

        assertAll(
                () -> assertEquals("Image Helpfulness", result.name()),
                () -> assertEquals(0.7, result.score(), 1e-12),
                () -> assertFalse(result.success()),
                () -> assertTrue(result.reason().contains("Reason for image 0: first helpful")),
                () -> assertTrue(result.reason().contains("Reason for image 1: second helpful")),
                () -> assertEquals(List.of("above first ", " between "), metric.contextsAbove),
                () -> assertEquals(List.of(" between ", " below second"), metric.contextsBelow),
                () -> assertTrue(testCase.multimodal()));
    }

    @Test
    void measureRequiresImageInActualOutputWithMetricName() {
        var testCase = LlmTestCase.builder("What is shown?")
                .actualOutput("no image here")
                .build();
        var metric = new StubImageHelpfulnessMetric(List.of(), List.of());

        var error = assertThrows(IllegalArgumentException.class, () -> metric.measure(testCase));

        assertTrue(error.getMessage().contains("Image Helpfulness"));
    }

    @Test
    void measureTruncatesImageContextWhenMaxContextSizeIsSetLikeDeepEval() {
        var image = new MllmImage("Zmlyc3Q=", "image/png");
        var testCase = LlmTestCase.builder("What is shown?")
                .actualOutput("abcdef" + image + "ghijkl")
                .build();
        var metric = new StubImageHelpfulnessMetric(2, List.of(8.0), List.of("reason"));

        metric.measure(testCase);

        assertAll(
                () -> assertEquals(List.of("ef"), metric.contextsAbove),
                () -> assertEquals(List.of("gh"), metric.contextsBelow));
    }

    @Test
    void modelBackedMetricBuildsDeepEvalPromptAndParsesScoreReason() {
        var image = new MllmImage("aW1hZ2U=", "image/png");
        var model = new ScriptedModel("{\"score\": 8, \"reasoning\": \"Image improves comprehension.\"}");
        var metric = new ImageHelpfulnessMetric(model, 0.7, false, null);
        var testCase = LlmTestCase.builder("What is shown?")
                .actualOutput("Read this chart " + image + " for details")
                .build();

        var result = metric.measure(testCase);

        assertAll(
                () -> assertEquals(0.8, result.score(), 1e-12),
                () -> assertTrue(result.success()),
                () -> assertTrue(result.reason().contains("Image improves comprehension.")),
                () -> assertTrue(model.prompt.contains("# Context Above")),
                () -> assertTrue(model.prompt.contains("Read this chart ")),
                () -> assertTrue(model.prompt.contains(" for details")),
                () -> assertTrue(model.prompt.contains("Images: " + image)));
    }

    private static final class StubImageHelpfulnessMetric extends ImageHelpfulnessMetric {
        private final List<Double> scores;
        private final List<String> reasons;
        private final List<String> contextsAbove = new ArrayList<>();
        private final List<String> contextsBelow = new ArrayList<>();
        private int calls;

        private StubImageHelpfulnessMetric(List<Double> scores, List<String> reasons) {
            super(0.8, false);
            this.scores = scores;
            this.reasons = reasons;
        }

        private StubImageHelpfulnessMetric(int maxContextSize, List<Double> scores, List<String> reasons) {
            super(0.8, false, maxContextSize);
            this.scores = scores;
            this.reasons = reasons;
        }

        @Override
        protected ScoreReason evaluateImageHelpfulness(MllmImage image, String contextAbove, String contextBelow) {
            contextsAbove.add(contextAbove);
            contextsBelow.add(contextBelow);
            return new ScoreReason(scores.get(calls), reasons.get(calls++));
        }
    }

    private static final class ScriptedModel implements EvaluationModel {
        private final String response;
        private String prompt;

        private ScriptedModel(String response) {
            this.response = response;
        }

        @Override
        public String generate(String prompt) {
            this.prompt = prompt;
            return response;
        }
    }
}
