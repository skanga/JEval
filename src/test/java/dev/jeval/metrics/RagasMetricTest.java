package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.LlmTestCase;
import dev.jeval.MissingTestCaseParamsException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class RagasMetricTest {

    @Test
    void componentMetricsUseDeepEvalNamesAndDelegateToScorer() {
        var scorer = new RecordingRagasScorer(Map.of(
                "context_precision", 0.7,
                "context_recall", 0.8,
                "context_entity_recall", 0.9,
                "answer_relevancy", 0.6,
                "faithfulness", 0.5));

        var precision = new RAGASContextualPrecisionMetric(0.3, scorer).measure(testCase());
        var recall = new RAGASContextualRecallMetric(0.3, scorer).measure(testCase());
        var entityRecall = new RAGASContextualEntitiesRecall(0.3, scorer).measure(testCase());
        var answerRelevancy = new RAGASAnswerRelevancyMetric(0.3, scorer).measure(testCase());
        var faithfulness = new RAGASFaithfulnessMetric(0.3, scorer).measure(testCase());

        assertAll(
                () -> assertEquals("Contextual Precision (ragas)", precision.name()),
                () -> assertEquals(0.7, precision.score()),
                () -> assertEquals("Contextual Recall (ragas)", recall.name()),
                () -> assertEquals(0.8, recall.score()),
                () -> assertEquals("Contextual Entities Recall (ragas)", entityRecall.name()),
                () -> assertEquals(0.9, entityRecall.score()),
                () -> assertEquals("Answer Relevancy (ragas)", answerRelevancy.name()),
                () -> assertEquals(0.6, answerRelevancy.score()),
                () -> assertEquals("Faithfulness (ragas)", faithfulness.name()),
                () -> assertEquals(0.5, faithfulness.score()),
                () -> assertEquals(List.of(
                        "context_precision",
                        "context_recall",
                        "context_entity_recall",
                        "answer_relevancy",
                        "faithfulness"), scorer.metricKeys));
    }

    @Test
    void ragasMetricAveragesDeepEvalComponentMetricsAndStoresBreakdown() {
        var scorer = new RecordingRagasScorer(Map.of(
                "context_precision", 0.7,
                "context_recall", 0.8,
                "context_entity_recall", 0.9,
                "answer_relevancy", 0.6,
                "faithfulness", 0.5));
        var metric = new RagasMetric(0.75, scorer);

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals("RAGAS", result.name()),
                () -> assertEquals(0.7, result.score(), 1.0e-12),
                () -> assertEquals(0.75, result.threshold()),
                () -> assertEquals(false, result.success()),
                () -> assertEquals(Map.of(
                        "Contextual Precision (ragas)", 0.7,
                        "Contextual Recall (ragas)", 0.8,
                        "Contextual Entities Recall (ragas)", 0.9,
                        "Answer Relevancy (ragas)", 0.6,
                        "Faithfulness (ragas)", 0.5), metric.scoreBreakdown()));
    }

    @Test
    void asyncMeasureMatchesSynchronousRagasMetricBehaviorLikeDeepEval() throws Exception {
        var scorer = new RecordingRagasScorer(Map.of(
                "context_precision", 0.7,
                "context_recall", 0.8,
                "context_entity_recall", 0.9,
                "answer_relevancy", 0.6,
                "faithfulness", 0.5));

        var precision = new RAGASContextualPrecisionMetric(0.3, scorer)
                .aMeasure(testCase())
                .get(5, TimeUnit.SECONDS);
        var ragas = new RagasMetric(0.75, scorer)
                .aMeasure(testCase())
                .get(5, TimeUnit.SECONDS);

        assertAll(
                () -> assertEquals("Contextual Precision (ragas)", precision.name()),
                () -> assertEquals(0.7, precision.score()),
                () -> assertEquals("RAGAS", ragas.name()),
                () -> assertEquals(0.7, ragas.score(), 1.0e-12),
                () -> assertEquals(false, ragas.success()));
    }

    @Test
    void ragasComponentsRequireDeepEvalInputFields() {
        var missingActual = LlmTestCase.builder("question")
                .expectedOutput("truth")
                .retrievalContext(List.of("ctx"))
                .build();
        var missingExpected = LlmTestCase.builder("question")
                .actualOutput("answer")
                .retrievalContext(List.of("ctx"))
                .build();
        var missingRetrieval = LlmTestCase.builder("question")
                .actualOutput("answer")
                .expectedOutput("truth")
                .build();

        assertAll(
                () -> assertThrows(MissingTestCaseParamsException.class,
                        () -> new RAGASAnswerRelevancyMetric(0.3, fixedScorer()).measure(missingActual)),
                () -> assertThrows(MissingTestCaseParamsException.class,
                        () -> new RAGASContextualRecallMetric(0.3, fixedScorer()).measure(missingExpected)),
                () -> assertThrows(MissingTestCaseParamsException.class,
                        () -> new RAGASFaithfulnessMetric(0.3, fixedScorer()).measure(missingRetrieval)));
    }

    @Test
    void defaultRagasScorerReportsUnavailableExternalDependency() {
        var error = assertThrows(UnsupportedOperationException.class,
                () -> new RAGASFaithfulnessMetric().measure(testCase()));

        assertTrue(error.getMessage().contains("RAGAS"));
    }

    @Test
    void constructorsRejectNonFiniteThresholds() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RagasMetric(Double.NaN, fixedScorer())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RagasMetric(Double.POSITIVE_INFINITY, fixedScorer())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RAGASContextualPrecisionMetric(Double.NaN, fixedScorer())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RAGASContextualRecallMetric(Double.NaN, fixedScorer())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RAGASContextualEntitiesRecall(Double.NaN, fixedScorer())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RAGASAnswerRelevancyMetric(Double.NaN, fixedScorer())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RAGASFaithfulnessMetric(Double.NaN, fixedScorer())));
    }

    private static LlmTestCase testCase() {
        return LlmTestCase.builder("question")
                .actualOutput("answer")
                .expectedOutput("truth")
                .retrievalContext(List.of("ctx one", "ctx two"))
                .build();
    }

    private static RagasScorer fixedScorer() {
        return (metricKey, testCase) -> 1.0;
    }

    private static final class RecordingRagasScorer implements RagasScorer {
        private final Map<String, Double> scores;
        private final List<String> metricKeys = new java.util.ArrayList<>();

        RecordingRagasScorer(Map<String, Double> scores) {
            this.scores = scores;
        }

        @Override
        public double score(String metricKey, LlmTestCase testCase) {
            metricKeys.add(metricKey);
            return scores.get(metricKey);
        }
    }
}
