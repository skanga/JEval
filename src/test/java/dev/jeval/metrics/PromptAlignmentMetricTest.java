package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.EvaluationModel;
import dev.jeval.LlmTestCase;
import dev.jeval.MissingTestCaseParamsException;
import dev.jeval.metrics.PromptAlignmentSchemas.PromptAlignmentVerdict;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PromptAlignmentMetricTest {

    @Test
    void constructorRequiresPromptInstructions() {
        assertThrows(IllegalArgumentException.class, () -> new PromptAlignmentMetric(List.of()));
    }

    @Test
    void measureScoresNonNoVerdictsAsAligned() {
        var metric = new StubPromptAlignmentMetric(List.of(
                new PromptAlignmentVerdict("yes", null),
                new PromptAlignmentVerdict("no", "No bullets."),
                new PromptAlignmentVerdict("idk", null)));

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals("Prompt Alignment", result.name()),
                () -> assertEquals(2.0 / 3.0, result.score()),
                () -> assertTrue(result.success()),
                () -> assertEquals("reason", result.reason()),
                () -> assertEquals(3, metric.verdicts().size()));
    }

    @Test
    void emptyVerdictsReturnPerfectScore() {
        var result = new StubPromptAlignmentMetric(List.of()).measure(testCase());

        assertEquals(1.0, result.score());
    }

    @Test
    void strictModeZerosScoresBelowThreshold() {
        var metric = new StubPromptAlignmentMetric(List.of(
                new PromptAlignmentVerdict("yes", null),
                new PromptAlignmentVerdict("no", "No bullets.")),
                true);

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals(0.0, result.score()),
                () -> assertFalse(result.success()),
                () -> assertEquals(1.0, result.threshold()));
    }

    @Test
    void measureAllowsEmptyInputLikeDeepEval() {
        var metric = new StubPromptAlignmentMetric(List.of());

        var result = metric.measure(LlmTestCase.builder("").actualOutput("output").build());

        assertEquals(1.0, result.score());
    }

    @Test
    void measureRequiresActualOutput() {
        var metric = new StubPromptAlignmentMetric(List.of());

        assertThrows(MissingTestCaseParamsException.class,
                () -> metric.measure(LlmTestCase.builder("input").actualOutput("").build()));
    }

    @Test
    void measureUsesModelPromptsAndParsesJsonResponses() {
        var model = new ScriptedModel(List.of(
                "{\"verdicts\":[{\"verdict\":\"no\",\"reason\":\"Did not use bullets.\"}]}",
                "{\"reason\":\"The answer missed the bullet instruction.\"}"));
        var metric = new PromptAlignmentMetric(model, List.of("Use bullet points."));

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals(0.0, result.score()),
                () -> assertEquals("The answer missed the bullet instruction.", result.reason()),
                () -> assertEquals(1, metric.verdicts().size()),
                () -> assertTrue(model.prompts().getFirst().contains("STRICTLY be either a 'yes' or 'no'")),
                () -> assertTrue(model.prompts().getFirst().contains("EXTRA STRICT AND CAREFUL")),
                () -> assertTrue(model.prompts().getFirst().contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().getFirst().contains("Example input")),
                () -> assertTrue(model.prompts().getFirst().contains("Example prompt instructions")),
                () -> assertTrue(model.prompts().getFirst().contains("The LLM corrected the user")),
                () -> assertTrue(model.prompts().getFirst().contains("number of 'verdicts' SHOULD BE STRICTLY EQUAL")),
                () -> assertTrue(model.prompts().getFirst().contains("Use bullet points.")),
                () -> assertTrue(model.prompts().getFirst().contains("output")),
                () -> assertTrue(model.prompts().get(1).contains("provide a CONCISE reason")),
                () -> assertTrue(model.prompts().get(1).contains("why it is not higher")),
                () -> assertTrue(model.prompts().get(1).contains("don't overdo it otherwise it gets annoying")),
                () -> assertTrue(model.prompts().get(1).contains("ENTIRELY based on the unalignment reasons")),
                () -> assertTrue(model.prompts().get(1).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(1).contains("Example JSON")),
                () -> assertTrue(model.prompts().get(1).contains("Did not use bullets.")));
    }

    private static LlmTestCase testCase() {
        return LlmTestCase.builder("input").actualOutput("output").build();
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

    private static final class StubPromptAlignmentMetric extends PromptAlignmentMetric {
        private final List<PromptAlignmentVerdict> verdicts;

        StubPromptAlignmentMetric(List<PromptAlignmentVerdict> verdicts) {
            this(verdicts, false);
        }

        StubPromptAlignmentMetric(List<PromptAlignmentVerdict> verdicts, boolean strictMode) {
            super(List.of("Use bullet points."), 0.5, true, strictMode);
            this.verdicts = verdicts;
        }

        @Override
        protected List<PromptAlignmentVerdict> generateVerdicts(String input, String actualOutput) {
            return verdicts;
        }

        @Override
        protected String generateReason(String input, String actualOutput) {
            return "reason";
        }
    }
}
