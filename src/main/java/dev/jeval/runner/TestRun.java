package dev.jeval.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jeval.ConversationalApiTestCase;
import dev.jeval.ConversationalTestCase;
import dev.jeval.LlmApiTestCase;
import dev.jeval.LlmTestCase;
import dev.jeval.MetricData;
import dev.jeval.Utils;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.BiFunction;
import java.util.function.Function;

public record TestRun(
        String testFile,
        List<LlmApiTestCase> testCases,
        List<ConversationalApiTestCase> conversationalTestCases,
        List<MetricScores> metricsScores,
        TraceMetricScores traceMetricsScores,
        String identifier,
        Map<String, Object> hyperparameters,
        List<PromptData> prompts,
        Integer testPassed,
        Integer testFailed,
        double runDuration,
        Double evaluationCost,
        String datasetAlias,
        String datasetId,
        boolean official) {
    private static final ObjectMapper JSON = new ObjectMapper();

    public TestRun() {
        this(null, null, null, null, null, null, null, null, null, null, 0.0, null, null, null, false);
    }

    public TestRun {
        testCases = testCases == null ? List.of() : List.copyOf(testCases);
        conversationalTestCases =
                conversationalTestCases == null ? List.of() : List.copyOf(conversationalTestCases);
        metricsScores = metricsScores == null ? List.of() : List.copyOf(metricsScores);
        hyperparameters = hyperparameters == null
                ? null
                : Collections.unmodifiableMap(new LinkedHashMap<>(hyperparameters));
        prompts = prompts == null ? null : List.copyOf(prompts);
    }

    public TestRun addTestCase(LlmApiTestCase apiTestCase) {
        var updatedTestCases = append(testCases, apiTestCase);
        return copyWith(updatedTestCases, conversationalTestCases, addEvaluationCost(apiTestCase.evaluationCost()));
    }

    public TestRun addTestCase(ConversationalApiTestCase apiTestCase) {
        var updatedConversationalTestCases = append(conversationalTestCases, apiTestCase);
        return copyWith(testCases, updatedConversationalTestCases, addEvaluationCost(apiTestCase.evaluationCost()));
    }

    public Map<String, Object> modelDump() {
        return modelDump(false, false);
    }

    public Map<String, Object> modelDump(boolean byAlias) {
        return modelDump(byAlias, false);
    }

    public Map<String, Object> modelDump(boolean byAlias, boolean excludeNulls) {
        var dump = new LinkedHashMap<String, Object>();
        put(dump, key("test_file", "testFile", byAlias), testFile, excludeNulls);
        put(dump, key("test_cases", "testCases", byAlias), dumpLlmApiTestCases(byAlias), excludeNulls);
        put(dump, key("conversational_test_cases", "conversationalTestCases", byAlias),
                dumpConversationalApiTestCases(byAlias), excludeNulls);
        put(dump, key("metrics_scores", "metricsScores", byAlias), new ArrayList<>(metricsScores), excludeNulls);
        put(dump, key("trace_metrics_scores", "traceMetricsScores", byAlias), dumpTraceMetricScores(), excludeNulls);
        put(dump, "identifier", identifier, excludeNulls);
        put(dump, "hyperparameters", hyperparameters, excludeNulls);
        put(dump, "prompts", prompts == null ? null : new ArrayList<>(prompts), excludeNulls);
        put(dump, key("test_passed", "testPassed", byAlias), testPassed, excludeNulls);
        put(dump, key("test_failed", "testFailed", byAlias), testFailed, excludeNulls);
        put(dump, key("run_duration", "runDuration", byAlias), runDuration, excludeNulls);
        put(dump, key("evaluation_cost", "evaluationCost", byAlias), evaluationCost, excludeNulls);
        put(dump, key("dataset_alias", "datasetAlias", byAlias), datasetAlias, excludeNulls);
        put(dump, key("dataset_id", "datasetId", byAlias), datasetId, excludeNulls);
        put(dump, "official", official, excludeNulls);
        return dump;
    }

    public TestRun save(Path file) throws IOException {
        var bytes = Utils.serializeToJson(modelDump(true, true)).getBytes(StandardCharsets.UTF_8);
        try (var channel = FileChannel.open(
                file, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            var buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
        return this;
    }

    public static TestRun load(Path file) throws IOException {
        return JSON.readValue(file.toFile(), TestRun.class);
    }

    public void guardMllmTestCases() {
        for (var testCase : testCases) {
            if (testCase.isMultimodal()) {
                throw new IllegalArgumentException("Unable to send multimodal test cases to Confident AI.");
            }
        }
    }

    public MetricsScoresAggregation constructMetricsScores() {
        var aggregators = new LinkedHashMap<String, MetricScoresAggregator>();
        var traceAggregators = TraceMetricScoresAggregator.empty();
        var validScores = 0;

        for (var testCase : testCases) {
            validScores += aggregateMetricData(testCase.metricsData(), aggregators);
            validScores += aggregateTraceMetricData(testCase.trace(), aggregators, traceAggregators);
        }
        for (var testCase : conversationalTestCases) {
            validScores += aggregateMetricData(testCase.metricsData(), aggregators);
        }

        var updatedMetricsScores = aggregators.values().stream()
                .map(MetricScoresAggregator::toMetricScores)
                .toList();
        return new MetricsScoresAggregation(
                copyWithMetricsScores(updatedMetricsScores, traceAggregators.toTraceMetricScores()), validScores);
    }

    public TestRun sortTestCases() {
        return copyWithSortedTestCases(
                assignUniqueOrders(sortByOrder(testCases), LlmApiTestCase::order, LlmApiTestCase::withOrder),
                assignUniqueOrders(
                        sortByOrder(conversationalTestCases),
                        ConversationalApiTestCase::order,
                        ConversationalApiTestCase::withOrder));
    }

    public TestRun setDatasetProperties(LlmTestCase testCase) {
        return copyWithDatasetProperties(
                datasetAlias == null ? testCase.datasetAlias() : datasetAlias,
                datasetId == null ? testCase.datasetId() : datasetId);
    }

    public TestRun setDatasetProperties(ConversationalTestCase testCase) {
        return copyWithDatasetProperties(
                datasetAlias == null ? testCase.datasetAlias() : datasetAlias,
                datasetId == null ? testCase.datasetId() : datasetId);
    }

    public TestRun calculateTestPassesAndFails() {
        var passed = 0;
        var failed = 0;
        for (var testCase : testCases) {
            if (testCase.success() == null) {
                continue;
            }
            if (testCase.success()) {
                passed++;
            } else {
                failed++;
            }
        }
        for (var testCase : conversationalTestCases) {
            if (testCase.success() == null) {
                continue;
            }
            if (testCase.success()) {
                passed++;
            } else {
                failed++;
            }
        }
        return copyWithPassFailCounts(passed, failed);
    }

    private Double addEvaluationCost(Double additional) {
        if (additional == null) {
            return evaluationCost;
        }
        return evaluationCost == null ? additional : evaluationCost + additional;
    }

    private List<Map<String, Object>> dumpLlmApiTestCases(boolean byAlias) {
        return new ArrayList<>(testCases.stream().map(testCase -> testCase.modelDump(byAlias)).toList());
    }

    private List<Map<String, Object>> dumpConversationalApiTestCases(boolean byAlias) {
        return new ArrayList<>(
                conversationalTestCases.stream().map(testCase -> testCase.modelDump(byAlias)).toList());
    }

    private Map<String, Object> dumpTraceMetricScores() {
        if (traceMetricsScores == null) {
            return null;
        }
        var dump = new LinkedHashMap<String, Object>();
        dump.put("agent", traceMetricsScores.agent());
        dump.put("tool", traceMetricsScores.tool());
        dump.put("retriever", traceMetricsScores.retriever());
        dump.put("llm", traceMetricsScores.llm());
        dump.put("base", traceMetricsScores.base());
        return dump;
    }

    private static void put(Map<String, Object> dump, String key, Object value, boolean excludeNulls) {
        if (excludeNulls && value == null) {
            return;
        }
        dump.put(key, value);
    }

    private static String key(String snakeCase, String camelCase, boolean byAlias) {
        return byAlias ? camelCase : snakeCase;
    }

    private TestRun copyWith(
            List<LlmApiTestCase> testCases,
            List<ConversationalApiTestCase> conversationalTestCases,
            Double evaluationCost) {
        return new TestRun(
                testFile,
                testCases,
                conversationalTestCases,
                metricsScores,
                traceMetricsScores,
                identifier,
                hyperparameters,
                prompts,
                testPassed,
                testFailed,
                runDuration,
                evaluationCost,
                datasetAlias,
                datasetId,
                official);
    }

    private TestRun copyWithMetricsScores(List<MetricScores> metricsScores, TraceMetricScores traceMetricScores) {
        return new TestRun(
                testFile,
                testCases,
                conversationalTestCases,
                metricsScores,
                traceMetricScores,
                identifier,
                hyperparameters,
                prompts,
                testPassed,
                testFailed,
                runDuration,
                evaluationCost,
                datasetAlias,
                datasetId,
                official);
    }

    private TestRun copyWithSortedTestCases(
            List<LlmApiTestCase> testCases, List<ConversationalApiTestCase> conversationalTestCases) {
        return new TestRun(
                testFile,
                testCases,
                conversationalTestCases,
                metricsScores,
                traceMetricsScores,
                identifier,
                hyperparameters,
                prompts,
                testPassed,
                testFailed,
                runDuration,
                evaluationCost,
                datasetAlias,
                datasetId,
                official);
    }

    private TestRun copyWithDatasetProperties(String datasetAlias, String datasetId) {
        return new TestRun(
                testFile,
                testCases,
                conversationalTestCases,
                metricsScores,
                traceMetricsScores,
                identifier,
                hyperparameters,
                prompts,
                testPassed,
                testFailed,
                runDuration,
                evaluationCost,
                datasetAlias,
                datasetId,
                official);
    }

    private TestRun copyWithPassFailCounts(Integer testPassed, Integer testFailed) {
        return new TestRun(
                testFile,
                testCases,
                conversationalTestCases,
                metricsScores,
                traceMetricsScores,
                identifier,
                hyperparameters,
                prompts,
                testPassed,
                testFailed,
                runDuration,
                evaluationCost,
                datasetAlias,
                datasetId,
                official);
    }

    private static int aggregateMetricData(List<Object> metricsData, Map<String, MetricScoresAggregator> aggregators) {
        var validScores = 0;
        if (metricsData == null) {
            return validScores;
        }
        for (var data : metricsData) {
            if (data instanceof MetricData metricData) {
                var aggregator = aggregators.computeIfAbsent(metricData.name(), MetricScoresAggregator::new);
                if (metricData.score() == null) {
                    aggregator.errors++;
                } else {
                    validScores++;
                    aggregator.scores.add(metricData.score());
                    if (metricData.success()) {
                        aggregator.passes++;
                    } else {
                        aggregator.fails++;
                    }
                }
            }
        }
        return validScores;
    }

    private static int aggregateTraceMetricData(
            Map<String, Object> trace,
            Map<String, MetricScoresAggregator> aggregators,
            TraceMetricScoresAggregator traceAggregators) {
        if (trace == null) {
            return 0;
        }
        var validScores = 0;
        validScores += aggregateTraceSpans(trace, "agentSpans", "agent_spans", traceAggregators.agent, aggregators);
        validScores += aggregateTraceSpans(trace, "toolSpans", "tool_spans", traceAggregators.tool, aggregators);
        validScores += aggregateTraceSpans(
                trace, "retrieverSpans", "retriever_spans", traceAggregators.retriever, aggregators);
        validScores += aggregateTraceSpans(trace, "llmSpans", "llm_spans", traceAggregators.llm, aggregators);
        validScores += aggregateTraceSpans(trace, "baseSpans", "base_spans", traceAggregators.base, aggregators);
        return validScores;
    }

    private static int aggregateTraceSpans(
            Map<String, Object> trace,
            String camelCaseKey,
            String snakeCaseKey,
            Map<String, Map<String, MetricScoresAggregator>> spanAggregators,
            Map<String, MetricScoresAggregator> overallAggregators) {
        var spans = trace.get(camelCaseKey);
        if (spans == null) {
            spans = trace.get(snakeCaseKey);
        }
        if (!(spans instanceof Iterable<?> iterable)) {
            return 0;
        }

        var validScores = 0;
        for (var span : iterable) {
            if (!(span instanceof Map<?, ?> spanMap)) {
                continue;
            }
            var spanName = String.valueOf(spanMap.get("name"));
            var metricsData = value(spanMap, "metricsData", "metrics_data");
            if (!(metricsData instanceof Iterable<?> metricIterable)) {
                continue;
            }
            var spanMetricAggregators = spanAggregators.computeIfAbsent(spanName, ignored -> new LinkedHashMap<>());
            for (var metric : metricIterable) {
                if (metric instanceof MetricData metricData) {
                    aggregateMetricData(metricData, overallAggregators.computeIfAbsent(
                            metricData.name(), MetricScoresAggregator::new));
                    aggregateMetricData(metricData, spanMetricAggregators.computeIfAbsent(
                            metricData.name(), MetricScoresAggregator::new));
                    if (metricData.score() != null) {
                        validScores++;
                    }
                }
            }
        }
        return validScores;
    }

    private static Object value(Map<?, ?> values, String camelCaseKey, String snakeCaseKey) {
        var value = values.get(camelCaseKey);
        return value == null ? values.get(snakeCaseKey) : value;
    }

    private static void aggregateMetricData(MetricData metricData, MetricScoresAggregator aggregator) {
        if (metricData.score() == null) {
            aggregator.errors++;
            return;
        }
        aggregator.scores.add(metricData.score());
        if (metricData.success()) {
            aggregator.passes++;
        } else {
            aggregator.fails++;
        }
    }

    private static <T> List<T> append(List<T> values, T value) {
        var updated = new java.util.ArrayList<T>(values == null ? List.of() : values);
        updated.add(value);
        return updated;
    }

    private static <T> List<T> sortByOrder(List<T> testCases) {
        return testCases.stream()
                .sorted(Comparator.comparingInt(testCase -> orderOf(testCase).orElse(Integer.MAX_VALUE)))
                .toList();
    }

    private static OptionalInt orderOf(Object testCase) {
        if (testCase instanceof LlmApiTestCase apiTestCase && apiTestCase.order() != null) {
            return OptionalInt.of(apiTestCase.order());
        }
        if (testCase instanceof ConversationalApiTestCase apiTestCase && apiTestCase.order() != null) {
            return OptionalInt.of(apiTestCase.order());
        }
        return OptionalInt.empty();
    }

    private static <T> List<T> assignUniqueOrders(
            List<T> sortedTestCases, Function<T, Integer> orderGetter, BiFunction<T, Integer, T> orderSetter) {
        var assigned = new ArrayList<T>();
        var highestOrder = 0;
        for (var testCase : sortedTestCases) {
            var order = orderGetter.apply(testCase);
            if (order == null) {
                order = highestOrder;
                testCase = orderSetter.apply(testCase, order);
            }
            highestOrder = order + 1;
            assigned.add(testCase);
        }

        var seen = new HashSet<Integer>();
        var hasDuplicates = assigned.stream().map(orderGetter).anyMatch(order -> !seen.add(order));
        if (!hasDuplicates) {
            return assigned;
        }

        var renumbered = new ArrayList<T>();
        for (var i = 0; i < assigned.size(); i++) {
            renumbered.add(orderSetter.apply(assigned.get(i), i));
        }
        return renumbered;
    }

    public record MetricsScoresAggregation(TestRun testRun, int validScores) {}

    private static final class MetricScoresAggregator {
        private final String name;
        private final List<Double> scores = new ArrayList<>();
        private int passes;
        private int fails;
        private int errors;

        private MetricScoresAggregator(String name) {
            this.name = name;
        }

        private MetricScores toMetricScores() {
            return new MetricScores(name, scores, passes, fails, errors);
        }
    }

    private record TraceMetricScoresAggregator(
            Map<String, Map<String, MetricScoresAggregator>> agent,
            Map<String, Map<String, MetricScoresAggregator>> tool,
            Map<String, Map<String, MetricScoresAggregator>> retriever,
            Map<String, Map<String, MetricScoresAggregator>> llm,
            Map<String, Map<String, MetricScoresAggregator>> base) {

        private static TraceMetricScoresAggregator empty() {
            return new TraceMetricScoresAggregator(
                    new LinkedHashMap<>(),
                    new LinkedHashMap<>(),
                    new LinkedHashMap<>(),
                    new LinkedHashMap<>(),
                    new LinkedHashMap<>());
        }

        private TraceMetricScores toTraceMetricScores() {
            if (agent.isEmpty() && tool.isEmpty() && retriever.isEmpty() && llm.isEmpty() && base.isEmpty()) {
                return null;
            }
            return new TraceMetricScores(
                    metricScores(agent),
                    metricScores(tool),
                    metricScores(retriever),
                    metricScores(llm),
                    metricScores(base));
        }

        private static Map<String, Map<String, MetricScores>> metricScores(
                Map<String, Map<String, MetricScoresAggregator>> aggregators) {
            var scores = new LinkedHashMap<String, Map<String, MetricScores>>();
            aggregators.forEach((spanName, spanAggregators) -> {
                var spanScores = new LinkedHashMap<String, MetricScores>();
                spanAggregators.forEach((metricName, aggregator) -> spanScores.put(metricName, aggregator.toMetricScores()));
                scores.put(spanName, spanScores);
            });
            return scores;
        }
    }
}
