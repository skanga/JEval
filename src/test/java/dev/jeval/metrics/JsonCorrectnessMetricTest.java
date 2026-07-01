package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.EvaluationModel;
import dev.jeval.Evaluator;
import dev.jeval.LlmTestCase;
import dev.jeval.MissingTestCaseParamsException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class JsonCorrectnessMetricTest {

    @Test
    void measureSucceedsWhenActualOutputMatchesExpectedSchema() {
        var metric = new JsonCorrectnessMetric(ExampleSchema.class);
        var testCase = new LlmTestCase("Return user JSON", "{\"name\":\"Ada\"}", null);

        var result = metric.measure(testCase);

        assertAll(
                () -> assertEquals("Json Correctness", result.name()),
                () -> assertEquals(1.0, result.score()),
                () -> assertTrue(result.success()),
                () -> assertEquals(JsonCorrectnessMetric.DEFAULT_CORRECT_REASON, result.reason()));
    }

    @Test
    void measureFailsWhenActualOutputDoesNotMatchExpectedSchema() {
        var metric = new JsonCorrectnessMetric(ExampleSchema.class, false);
        var testCase = new LlmTestCase("Return user JSON", "{\"name\":null}", null);

        var result = metric.measure(testCase);

        assertAll(
                () -> assertEquals(0.0, result.score()),
                () -> assertFalse(result.success()),
                () -> assertEquals(null, result.reason()));
    }

    @Test
    void measureIncludesReasonWhenActualOutputDoesNotMatchExpectedSchema() {
        var metric = new JsonCorrectnessMetric(ExampleSchema.class);
        var testCase = new LlmTestCase("Return user JSON", "{\"name\":null}", null);

        var result = metric.measure(testCase);

        assertAll(
                () -> assertEquals(0.0, result.score()),
                () -> assertFalse(result.success()),
                () -> assertEquals("The generated Json does not match the expected schema.", result.reason()));
    }

    @Test
    void measureUsesModelToExplainInvalidJsonWhenModelIsProvided() {
        var model = new ScriptedModel("{\"reason\":\"The name field is null but must be a string.\"}");
        var metric = new JsonCorrectnessMetric(model, ExampleSchema.class);

        var result = metric.measure(new LlmTestCase("Return user JSON", "{\"name\":null}", null));

        assertAll(
                () -> assertEquals(0.0, result.score()),
                () -> assertFalse(result.success()),
                () -> assertEquals("The name field is null but must be a string.", result.reason()),
                () -> assertEquals(1, model.prompts().size()),
                () -> assertTrue(model.prompts().getFirst().contains("Generated Json:\n{\"name\":null}")),
                () -> assertTrue(model.prompts().getFirst().contains("Expected Json Schema:")),
                () -> assertTrue(model.prompts().getFirst().contains("\"type\":\"object\"")),
                () -> assertTrue(model.prompts().getFirst().contains("\"name\":{\"type\":\"string\"}")),
                () -> assertTrue(model.prompts().getFirst().contains("\"required\":[\"name\"]")),
                () -> assertTrue(model.prompts().getFirst().contains("Is Valid Json?\nfalse")));
    }

    @Test
    void measureRejectsInvalidModelReasonLikePydanticSchema() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new JsonCorrectnessMetric(new ScriptedModel("{}"), ExampleSchema.class)
                                .measure(new LlmTestCase("Return user JSON", "{\"name\":null}", null))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new JsonCorrectnessMetric(new ScriptedModel("{\"reason\":1}"), ExampleSchema.class)
                                .measure(new LlmTestCase("Return user JSON", "{\"name\":null}", null))));
    }

    @Test
    void measureIncludesMultimodalRulesInModelReasonPrompt() {
        var model = new ScriptedModel("{\"reason\":\"The generated JSON is not valid.\"}");
        var metric = new JsonCorrectnessMetric(model, ExampleSchema.class);

        metric.measure(LlmTestCase.builder("Return user JSON")
                .actualOutput("{\"name\":null}")
                .multimodal(true)
                .build());

        assertTrue(model.prompts().getFirst().contains("--- MULTIMODAL INPUT RULES ---"));
    }

    @Test
    void measureFailsWhenActualOutputIsNotValidJson() {
        var metric = new JsonCorrectnessMetric(ExampleSchema.class, false);
        var testCase = new LlmTestCase("Return user JSON", "{'name':'Ada'}", null);

        assertFalse(metric.measure(testCase).success());
    }

    @Test
    void evaluateCanRunJsonCorrectnessMetric() {
        var result = Evaluator.evaluate(
                new LlmTestCase("Return user JSON", "{\"name\":\"Ada\"}", null),
                List.of(new JsonCorrectnessMetric(ExampleSchema.class)));

        assertTrue(result.success());
    }

    @Test
    void measureRequiresActualOutput() {
        assertThrows(MissingTestCaseParamsException.class,
                () -> new JsonCorrectnessMetric(ExampleSchema.class).measure(new LlmTestCase("Return JSON", "", null)));
    }

    @Test
    void measureAllowsEmptyInputLikeDeepEval() {
        var result = new JsonCorrectnessMetric(ExampleSchema.class)
                .measure(new LlmTestCase("", "{\"name\":\"Ada\"}", null));

        assertTrue(result.success());
    }

    @Test
    void constructorRejectsNonFiniteThresholds() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new JsonCorrectnessMetric(ExampleSchema.class, true, false, Double.NaN)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new JsonCorrectnessMetric(
                                ExampleSchema.class,
                                true,
                                false,
                                Double.POSITIVE_INFINITY)));
    }

    private static final class ScriptedModel implements EvaluationModel {
        private final String response;
        private final List<String> prompts = new ArrayList<>();

        ScriptedModel(String response) {
            this.response = response;
        }

        @Override
        public String generate(String prompt) {
            prompts.add(prompt);
            return response;
        }

        List<String> prompts() {
            return prompts;
        }
    }

    private record ExampleSchema(String name) {
    }
}
