package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.EvaluationModel;
import dev.jeval.LlmTestCase;
import dev.jeval.MissingTestCaseParamsException;
import dev.jeval.metrics.RoleViolationSchemas.RoleViolationVerdict;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoleViolationMetricTest {

    @Test
    void constructorRequiresRole() {
        assertThrows(IllegalArgumentException.class, () -> new RoleViolationMetric(null));
    }

    @Test
    void measureReturnsZeroIfAnyVerdictIsViolation() {
        var metric = new StubRoleViolationMetric(List.of("violation"), List.of(
                new RoleViolationVerdict("no", "Fine."),
                new RoleViolationVerdict("yes", "Broke role.")));

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals("Role Violation", result.name()),
                () -> assertEquals(0.0, result.score()),
                () -> assertFalse(result.success()),
                () -> assertEquals("reason", result.reason()),
                () -> assertEquals(List.of("violation"), metric.roleViolations()),
                () -> assertEquals(2, metric.verdicts().size()));
    }

    @Test
    void noRoleViolationsReturnPerfectScore() {
        var result = new StubRoleViolationMetric(List.of(), List.of()).measure(testCase());

        assertAll(
                () -> assertEquals(1.0, result.score()),
                () -> assertTrue(result.success()));
    }

    @Test
    void constructorRejectsNonFiniteThresholds() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RoleViolationMetric("helpful assistant", Double.NaN, true, false)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RoleViolationMetric("helpful assistant", Double.POSITIVE_INFINITY, true, false)));
    }

    @Test
    void measureAllowsEmptyInputLikeDeepEval() {
        var metric = new StubRoleViolationMetric(List.of(), List.of());

        var result = metric.measure(LlmTestCase.builder("").actualOutput("output").build());

        assertEquals(1.0, result.score());
    }

    @Test
    void measureRequiresActualOutput() {
        var metric = new StubRoleViolationMetric(List.of(), List.of());

        assertThrows(MissingTestCaseParamsException.class,
                () -> metric.measure(LlmTestCase.builder("input").actualOutput("").build()));
    }

    @Test
    void measureUsesModelPromptsAndParsesJsonResponses() {
        var model = new ScriptedModel(List.of(
                "{\"role_violations\":[\"I am a licensed doctor.\"]}",
                "{\"verdicts\":[{\"verdict\":\"yes\",\"reason\":\"Claims medical authority.\"}]}",
                "{\"reason\":\"The output breaks the assistant role.\"}"));
        var metric = new RoleViolationMetric(model, "helpful assistant");

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals(0.0, result.score()),
                () -> assertEquals("The output breaks the assistant role.", result.reason()),
                () -> assertEquals(List.of("I am a licensed doctor."), metric.roleViolations()),
                () -> assertEquals(1, metric.verdicts().size()),
                () -> assertTrue(model.prompts().get(0).contains("Breaking character or assigned role")),
                () -> assertTrue(model.prompts().get(0).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(0).contains("'role_violations' key")),
                () -> assertTrue(model.prompts().get(0).contains("\"role_violations\": [\"Statement 1\", \"Statement 2\", ...]")),
                () -> assertTrue(model.prompts().get(0).contains("helpful assistant")),
                () -> assertTrue(model.prompts().get(0).contains("output")),
                () -> assertTrue(model.prompts().get(1).contains("Pretending to be something it's not")),
                () -> assertTrue(model.prompts().get(1).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(1).contains("'verdicts' key")),
                () -> assertTrue(model.prompts().get(1).contains("AI is pretending to be human")),
                () -> assertTrue(model.prompts().get(1).contains("I am a licensed doctor.")),
                () -> assertTrue(model.prompts().get(2).contains("comprehensive reason")),
                () -> assertTrue(model.prompts().get(2).contains(
                        "Based on the role violations identified: [Claims medical authority.], and the role violation score: 0.00")),
                () -> assertTrue(model.prompts().get(2).contains("'reason' key")),
                () -> assertTrue(model.prompts().get(2).contains("<role_violation_score>")),
                () -> assertTrue(model.prompts().get(2).contains("Claims medical authority.")));
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

    private static final class StubRoleViolationMetric extends RoleViolationMetric {
        private final List<String> roleViolations;
        private final List<RoleViolationVerdict> verdicts;

        StubRoleViolationMetric(List<String> roleViolations, List<RoleViolationVerdict> verdicts) {
            super("helpful assistant");
            this.roleViolations = roleViolations;
            this.verdicts = verdicts;
        }

        @Override
        protected List<String> detectRoleViolations(String actualOutput, boolean multimodal) {
            return roleViolations;
        }

        @Override
        protected List<RoleViolationVerdict> generateVerdicts() {
            return verdicts;
        }

        @Override
        protected String generateReason() {
            return "reason";
        }
    }
}
