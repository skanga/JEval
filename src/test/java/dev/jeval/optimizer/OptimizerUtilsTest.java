package dev.jeval.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jeval.ConversationalMetric;
import dev.jeval.DeepEvalException;
import dev.jeval.Golden;
import dev.jeval.Metric;
import dev.jeval.MetricResult;
import dev.jeval.prompt.ModelProvider;
import dev.jeval.prompt.ModelSettings;
import dev.jeval.prompt.OutputSchema;
import dev.jeval.prompt.OutputSchemaField;
import dev.jeval.prompt.OutputType;
import dev.jeval.prompt.Prompt;
import dev.jeval.prompt.PromptInterpolationType;
import dev.jeval.prompt.PromptMessage;
import dev.jeval.prompt.PromptType;
import dev.jeval.prompt.ReasoningEffort;
import dev.jeval.prompt.SchemaDataType;
import dev.jeval.prompt.Verbosity;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OptimizerUtilsTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void buildPromptConfigSnapshotsCopiesStoredPromptConfigurations() {
        var rootPrompt = new Prompt("root", "Root prompt");
        var childPrompt = new Prompt("child", "Child prompt");
        var rootPrompts = new LinkedHashMap<String, Prompt>();
        rootPrompts.put("generator", rootPrompt);
        var childPrompts = new LinkedHashMap<String, Prompt>();
        childPrompts.put("generator", childPrompt);
        var configs = new LinkedHashMap<String, PromptConfiguration>();
        configs.put("root", new PromptConfiguration("root", null, rootPrompts));
        configs.put("child", new PromptConfiguration("child", "root", childPrompts));

        var snapshots = OptimizerUtils.buildPromptConfigSnapshots(configs);

        configs.clear();
        childPrompts.put("judge", new Prompt("judge", "Score"));

        assertEquals(List.of("root", "child"), new ArrayList<>(snapshots.keySet()));
        assertEquals(null, snapshots.get("root").parent());
        assertEquals("root", snapshots.get("child").parent());
        assertSame(rootPrompt, snapshots.get("root").prompts().get("generator"));
        assertSame(childPrompt, snapshots.get("child").prompts().get("generator"));
        assertEquals(List.of("generator"), new ArrayList<>(snapshots.get("child").prompts().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> snapshots.clear());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshots.get("child").prompts().put("other", rootPrompt));
    }

    @Test
    void splitGoldensRejectsNegativeParetoSize() {
        assertThrows(IllegalArgumentException.class,
                () -> OptimizerUtils.splitGoldens(List.of("a"), -1, new Random(1)));
    }

    @Test
    void splitGoldensHandlesEmptyAndSingleItemInputs() {
        var empty = OptimizerUtils.splitGoldens(List.<String>of(), 2, new Random(1));
        var single = OptimizerUtils.splitGoldens(List.of("only"), 2, new Random(1));

        assertEquals(List.of(), empty.feedback());
        assertEquals(List.of(), empty.pareto());
        assertEquals(List.of(), single.feedback());
        assertEquals(List.of("only"), single.pareto());
    }

    @Test
    void splitGoldensSelectsDeterministicallyAndPreservesOriginalOrderWithinSplits() {
        var goldens = List.of("g0", "g1", "g2", "g3", "g4");

        var first = OptimizerUtils.splitGoldens(goldens, 2, new Random(7));
        var second = OptimizerUtils.splitGoldens(goldens, 2, new Random(7));

        assertEquals(first, second);
        assertEquals(3, first.feedback().size());
        assertEquals(2, first.pareto().size());
        assertEquals(new LinkedHashSet<>(goldens), union(first));
        assertEquals(orderByOriginal(goldens, first.feedback()), first.feedback());
        assertEquals(orderByOriginal(goldens, first.pareto()), first.pareto());
    }

    @Test
    void splitGoldensLeavesAtLeastOneFeedbackItemWhenPossible() {
        var split = OptimizerUtils.splitGoldens(List.of("a", "b", "c"), 99, new Random(2));

        assertEquals(1, split.feedback().size());
        assertEquals(2, split.pareto().size());
    }

    @Test
    void validateInstanceReturnsMatchingValueAndAllowsNullWhenRequested() {
        assertEquals("value", OptimizerUtils.validateInstance(
                "PromptOptimizer.optimize", "prompt", "value", false, String.class));
        assertNull(OptimizerUtils.validateInstance(
                "PromptOptimizer.optimize", "prompt", null, true, String.class));
    }

    @Test
    void validateInstanceRejectsWrongTypeWithDeepEvalStyleMessage() {
        var error = assertThrows(DeepEvalException.class, () -> OptimizerUtils.validateInstance(
                "PromptOptimizer.optimize", "prompt", 3, false, String.class));

        assertTrue(error.getMessage().contains("PromptOptimizer.optimize expected `prompt`"));
        assertTrue(error.getMessage().contains("String"));
        assertTrue(error.getMessage().contains("Integer"));
    }

    @Test
    void validateSequenceOfReturnsListAndValidatesEachItem() {
        var values = List.of("a", "b");

        assertSame(values, OptimizerUtils.validateSequenceOf(
                "Scorer", "goldens", values, false, String.class));

        var error = assertThrows(DeepEvalException.class, () -> OptimizerUtils.validateSequenceOf(
                "Scorer", "goldens", List.of("a", 1), false, String.class));
        assertTrue(error.getMessage().contains("element at index 1"));
        assertTrue(error.getMessage().contains("Integer"));
    }

    @Test
    void validateCallbackRequiresCallbackValue() {
        var callback = new Object();

        assertSame(callback, OptimizerUtils.validateCallback("Scorer", callback));

        var error = assertThrows(DeepEvalException.class,
                () -> OptimizerUtils.validateCallback("Scorer", null));
        assertTrue(error.getMessage().contains("Scorer requires a `model_callback`"));
    }

    @Test
    void validateIntInRangeReturnsValueAndRejectsBounds() {
        assertEquals(3, OptimizerUtils.validateIntInRange("GEPA", "pareto_size", 3, 1, 5));

        var low = assertThrows(DeepEvalException.class,
                () -> OptimizerUtils.validateIntInRange("GEPA", "pareto_size", 0, 1, 5));
        var high = assertThrows(DeepEvalException.class,
                () -> OptimizerUtils.validateIntInRange("GEPA", "pareto_size", 5, 1, 5));

        assertTrue(low.getMessage().contains("between 1 and 4"));
        assertTrue(high.getMessage().contains("between 1 and 4"));
    }

    @Test
    void validateMetricsCopiesSingleTurnAndConversationalMetrics() {
        Metric metric = testCase -> new MetricResult("metric", 1.0, 0.5, true, null);
        ConversationalMetric conversationalMetric =
                testCase -> new MetricResult("conversation", 1.0, 0.5, true, null);
        var metrics = new ArrayList<>(List.of(metric, conversationalMetric));

        var validated = OptimizerUtils.validateMetrics("Scorer", metrics);

        metrics.clear();

        assertEquals(2, validated.size());
        assertSame(metric, validated.get(0));
        assertSame(conversationalMetric, validated.get(1));
        assertThrows(UnsupportedOperationException.class, () -> validated.clear());
    }

    @Test
    void validateMetricsRequiresNonEmptyMetricList() {
        assertThrows(DeepEvalException.class, () -> OptimizerUtils.validateMetrics("Scorer", null));
        assertThrows(DeepEvalException.class, () -> OptimizerUtils.validateMetrics("Scorer", List.of()));

        var error = assertThrows(DeepEvalException.class,
                () -> OptimizerUtils.validateMetrics("Scorer", List.of("not a metric")));

        assertTrue(error.getMessage().contains("Scorer expected all elements of `metrics`"));
        assertTrue(error.getMessage().contains("String"));
    }

    @Test
    void invokeModelCallbackPassesPromptAndGoldenToCallback() {
        var prompt = new Prompt("answer", "Answer {question}");
        var golden = Golden.builder("question").expectedOutput("answer").build();
        ModelCallback callback = (givenPrompt, givenGolden) -> {
            assertSame(prompt, givenPrompt);
            assertSame(golden, givenGolden);
            return "actual answer";
        };

        assertEquals("actual answer", OptimizerUtils.invokeModelCallback(callback, prompt, golden));
    }

    @Test
    void parsePromptReturnsTextTemplate() {
        var prompt = new Prompt("answer", "Answer {question}");

        assertEquals("Answer {question}", OptimizerUtils.parsePrompt(prompt));
    }

    @Test
    void parsePromptSerializesMessageTemplate() throws Exception {
        var prompt = new Prompt("chat", List.of(
                new PromptMessage("system", "Be direct"),
                new PromptMessage("user", "{question}")),
                PromptInterpolationType.FSTRING);

        var parsed = MAPPER.readTree(OptimizerUtils.parsePrompt(prompt));

        assertTrue(parsed.isArray());
        assertEquals("system", parsed.get(0).get("role").asText());
        assertEquals("Be direct", parsed.get(0).get("content").asText());
        assertEquals("user", parsed.get(1).get("role").asText());
        assertEquals("{question}", parsed.get(1).get("content").asText());
    }

    @Test
    void createPromptRewritesTextAndPreservesSettings() {
        var settings = new ModelSettings(
                ModelProvider.OPEN_AI,
                "gpt-4.1",
                0.2,
                512,
                0.9,
                0.1,
                0.0,
                List.of("END"),
                ReasoningEffort.LOW,
                Verbosity.MEDIUM);
        var schema = new OutputSchema("schema-1", List.of(new OutputSchemaField(
                "field-1", SchemaDataType.STRING, "answer", "Final answer", true, null)), "Answer");
        var oldPrompt = new Prompt(
                "answer",
                "Old {question}",
                null,
                settings,
                OutputType.SCHEMA,
                schema,
                PromptInterpolationType.JINJA,
                "api-key",
                "main");

        var rewritten = OptimizerUtils.createPrompt(oldPrompt, "New {{ question }}");

        assertEquals("answer", rewritten.alias());
        assertEquals("New {{ question }}", rewritten.textTemplate());
        assertEquals(PromptType.TEXT, rewritten.type());
        assertNull(rewritten.messagesTemplate());
        assertSame(settings, rewritten.modelSettings());
        assertSame(schema, rewritten.outputSchema());
        assertEquals(OutputType.SCHEMA, rewritten.outputType());
        assertEquals(PromptInterpolationType.JINJA, rewritten.interpolationType());
        assertEquals("api-key", rewritten.confidentApiKey());
        assertEquals("main", rewritten.branch());
    }

    @Test
    void createPromptRewritesMessagesFromJson() {
        var oldPrompt = new Prompt("chat", List.of(new PromptMessage("system", "Old")),
                PromptInterpolationType.MUSTACHE);

        var rewritten = OptimizerUtils.createPrompt(
                oldPrompt,
                """
                [
                  {"role": "system", "content": "New"},
                  {"role": "user", "content": "{{question}}"}
                ]
                """);

        assertEquals(PromptType.LIST, rewritten.type());
        assertNull(rewritten.textTemplate());
        assertEquals(List.of(
                new PromptMessage("system", "New"),
                new PromptMessage("user", "{{question}}")), rewritten.messagesTemplate());
        assertEquals(PromptInterpolationType.MUSTACHE, rewritten.interpolationType());
    }

    @Test
    void createPromptRejectsInvalidMessageJson() {
        var oldPrompt = new Prompt("chat", List.of(new PromptMessage("system", "Old")),
                PromptInterpolationType.FSTRING);

        assertThrows(DeepEvalException.class, () -> OptimizerUtils.createPrompt(oldPrompt, "not json"));
        assertThrows(DeepEvalException.class, () -> OptimizerUtils.createPrompt(
                oldPrompt, "[{\"role\":\"system\",\"body\":\"missing content\"}]"));
    }

    private static Set<String> union(OptimizerUtils.GoldenSplit<String> split) {
        var values = new LinkedHashSet<String>();
        values.addAll(split.feedback());
        values.addAll(split.pareto());
        return values;
    }

    private static List<String> orderByOriginal(List<String> original, List<String> values) {
        var selected = new LinkedHashSet<>(values);
        return original.stream().filter(selected::contains).toList();
    }
}
