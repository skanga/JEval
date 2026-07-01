package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.ConversationalTestCase;
import dev.jeval.EvaluationModel;
import dev.jeval.Turn;
import dev.jeval.metrics.KnowledgeRetentionSchemas.Knowledge;
import dev.jeval.metrics.KnowledgeRetentionSchemas.KnowledgeRetentionScoreReason;
import dev.jeval.metrics.KnowledgeRetentionSchemas.KnowledgeRetentionVerdict;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KnowledgeRetentionMetricTest {

    @Test
    void measureExtractsKnowledgeScoresRetentionAndGeneratesReason() {
        var model = new ScriptedModel(List.of(
                "{\"data\":{\"Allergies\":[\"Peanuts\"]}}",
                "{\"verdict\":\"yes\",\"reason\":\"Asked for allergies when peanuts were known.\"}",
                "{\"reason\":\"The assistant forgot the known peanut allergy.\"}"));
        var metric = new KnowledgeRetentionMetric(model);

        var result = metric.measure(ConversationalTestCase.builder(List.of(
                new Turn("user", "I'm allergic to peanuts."),
                new Turn("assistant", "Are you allergic to anything?")))
                .multimodal(true)
                .build());

        assertAll(
                () -> assertEquals("Knowledge Retention", result.name()),
                () -> assertEquals(0.0, result.score()),
                () -> assertFalse(result.success()),
                () -> assertEquals("The assistant forgot the known peanut allergy.", result.reason()),
                () -> assertEquals(2, metric.knowledges().size()),
                () -> assertEquals(1, metric.verdicts().size()),
                () -> assertTrue(model.prompts().get(0).contains("most recent user message")),
                () -> assertTrue(model.prompts().get(0).contains("source of truth")),
                () -> assertTrue(model.prompts().get(0).contains("All keys must be **strings**")),
                () -> assertTrue(model.prompts().get(0).contains("**Example C**")),
                () -> assertTrue(model.prompts().get(0).contains("**Example D**")),
                () -> assertTrue(model.prompts().get(0).contains("Birth Year")),
                () -> assertTrue(model.prompts().get(0).contains("I'm allergic to peanuts.")),
                () -> assertTrue(model.prompts().get(1).contains("contradicts")),
                () -> assertTrue(model.prompts().get(1).contains("seeking clarification, confirmation, or correction")),
                () -> assertTrue(model.prompts().get(1).contains("Return a JSON object with:")),
                () -> assertTrue(model.prompts().get(1).contains("Only return a valid JSON. No extra commentary.")),
                () -> assertTrue(model.prompts().get(1).contains("**Example A**")),
                () -> assertTrue(model.prompts().get(1).contains("London trip was a holiday")),
                () -> assertTrue(model.prompts().get(1).contains("**Example B**")),
                () -> assertTrue(model.prompts().get(1).contains("\"verdict\": \"no\"")),
                () -> assertTrue(model.prompts().get(1).contains("**Example C**")),
                () -> assertTrue(model.prompts().get(1).contains("Are you allergic to anything?")),
                () -> assertTrue(model.prompts().get(1).contains("Peanuts")),
                () -> assertTrue(model.prompts().get(1).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(2).contains("list of attritions")),
                () -> assertTrue(model.prompts().get(2).contains("higher the better")),
                () -> assertTrue(model.prompts().get(2).contains("Example JSON")),
                () -> assertTrue(model.prompts().get(2).contains("<knowledge_retention_score>")),
                () -> assertTrue(model.prompts().get(2).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(2).contains("Asked for allergies when peanuts were known.")));
    }

    @Test
    void includeReasonFalseSkipsReasonGeneration() {
        var model = new ScriptedModel(List.of(
                "{\"data\":{\"Allergies\":[\"Peanuts\"]}}",
                "{\"verdict\":\"no\"}"));
        var metric = new KnowledgeRetentionMetric(model, 0.5, false, false);

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals(1.0, result.score()),
                () -> assertTrue(result.success()),
                () -> assertNull(result.reason()),
                () -> assertEquals(2, model.prompts().size()));
    }

    @Test
    void strictModeZerosScoresBelowThreshold() {
        var metric = new StubKnowledgeRetentionMetric(
                Arrays.asList(new Knowledge(Map.of("Allergies", List.of("Peanuts"))), null),
                List.of(new KnowledgeRetentionVerdict("yes", "Forgot peanuts.")),
                new KnowledgeRetentionScoreReason("Forgot peanuts."),
                true);

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals(0.0, result.score()),
                () -> assertFalse(result.success()),
                () -> assertEquals(1.0, result.threshold()));
    }

    @Test
    void constructorRejectsNonFiniteThresholds() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new KnowledgeRetentionMetric(Double.NaN, true, false)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new KnowledgeRetentionMetric(Double.POSITIVE_INFINITY, true, false)));
    }

    @Test
    void noVerdictsScoresOne() {
        var metric = new StubKnowledgeRetentionMetric(
                List.of(new Knowledge(Map.of("Allergies", List.of("Peanuts")))),
                List.of(),
                new KnowledgeRetentionScoreReason("No attritions."),
                false);

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals(1.0, result.score()),
                () -> assertTrue(result.success()));
    }

    private static ConversationalTestCase testCase() {
        return ConversationalTestCase.builder(List.of(
                new Turn("user", "I'm allergic to peanuts."),
                new Turn("assistant", "Are you allergic to anything?"))).build();
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

    private static final class StubKnowledgeRetentionMetric extends KnowledgeRetentionMetric {
        private final List<Knowledge> knowledges;
        private final List<KnowledgeRetentionVerdict> verdicts;
        private final KnowledgeRetentionScoreReason reason;

        StubKnowledgeRetentionMetric(
                List<Knowledge> knowledges,
                List<KnowledgeRetentionVerdict> verdicts,
                KnowledgeRetentionScoreReason reason,
                boolean strictMode) {
            super(0.5, true, strictMode);
            this.knowledges = knowledges;
            this.verdicts = verdicts;
            this.reason = reason;
        }

        @Override
        protected List<Knowledge> generateKnowledges(List<Turn> turns) {
            return knowledges;
        }

        @Override
        protected List<KnowledgeRetentionVerdict> generateVerdicts(List<Turn> turns, boolean multimodal) {
            return verdicts;
        }

        @Override
        protected KnowledgeRetentionScoreReason generateReason(boolean multimodal) {
            return reason;
        }
    }
}
