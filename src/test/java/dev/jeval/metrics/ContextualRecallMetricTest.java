package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.EvaluationModel;
import dev.jeval.LlmTestCase;
import dev.jeval.MissingTestCaseParamsException;
import dev.jeval.metrics.ContextualRecallSchemas.VerdictWithExpectedOutput;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContextualRecallMetricTest {

    @Test
    void measureScoresYesVerdictsAsRecalled() {
        var metric = new StubContextualRecallMetric(List.of(
                new VerdictWithExpectedOutput("yes", "Supported by node 1.", "expected"),
                new VerdictWithExpectedOutput("no", "Not found.", "expected"),
                new VerdictWithExpectedOutput("idk", "Unclear.", "expected")));

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals("Contextual Recall", result.name()),
                () -> assertEquals(1.0 / 3.0, result.score()),
                () -> assertFalse(result.success()),
                () -> assertEquals("reason", result.reason()),
                () -> assertEquals(3, metric.verdicts().size()));
    }

    @Test
    void measureScoresZeroWhenThereAreNoVerdicts() {
        var result = new StubContextualRecallMetric(List.of()).measure(testCase());

        assertEquals(0.0, result.score());
    }

    @Test
    void strictModeZerosScoresBelowPerfect() {
        var metric = new StubContextualRecallMetric(List.of(
                new VerdictWithExpectedOutput("yes", "Supported.", "expected"),
                new VerdictWithExpectedOutput("no", "Missing.", "expected")),
                true);

        var result = metric.measure(LlmTestCase.builder("input")
                .expectedOutput("expected")
                .retrievalContext(List.of("context"))
                .multimodal(true)
                .build());

        assertAll(
                () -> assertEquals(0.0, result.score()),
                () -> assertFalse(result.success()),
                () -> assertEquals(1.0, result.threshold()));
    }

    @Test
    void measureAllowsEmptyInputAndExpectedOutputLikeDeepEval() {
        var metric = new StubContextualRecallMetric(List.of());

        assertAll(
                () -> assertEquals(0.0, metric.measure(LlmTestCase.builder("")
                        .expectedOutput("expected")
                        .retrievalContext(List.of("ctx"))
                        .build()).score()),
                () -> assertEquals(0.0, metric.measure(LlmTestCase.builder("input")
                        .expectedOutput("")
                        .retrievalContext(List.of("ctx"))
                        .build()).score()));
    }

    @Test
    void measureRequiresRetrievalContext() {
        var metric = new StubContextualRecallMetric(List.of());

        assertThrows(MissingTestCaseParamsException.class,
                () -> metric.measure(LlmTestCase.builder("input")
                        .expectedOutput("expected")
                        .build()));
    }

    @Test
    void measureUsesModelPromptsAndParsesJsonResponses() {
        var model = new ScriptedModel(List.of(
                "{\"verdicts\": [{\"verdict\": \"yes\", \"reason\": \"Supported by node 1.\"},"
                        + "{\"verdict\": \"no\", \"reason\": \"Not found.\"}]}",
                "{\"reason\": \"One expected fact was missing.\"}"));
        var metric = new ContextualRecallMetric(model);

        var result = metric.measure(LlmTestCase.builder("input")
                .expectedOutput("expected")
                .retrievalContext(List.of("context"))
                .multimodal(true)
                .build());

        assertAll(
                () -> assertEquals(0.5, result.score()),
                () -> assertEquals("One expected fact was missing.", result.reason()),
                () -> assertEquals(2, metric.verdicts().size()),
                () -> assertEquals("expected", metric.verdicts().getFirst().expectedOutput()),
                () -> assertTrue(model.prompts().get(0).contains("eg., 1st node")),
                () -> assertTrue(model.prompts().get(0).contains("\"verdicts\"")),
                () -> assertTrue(model.prompts().get(0).contains("expected")),
                () -> assertTrue(model.prompts().get(0).contains("context")),
                () -> assertTrue(model.prompts().get(1).contains("which is deduced directly from the \"expected output\"")),
                () -> assertTrue(model.prompts().get(1).contains("DO NOT mention 'supportive reasons'")),
                () -> assertTrue(model.prompts().get(1).contains("you should related supportive/unsupportive reasons")),
                () -> assertTrue(model.prompts().get(1).contains("and info regarding the node number")),
                () -> assertTrue(model.prompts().get(1).contains("node(s) in retrieval context)")),
                () -> assertTrue(model.prompts().get(1).contains("but don't overdo it, otherwise it gets annoying")),
                () -> assertTrue(model.prompts().get(1).contains("Not found.")));
    }

    private static LlmTestCase testCase() {
        return LlmTestCase.builder("input")
                .expectedOutput("expected")
                .retrievalContext(List.of("context"))
                .build();
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

    private static final class StubContextualRecallMetric extends ContextualRecallMetric {
        private final List<VerdictWithExpectedOutput> verdicts;

        StubContextualRecallMetric(List<VerdictWithExpectedOutput> verdicts) {
            this(verdicts, false);
        }

        StubContextualRecallMetric(List<VerdictWithExpectedOutput> verdicts, boolean strictMode) {
            super(0.5, true, strictMode);
            this.verdicts = verdicts;
        }

        @Override
        protected List<VerdictWithExpectedOutput> generateVerdicts(
                String expectedOutput, List<String> retrievalContext, boolean multimodal) {
            return verdicts;
        }

        @Override
        protected String generateReason(String expectedOutput, boolean multimodal) {
            return "reason";
        }
    }
}
