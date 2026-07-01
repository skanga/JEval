package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.EvaluationModel;
import dev.jeval.LlmTestCase;
import dev.jeval.MissingTestCaseParamsException;
import dev.jeval.metrics.SummarizationSchemas.SummarizationAlignmentVerdict;
import dev.jeval.metrics.SummarizationSchemas.SummarizationCoverageVerdict;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SummarizationMetricTest {

    @Test
    void measureUsesMinimumOfAlignmentAndCoverageScores() {
        var metric = new StubSummarizationMetric(
                List.of("What changed?", "Who approved it?"),
                List.of(
                        new SummarizationAlignmentVerdict("yes", null),
                        new SummarizationAlignmentVerdict("no", "Contradicted.")),
                List.of(
                        new SummarizationCoverageVerdict("yes", "yes", "What changed?"),
                        new SummarizationCoverageVerdict("no", "yes", "Who approved it?")));

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals("Summarization", result.name()),
                () -> assertEquals(0.5, result.score()),
                () -> assertEquals("reason", result.reason()),
                () -> assertEquals(0.5, metric.scoreBreakdown().get("Alignment")),
                () -> assertEquals(0.5, metric.scoreBreakdown().get("Coverage")),
                () -> assertEquals(List.of("truth"), metric.truths()),
                () -> assertEquals(List.of("claim"), metric.claims()));
    }

    @Test
    void coverageScoreIsOneWhenAssessmentQuestionsAreMissing() {
        var metric = new StubSummarizationMetric(
                null,
                List.of(new SummarizationAlignmentVerdict("yes", null)),
                List.of());

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals(1.0, result.score()),
                () -> assertEquals(1.0, metric.scoreBreakdown().get("Coverage")));
    }

    @Test
    void strictModeZerosScoresBelowPerfect() {
        var metric = new StubSummarizationMetric(
                List.of("What changed?"),
                List.of(
                        new SummarizationAlignmentVerdict("yes", null),
                        new SummarizationAlignmentVerdict("no", "Contradicted.")),
                List.of(new SummarizationCoverageVerdict("yes", "yes", "What changed?")),
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
                        () -> new SummarizationMetric(Double.NaN, 5, null, true, false, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SummarizationMetric(Double.POSITIVE_INFINITY, 5, null, true, false, null)));
    }

    @Test
    void measureAllowsEmptyInputLikeDeepEval() {
        var metric = new StubSummarizationMetric(null, List.of(), List.of());

        var result = metric.measure(LlmTestCase.builder("").actualOutput("summary").build());

        assertEquals(0.0, result.score());
    }

    @Test
    void measureRequiresActualOutput() {
        var metric = new StubSummarizationMetric(null, List.of(), List.of());

        assertThrows(MissingTestCaseParamsException.class,
                () -> metric.measure(LlmTestCase.builder("source").actualOutput("").build()));
    }

    @Test
    void measureUsesModelPromptsAndParsesJsonResponses() {
        var model = new ScriptedModel(List.of(
                "{\"truths\": [\"Source fact.\"]}",
                "{\"claims\": [\"Summary claim.\"]}",
                "{\"questions\": [\"Does the source include the fact?\"]}",
                "{\"answers\": [\"yes\"]}",
                "{\"answers\": [\"no\"]}",
                "{\"verdicts\": [{\"verdict\": \"yes\"}]}",
                "{\"reason\": \"The summary omits one source question.\"}"));
        var metric = new SummarizationMetric(model);

        var result = metric.measure(testCase(true));

        assertAll(
                () -> assertEquals(0.0, result.score()),
                () -> assertEquals("The summary omits one source question.", result.reason()),
                () -> assertEquals(List.of("Source fact."), metric.truths()),
                () -> assertEquals(List.of("Summary claim."), metric.claims()),
                () -> assertEquals(List.of("Does the source include the fact?"), metric.assessmentQuestions()),
                () -> assertEquals(1.0, metric.scoreBreakdown().get("Alignment")),
                () -> assertEquals(0.0, metric.scoreBreakdown().get("Coverage")),
                () -> assertTrue(model.prompts().get(0).contains("source")),
                () -> assertTrue(model.prompts().get(0).contains("excerpt (text and images)")),
                () -> assertTrue(model.prompts().get(1).contains("summary")),
                () -> assertTrue(model.prompts().get(1).contains("extract claims from all provided content")),
                () -> assertTrue(model.prompts().get(2).contains("source")),
                () -> assertTrue(model.prompts().get(2).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(3).contains("with the 'answers' key")),
                () -> assertTrue(model.prompts().get(3).contains("Example Answers")),
                () -> assertTrue(model.prompts().get(3).contains("length of 'answers' SHOULD BE STRICTLY EQUAL")),
                () -> assertTrue(model.prompts().get(3).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(3).contains("Does the source include the fact?")),
                () -> assertTrue(model.prompts().get(4).contains("summary")),
                () -> assertTrue(model.prompts().get(4).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(5).contains("with the 'verdicts' key")),
                () -> assertTrue(model.prompts().get(5).contains("Example Original Text")),
                () -> assertTrue(model.prompts().get(5).contains("YOU SHOULD NEVER USE YOUR PRIOR KNOWLEDGE")),
                () -> assertTrue(model.prompts().get(5).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(5).contains("Source fact.")),
                () -> assertTrue(model.prompts().get(6).contains("[Optional] questions")),
                () -> assertTrue(model.prompts().get(6).contains("with the 'reason' key")),
                () -> assertTrue(model.prompts().get(6).contains("Example JSON")),
                () -> assertTrue(model.prompts().get(6).contains("<summarization_score>")),
                () -> assertTrue(model.prompts().get(6).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(6).contains("DON'T mention anything and instead offer some praise")),
                () -> assertTrue(model.prompts().get(6).contains("Does the source include the fact?")));
    }

    private static LlmTestCase testCase() {
        return testCase(false);
    }

    private static LlmTestCase testCase(boolean multimodal) {
        return LlmTestCase.builder("source").actualOutput("summary").multimodal(multimodal).build();
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

    private static final class StubSummarizationMetric extends SummarizationMetric {
        private final List<SummarizationAlignmentVerdict> alignmentVerdicts;
        private final List<SummarizationCoverageVerdict> coverageVerdicts;

        StubSummarizationMetric(
                List<String> assessmentQuestions,
                List<SummarizationAlignmentVerdict> alignmentVerdicts,
                List<SummarizationCoverageVerdict> coverageVerdicts) {
            this(assessmentQuestions, alignmentVerdicts, coverageVerdicts, false);
        }

        StubSummarizationMetric(
                List<String> assessmentQuestions,
                List<SummarizationAlignmentVerdict> alignmentVerdicts,
                List<SummarizationCoverageVerdict> coverageVerdicts,
                boolean strictMode) {
            super(0.5, 5, assessmentQuestions, true, strictMode, null);
            this.alignmentVerdicts = alignmentVerdicts;
            this.coverageVerdicts = coverageVerdicts;
        }

        @Override
        protected List<String> generateTruths(String text) {
            return List.of("truth");
        }

        @Override
        protected List<String> generateClaims(String text) {
            return List.of("claim");
        }

        @Override
        protected List<SummarizationCoverageVerdict> generateCoverageVerdicts(LlmTestCase testCase) {
            return coverageVerdicts;
        }

        @Override
        protected List<SummarizationAlignmentVerdict> generateAlignmentVerdicts() {
            return alignmentVerdicts;
        }

        @Override
        protected String generateReason() {
            return "reason";
        }
    }
}
