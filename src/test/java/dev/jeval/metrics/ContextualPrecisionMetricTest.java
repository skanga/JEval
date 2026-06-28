package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.EvaluationModel;
import dev.jeval.LlmTestCase;
import dev.jeval.MissingTestCaseParamsException;
import dev.jeval.RetrievedContextData;
import dev.jeval.metrics.ContextualPrecisionSchemas.ContextualPrecisionVerdict;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContextualPrecisionMetricTest {

    @Test
    void measureUsesAveragePrecisionOverRankedVerdicts() {
        var metric = new StubContextualPrecisionMetric(List.of(
                new ContextualPrecisionVerdict("yes", "Useful first node."),
                new ContextualPrecisionVerdict("no", "Irrelevant second node."),
                new ContextualPrecisionVerdict("yes", "Useful third node.")));

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals("Contextual Precision", result.name()),
                () -> assertEquals(5.0 / 6.0, result.score(), 1.0e-12),
                () -> assertTrue(result.success()),
                () -> assertEquals("reason", result.reason()),
                () -> assertEquals(3, metric.verdicts().size()));
    }

    @Test
    void measureScoresZeroWhenThereAreNoRelevantVerdicts() {
        var result = new StubContextualPrecisionMetric(List.of(
                new ContextualPrecisionVerdict("no", "Unrelated.")))
                .measure(testCase());

        assertEquals(0.0, result.score());
    }

    @Test
    void strictModeZerosScoresBelowPerfect() {
        var metric = new StubContextualPrecisionMetric(List.of(
                new ContextualPrecisionVerdict("no", "Unrelated."),
                new ContextualPrecisionVerdict("yes", "Useful.")),
                true);

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals(0.0, result.score()),
                () -> assertFalse(result.success()),
                () -> assertEquals(1.0, result.threshold()));
    }

    @Test
    void measureAllowsEmptyInputAndExpectedOutputLikeDeepEval() {
        var metric = new StubContextualPrecisionMetric(List.of());

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
        var metric = new StubContextualPrecisionMetric(List.of());

        assertThrows(MissingTestCaseParamsException.class,
                () -> metric.measure(LlmTestCase.builder("input")
                        .expectedOutput("expected")
                        .build()));
    }

    @Test
    void measureUsesModelPromptsAndParsesJsonResponses() {
        var model = new ScriptedModel(List.of(
                "{\"verdicts\": [{\"verdict\": \"yes\", \"reason\": \"Useful node.\"},"
                        + "{\"verdict\": \"no\", \"reason\": \"Unrelated node.\"}]}",
                "{\"reason\": \"Useful nodes are ranked high.\"}"));
        var metric = new ContextualPrecisionMetric(model);

        var result = metric.measure(LlmTestCase.builder("input")
                .expectedOutput("expected")
                .retrievalContext(List.of("context one", "context two"))
                .multimodal(true)
                .build());

        assertAll(
                () -> assertEquals(1.0, result.score()),
                () -> assertEquals("Useful nodes are ranked high.", result.reason()),
                () -> assertEquals(2, metric.verdicts().size()),
                () -> assertTrue(model.prompts().get(0).contains("Example Retrieval Context")),
                () -> assertTrue(model.prompts().get(0).contains("Einstein won the Nobel Prize")),
                () -> assertTrue(model.prompts().get(0).contains("input")),
                () -> assertTrue(model.prompts().get(0).contains("expected")),
                () -> assertTrue(model.prompts().get(0).contains("context one")),
                () -> assertTrue(model.prompts().get(1).contains("DO NOT mention 'verdict'")),
                () -> assertTrue(model.prompts().get(1).contains("CONCISE summarize")),
                () -> assertTrue(model.prompts().get(1).contains("The term 'verdict' are")),
                () -> assertTrue(model.prompts().get(1).contains("node RANK")),
                () -> assertTrue(model.prompts().get(1).contains("it is nodes in retrieval context")),
                () -> assertTrue(model.prompts().get(1).contains("but don't overdo it, otherwise it gets annoying")),
                () -> assertTrue(model.prompts().get(1).contains("Unrelated node.")));
    }

    @Test
    void measureGroupsRetrievedContextDataBySourceLikeDeepEval() {
        var model = new ScriptedModel(List.of(
                "{\"verdicts\": [{\"verdict\": \"yes\", \"reason\": \"Grouped source.\"}]}",
                "{\"reason\": \"Grouped.\"}"));
        var metric = new ContextualPrecisionMetric(model);

        metric.measure(LlmTestCase.builder("input")
                .expectedOutput("expected")
                .retrievalContext(List.of(
                        new RetrievedContextData("chunk one", "policy.md"),
                        "standalone context",
                        new RetrievedContextData("chunk two", "policy.md")))
                .build());

        assertTrue(model.prompts().getFirst().contains("Source: policy.md\nchunk one\n---\nchunk two"));
    }

    private static LlmTestCase testCase() {
        return LlmTestCase.builder("input")
                .expectedOutput("expected")
                .retrievalContext(List.of("context one", "context two"))
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

    private static final class StubContextualPrecisionMetric extends ContextualPrecisionMetric {
        private final List<ContextualPrecisionVerdict> verdicts;

        StubContextualPrecisionMetric(List<ContextualPrecisionVerdict> verdicts) {
            this(verdicts, false);
        }

        StubContextualPrecisionMetric(List<ContextualPrecisionVerdict> verdicts, boolean strictMode) {
            super(0.5, true, strictMode);
            this.verdicts = verdicts;
        }

        @Override
        protected List<ContextualPrecisionVerdict> generateVerdicts(
                String input, String expectedOutput, List<String> retrievalContext, boolean multimodal) {
            return verdicts;
        }

        @Override
        protected String generateReason(String input, boolean multimodal) {
            return "reason";
        }
    }
}
