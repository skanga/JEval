package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.ConversationalTestCase;
import dev.jeval.EvaluationModel;
import dev.jeval.Turn;
import dev.jeval.metrics.TopicAdherenceSchemas.QAPair;
import dev.jeval.metrics.TopicAdherenceSchemas.QAPairs;
import dev.jeval.metrics.TopicAdherenceSchemas.RelevancyVerdict;
import dev.jeval.metrics.TopicAdherenceSchemas.TopicAdherenceReason;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TopicAdherenceMetricTest {

    @Test
    void measureExtractsQaPairsScoresTruthTableAndGeneratesReason() {
        var model = new ScriptedModel(List.of(
                "{\"qa_pairs\":[{\"question\":\"Which food helps diabetics?\",\"response\":\"Oats help.\"}]}",
                "{\"verdict\":\"TP\",\"reason\":\"Relevant nutrition answer.\"}",
                "{\"reason\":\"The only QA pair stayed on nutrition.\"}"));
        var metric = new TopicAdherenceMetric(List.of("nutrition"), model);

        var result = metric.measure(ConversationalTestCase.builder(List.of(
                new Turn("user", "Which food helps diabetics?"),
                new Turn("assistant", "Oats help.")))
                .multimodal(true)
                .build());

        assertAll(
                () -> assertEquals("Topic Adherence", result.name()),
                () -> assertEquals(1.0, result.score()),
                () -> assertTrue(result.success()),
                () -> assertEquals("The only QA pair stayed on nutrition.", result.reason()),
                () -> assertEquals(1, metric.qaPairs().size()),
                () -> assertEquals(1, metric.verdicts().size()),
                () -> assertTrue(model.prompts().get(0).contains("question-answer (QA) pairs")),
                () -> assertTrue(model.prompts().get(0).contains("Do not infer information")),
                () -> assertTrue(model.prompts().get(0).contains("CHAIN OF THOUGHT")),
                () -> assertTrue(model.prompts().get(0).contains("Example JSON")),
                () -> assertTrue(model.prompts().get(0).contains("Is it better if I eat muesli instead of oats?")),
                () -> assertTrue(model.prompts().get(0).contains("value MUST be a list of dictionaries")),
                () -> assertTrue(model.prompts().get(0).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(0).contains("Which food helps diabetics?")),
                () -> assertTrue(model.prompts().get(1).contains("True Positive")),
                () -> assertTrue(model.prompts().get(1).contains("False Negative")),
                () -> assertTrue(model.prompts().get(1).contains("OUTPUT FORMAT")),
                () -> assertTrue(model.prompts().get(1).contains("CHAIN OF THOUGHT")),
                () -> assertTrue(model.prompts().get(1).contains("Example JSON")),
                () -> assertTrue(model.prompts().get(1).contains("two keys: 'verdict' and 'reason'")),
                () -> assertTrue(model.prompts().get(1).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(1).contains("nutrition")),
                () -> assertTrue(model.prompts().get(1).contains("Oats help.")),
                () -> assertTrue(model.prompts().get(2).contains("truth table entries")),
                () -> assertTrue(model.prompts().get(2).contains("Example JSON")),
                () -> assertTrue(model.prompts().get(2).contains("The score is <score>")),
                () -> assertTrue(model.prompts().get(2).contains("Output ONLY the reason")),
                () -> assertTrue(model.prompts().get(2).contains("Score calculation")),
                () -> assertTrue(model.prompts().get(2).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(2).contains("Relevant nutrition answer.")));
    }

    @Test
    void includeReasonFalseSkipsReasonGeneration() {
        var model = new ScriptedModel(List.of(
                "{\"qa_pairs\":[{\"question\":\"Which food helps?\",\"response\":\"Oats.\"}]}",
                "{\"verdict\":\"TN\",\"reason\":\"Off-topic refusal.\"}"));
        var metric = new TopicAdherenceMetric(List.of("nutrition"), model, 0.5, false, false);

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals(1.0, result.score()),
                () -> assertTrue(result.success()),
                () -> assertNull(result.reason()),
                () -> assertEquals(2, model.prompts().size()));
    }

    @Test
    void strictModeZerosScoresBelowThreshold() {
        var metric = new StubTopicAdherenceMetric(
                List.of(new QAPairs(List.of(
                        new QAPair("Relevant question", "Irrelevant answer"),
                        new QAPair("Off-topic question", "General answer")))),
                List.of(
                        new RelevancyVerdict("FN", "Relevant question was not answered."),
                        new RelevancyVerdict("FP", "Off-topic question was answered.")),
                new TopicAdherenceReason("Both pairs failed."),
                true);

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals(0.0, result.score()),
                () -> assertFalse(result.success()),
                () -> assertEquals(1.0, result.threshold()));
    }

    @Test
    void noQaPairsScoresZeroWithDefaultReason() {
        var metric = new StubTopicAdherenceMetric(
                List.of(new QAPairs(List.of())),
                List.of(),
                new TopicAdherenceReason("Unused."),
                false);

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals(0.0, result.score()),
                () -> assertFalse(result.success()),
                () -> assertTrue(result.reason().contains("no question-answer pairs")));
    }

    private static ConversationalTestCase testCase() {
        return ConversationalTestCase.builder(List.of(
                new Turn("user", "Which food helps diabetics?"),
                new Turn("assistant", "Oats help."))).build();
    }

    private static final class ScriptedModel implements EvaluationModel {
        private final List<String> responses;
        private final List<String> prompts = new ArrayList<>();

        ScriptedModel(List<String> responses) {
            this.responses = responses;
        }

        @Override
        public String generate(String prompt) {
            prompts.add(prompt);
            return responses.get(prompts.size() - 1);
        }

        List<String> prompts() {
            return prompts;
        }
    }

    private static final class StubTopicAdherenceMetric extends TopicAdherenceMetric {
        private final List<QAPairs> qaPairs;
        private final List<RelevancyVerdict> verdicts;
        private final TopicAdherenceReason reason;
        private int verdictIndex;

        StubTopicAdherenceMetric(
                List<QAPairs> qaPairs,
                List<RelevancyVerdict> verdicts,
                TopicAdherenceReason reason,
                boolean strictMode) {
            super(List.of("nutrition"), 0.5, true, strictMode);
            this.qaPairs = qaPairs;
            this.verdicts = verdicts;
            this.reason = reason;
        }

        @Override
        protected List<QAPairs> getQaPairs(List<List<Turn>> unitInteractions, boolean multimodal) {
            return qaPairs;
        }

        @Override
        protected RelevancyVerdict getQaVerdict(QAPair qaPair, boolean multimodal) {
            return verdicts.get(verdictIndex++);
        }

        @Override
        protected TopicAdherenceReason generateReason(boolean multimodal) {
            return reason;
        }
    }
}
