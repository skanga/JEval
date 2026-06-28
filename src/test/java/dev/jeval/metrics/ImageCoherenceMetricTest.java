package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.EvaluationModel;
import dev.jeval.LlmTestCase;
import dev.jeval.MetricResult;
import dev.jeval.MllmImage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ImageCoherenceMetricTest {

    @Test
    void measureAveragesImageScoresAndPassesNearestContextLikeDeepEval() {
        var first = new MllmImage("Zmlyc3Q=", "image/png");
        var second = new MllmImage("c2Vjb25k", "image/png");
        var testCase = LlmTestCase.builder("What is shown?")
                .actualOutput("above first " + first + " between " + second + " below second")
                .build();
        var metric = new StubImageCoherenceMetric(List.of(7.0, 5.0), List.of("first coherent", "second coherent"));

        MetricResult result = metric.measure(testCase);

        assertAll(
                () -> assertEquals("Image Coherence", result.name()),
                () -> assertEquals(0.6, result.score(), 1e-12),
                () -> assertFalse(result.success()),
                () -> assertTrue(result.reason().contains("Reason for image 0: first coherent")),
                () -> assertTrue(result.reason().contains("Reason for image 1: second coherent")),
                () -> assertEquals(List.of("above first ", " between "), metric.contextsAbove),
                () -> assertEquals(List.of(" between ", " below second"), metric.contextsBelow),
                () -> assertTrue(testCase.multimodal()));
    }

    @Test
    void modelBackedMetricBuildsDeepEvalPromptAndParsesScoreReason() {
        var image = new MllmImage("aW1hZ2U=", "image/png");
        var model = new ScriptedModel("{\"score\": 8, \"reasoning\": \"Image matches the text.\"}");
        var metric = new ImageCoherenceMetric(model, 0.7, false, null);
        var testCase = LlmTestCase.builder("What is shown?")
                .actualOutput("Text above " + image + " text below")
                .build();

        var result = metric.measure(testCase);

        assertAll(
                () -> assertEquals(0.8, result.score(), 1e-12),
                () -> assertTrue(result.success()),
                () -> assertTrue(result.reason().contains("Image matches the text.")),
                () -> assertTrue(model.prompt.contains("# Context Above")),
                () -> assertTrue(model.prompt.contains("Text above ")),
                () -> assertTrue(model.prompt.contains(" text below")),
                () -> assertTrue(model.prompt.contains("Images: " + image)));
    }

    private static final class StubImageCoherenceMetric extends ImageCoherenceMetric {
        private final List<Double> scores;
        private final List<String> reasons;
        private final List<String> contextsAbove = new ArrayList<>();
        private final List<String> contextsBelow = new ArrayList<>();
        private int calls;

        private StubImageCoherenceMetric(List<Double> scores, List<String> reasons) {
            super(0.7, false);
            this.scores = scores;
            this.reasons = reasons;
        }

        @Override
        protected ScoreReason evaluateImageCoherence(MllmImage image, String contextAbove, String contextBelow) {
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
