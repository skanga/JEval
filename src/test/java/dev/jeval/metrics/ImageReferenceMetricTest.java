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

class ImageReferenceMetricTest {

    @Test
    void measureAveragesImageScoresAndPassesNearestContextLikeDeepEval() {
        var first = new MllmImage("Zmlyc3Q=", "image/png");
        var second = new MllmImage("c2Vjb25k", "image/png");
        var testCase = LlmTestCase.builder("What is shown?")
                .actualOutput("above first " + first + " between " + second + " below second")
                .build();
        var metric = new StubImageReferenceMetric(List.of(8.0, 4.0), List.of("first reason", "second reason"));

        MetricResult result = metric.measure(testCase);

        assertAll(
                () -> assertEquals(0.6, result.score(), 1e-12),
                () -> assertFalse(result.success()),
                () -> assertTrue(result.reason().contains("Reason for image 0: first reason")),
                () -> assertTrue(result.reason().contains("Reason for image 1: second reason")),
                () -> assertEquals(List.of("above first ", " between "), metric.contextsAbove),
                () -> assertEquals(List.of(" between ", " below second"), metric.contextsBelow),
                () -> assertTrue(testCase.multimodal()));
    }

    @Test
    void measureTruncatesImageContextWhenMaxContextSizeIsSetLikeDeepEval() {
        var image = new MllmImage("Zmlyc3Q=", "image/png");
        var testCase = LlmTestCase.builder("What is shown?")
                .actualOutput("abcdef" + image + "ghijkl")
                .build();
        var metric = new StubImageReferenceMetric(3, List.of(8.0), List.of("reason"));

        metric.measure(testCase);

        assertAll(
                () -> assertEquals(List.of("def"), metric.contextsAbove),
                () -> assertEquals(List.of("ghi"), metric.contextsBelow));
    }

    @Test
    void modelBackedMetricBuildsDeepEvalPromptAndParsesScoreReason() {
        var image = new MllmImage("aW1hZ2U=", "image/png");
        var model = new ScriptedModel("{\"score\": 9, \"reasoning\": \"Image is explicitly referenced.\"}");
        var metric = new ImageReferenceMetric(model, 0.7, false, null);
        var testCase = LlmTestCase.builder("What is shown?")
                .actualOutput("Figure 1 shows " + image + " in detail")
                .build();

        var result = metric.measure(testCase);

        assertAll(
                () -> assertEquals(0.9, result.score(), 1e-12),
                () -> assertTrue(result.success()),
                () -> assertTrue(result.reason().contains("Image is explicitly referenced.")),
                () -> assertTrue(model.prompt.contains("# Context Above")),
                () -> assertTrue(model.prompt.contains("Figure 1 shows ")),
                () -> assertTrue(model.prompt.contains(" in detail")),
                () -> assertTrue(model.prompt.contains("Images: " + image)));
    }

    @Test
    void modelBackedMetricRejectsInvalidScoreReasonSchemaLikeDeepEval() {
        var image = new MllmImage("aW1hZ2U=", "image/png");
        var metric = new ImageReferenceMetric(new ScriptedModel("{\"score\": 9}"), 0.7, false, null);
        var testCase = LlmTestCase.builder("What is shown?")
                .actualOutput("Figure " + image)
                .build();

        assertThrows(IllegalArgumentException.class, () -> metric.measure(testCase));
    }

    private static final class StubImageReferenceMetric extends ImageReferenceMetric {
        private final List<Double> scores;
        private final List<String> reasons;
        private final List<String> contextsAbove = new ArrayList<>();
        private final List<String> contextsBelow = new ArrayList<>();
        private int calls;

        private StubImageReferenceMetric(List<Double> scores, List<String> reasons) {
            super(0.7, false);
            this.scores = scores;
            this.reasons = reasons;
        }

        private StubImageReferenceMetric(int maxContextSize, List<Double> scores, List<String> reasons) {
            super(0.7, false, maxContextSize);
            this.scores = scores;
            this.reasons = reasons;
        }

        @Override
        protected ScoreReason evaluateImageReference(MllmImage image, String contextAbove, String contextBelow) {
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
