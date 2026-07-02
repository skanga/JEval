package dev.jeval.runner;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jeval.EvaluationDataset;
import dev.jeval.Evaluator;
import dev.jeval.LlmTestCase;
import dev.jeval.Metric;
import dev.jeval.MetricResult;
import dev.jeval.MissingTestCaseParamsException;
import dev.jeval.ToolCall;
import dev.jeval.metrics.ExactMatchMetric;
import dev.jeval.metrics.PatternMatchMetric;
import dev.jeval.runner.TestRunResult.MetricAggregate;
import dev.jeval.runner.TestRunResult.TestCaseResult;
import dev.jeval.runner.TestRunResult.TestRunSummary;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TestRunner {
    private static final ObjectMapper JSON = new ObjectMapper();

    public TestRunResult run(Path path) throws IOException {
        return run(path, null);
    }

    public TestRunResult run(Path path, String selector) throws IOException {
        return run(path, selector, 1);
    }

    public TestRunResult run(Path path, String selector, int repeat) throws IOException {
        return run(path, selector, repeat, false);
    }

    public TestRunResult run(Path path, String selector, int repeat, boolean exitOnFirstFailure) throws IOException {
        return run(path, selector, repeat, exitOnFirstFailure, false);
    }

    public TestRunResult run(
            Path path,
            String selector,
            int repeat,
            boolean exitOnFirstFailure,
            boolean ignoreErrors) throws IOException {
        return run(path, selector, repeat, exitOnFirstFailure, ignoreErrors, null);
    }

    public TestRunResult run(
            Path path,
            String selector,
            int repeat,
            boolean exitOnFirstFailure,
            boolean ignoreErrors,
            String mark) throws IOException {
        return run(path, selector, repeat, exitOnFirstFailure, ignoreErrors, false, mark);
    }

    public TestRunResult run(
            Path path,
            String selector,
            int repeat,
            boolean exitOnFirstFailure,
            boolean ignoreErrors,
            boolean skipOnMissingParams,
            String mark) throws IOException {
        return run(path, selector, repeat, exitOnFirstFailure, ignoreErrors, skipOnMissingParams, mark, null, false);
    }

    public TestRunResult run(
            Path path,
            String selector,
            int repeat,
            boolean exitOnFirstFailure,
            boolean ignoreErrors,
            boolean skipOnMissingParams,
            String mark,
            Path cacheFile,
            boolean useCache) throws IOException {
        try {
            if (repeat < 1) {
                throw new IllegalArgumentException("The repeat argument must be at least 1.");
            }
            var cache = TestRunCache.open(cacheFile);
            if (Files.isDirectory(path)) {
                if (selector != null) {
                    throw new IllegalArgumentException("Test selectors are only supported for files: " + path);
                }
                var results = new ArrayList<TestCaseResult>();
                try (var files = Files.walk(path)) {
                    for (var file : files
                            .filter(Files::isRegularFile)
                            .filter(TestRunner::isJson)
                            .sorted()
                            .toList()) {
                        results.addAll(runFile(
                                file,
                                null,
                                repeat,
                                exitOnFirstFailure,
                                ignoreErrors,
                                skipOnMissingParams,
                                mark,
                                cache,
                                useCache).results());
                        if (exitOnFirstFailure && results.stream().anyMatch(result -> !result.success())) {
                            break;
                        }
                    }
                }
                return summarize(path.getFileName().toString(), results);
            }
            return runFile(path, selector, repeat, exitOnFirstFailure, ignoreErrors, skipOnMissingParams, mark, cache, useCache);
        } finally {
            TestRunHooks.invokeTestRunEndHook();
        }
    }

    private TestRunResult runFile(Path path) throws IOException {
        return runFile(path, null);
    }

    private TestRunResult runFile(Path path, String selector) throws IOException {
        return runFile(path, selector, 1);
    }

    private TestRunResult runFile(Path path, String selector, int repeat) throws IOException {
        return runFile(path, selector, repeat, false);
    }

    private TestRunResult runFile(Path path, String selector, int repeat, boolean exitOnFirstFailure)
            throws IOException {
        return runFile(path, selector, repeat, exitOnFirstFailure, false);
    }

    private TestRunResult runFile(
            Path path,
            String selector,
            int repeat,
            boolean exitOnFirstFailure,
            boolean ignoreErrors) throws IOException {
        return runFile(path, selector, repeat, exitOnFirstFailure, ignoreErrors, null);
    }

    private TestRunResult runFile(
            Path path,
            String selector,
            int repeat,
            boolean exitOnFirstFailure,
            boolean ignoreErrors,
            String mark) throws IOException {
        return runFile(path, selector, repeat, exitOnFirstFailure, ignoreErrors, false, mark);
    }

    private TestRunResult runFile(
            Path path,
            String selector,
            int repeat,
            boolean exitOnFirstFailure,
            boolean ignoreErrors,
            boolean skipOnMissingParams,
            String mark) throws IOException {
        return runFile(path, selector, repeat, exitOnFirstFailure, ignoreErrors, skipOnMissingParams, mark, null, false);
    }

    private TestRunResult runFile(
            Path path,
            String selector,
            int repeat,
            boolean exitOnFirstFailure,
            boolean ignoreErrors,
            boolean skipOnMissingParams,
            String mark,
            TestRunCache cache,
            boolean useCache) throws IOException {
        var spec = JSON.readValue(path.toFile(), EvaluationSpec.class);
        if (spec.metrics() == null || spec.metrics().isEmpty()) {
            throw new IllegalArgumentException("Evaluation spec must define at least one metric: " + path);
        }
        if (spec.dataset() == null && spec.cases() == null) {
            throw new IllegalArgumentException("Evaluation spec must define cases or dataset: " + path);
        }
        var metrics = spec.metrics().stream().map(TestRunner::metric).toList();
        var testCases = spec.dataset() == null
                ? spec.cases().stream().map(TestRunner::testCase).toList()
                : dataset(path.getParent(), spec.dataset());
        if (selector != null) {
            testCases = selectedCases(testCases, selector);
        }
        if (mark != null) {
            testCases = markedCases(testCases, mark);
        }
        var results = repeatedResults(
                testCases,
                metrics,
                metricCacheKeys(spec.metrics()),
                repeat,
                exitOnFirstFailure,
                ignoreErrors,
                skipOnMissingParams,
                cache,
                useCache);
        return summarize(spec.name() == null ? stripExtension(path.getFileName().toString()) : spec.name(), results);
    }

    private static List<TestCaseResult> repeatedResults(
            List<LlmTestCase> testCases,
            List<Metric> metrics,
            List<String> metricCacheKeys,
            int repeat,
            boolean exitOnFirstFailure,
            boolean ignoreErrors,
            boolean skipOnMissingParams,
            TestRunCache cache,
            boolean useCache) {
        var results = new ArrayList<TestCaseResult>();
        for (var i = 0; i < repeat; i++) {
            for (var testCase : testCases) {
                if (useCache && cache != null) {
                    var cached = cache.get(testCase, metricCacheKeys);
                    if (cached.isPresent()) {
                        var result = cached.get();
                        results.add(result);
                        if (exitOnFirstFailure && !result.success()) {
                            return List.copyOf(results);
                        }
                        continue;
                    }
                }
                var result = runCase(testCase, metrics, ignoreErrors, skipOnMissingParams);
                if (result == null) {
                    continue;
                }
                results.add(result);
                if (cache != null) {
                    cache.put(testCase, metricCacheKeys, result);
                }
                if (exitOnFirstFailure && !result.success()) {
                    return List.copyOf(results);
                }
            }
        }
        return List.copyOf(results);
    }

    private static TestCaseResult runCase(
            LlmTestCase testCase,
            List<Metric> metrics,
            boolean ignoreErrors,
            boolean skipOnMissingParams) {
        try {
            return runCase(testCase, metrics);
        } catch (MissingTestCaseParamsException error) {
            if (skipOnMissingParams) {
                return null;
            }
            if (!ignoreErrors) {
                throw error;
            }
            return new TestCaseResult(
                    testCase.name(),
                    false,
                    List.of(new MetricResult("Error", 0.0, 1.0, false, "Evaluation error: " + error.getMessage())),
                    testCase.input(),
                    testCase.actualOutput(),
                    testCase.expectedOutput(),
                    testCase.context(),
                    testCase.retrievalContext(),
                    testCase.tags(),
                    testCase.additionalMetadata(),
                    testCase.comments(),
                    testCase.tokenCost(),
                    testCase.completionTime(),
                    testCase.customColumnKeyValues(),
                    testCase.toolsCalled(),
                    testCase.expectedTools(),
                    testCase.mcpServers(),
                    testCase.mcpToolsCalled(),
                    testCase.mcpResourcesCalled(),
                    testCase.mcpPromptsCalled(),
                    testCase.trace());
        } catch (RuntimeException error) {
            if (!ignoreErrors) {
                throw error;
            }
            return new TestCaseResult(
                    testCase.name(),
                    false,
                    List.of(new MetricResult("Error", 0.0, 1.0, false, "Evaluation error: " + error.getMessage())),
                    testCase.input(),
                    testCase.actualOutput(),
                    testCase.expectedOutput(),
                    testCase.context(),
                    testCase.retrievalContext(),
                    testCase.tags(),
                    testCase.additionalMetadata(),
                    testCase.comments(),
                    testCase.tokenCost(),
                    testCase.completionTime(),
                    testCase.customColumnKeyValues(),
                    testCase.toolsCalled(),
                    testCase.expectedTools(),
                    testCase.mcpServers(),
                    testCase.mcpToolsCalled(),
                    testCase.mcpResourcesCalled(),
                    testCase.mcpPromptsCalled(),
                    testCase.trace());
        }
    }

    private static List<LlmTestCase> selectedCases(List<LlmTestCase> testCases, String selector) {
        var selected = testCases.stream()
                .filter(testCase -> selector.equals(testCase.name()))
                .toList();
        if (selected.isEmpty()) {
            throw new IllegalArgumentException("No test case matched selector: " + selector);
        }
        return selected;
    }

    private static List<LlmTestCase> markedCases(List<LlmTestCase> testCases, String mark) {
        var selected = testCases.stream()
                .filter(testCase -> testCase.tags() != null && testCase.tags().contains(mark))
                .toList();
        if (selected.isEmpty()) {
            throw new IllegalArgumentException("No test case matched mark: " + mark);
        }
        return selected;
    }

    private static LlmTestCase testCase(CaseSpec spec) {
        return LlmTestCase.builder(spec.input())
                .actualOutput(spec.actualOutput())
                .expectedOutput(spec.expectedOutput())
                .context(spec.context())
                .retrievalContext(spec.retrievalContext())
                .metadata(spec.metadata())
                .comments(spec.comments())
                .tokenCost(spec.tokenCost())
                .completionTime(spec.completionTime())
                .customColumnKeyValues(spec.customColumnKeyValues())
                .toolsCalled(spec.toolsCalled())
                .expectedTools(spec.expectedTools())
                .mcpServers(spec.mcpServers())
                .mcpToolsCalled(spec.mcpToolsCalled())
                .mcpResourcesCalled(spec.mcpResourcesCalled())
                .mcpPromptsCalled(spec.mcpPromptsCalled())
                .trace(spec.trace())
                .name(spec.name())
                .tags(spec.tags())
                .build();
    }

    private static TestCaseResult runCase(LlmTestCase testCase, List<Metric> metrics) {
        var result = Evaluator.evaluate(testCase, metrics);
        return new TestCaseResult(
                testCase.name(),
                result.success(),
                result.metricResults(),
                testCase.input(),
                testCase.actualOutput(),
                testCase.expectedOutput(),
                testCase.context(),
                testCase.retrievalContext(),
                testCase.tags(),
                testCase.additionalMetadata(),
                testCase.comments(),
                testCase.tokenCost(),
                testCase.completionTime(),
                testCase.customColumnKeyValues(),
                testCase.toolsCalled(),
                testCase.expectedTools(),
                testCase.mcpServers(),
                testCase.mcpToolsCalled(),
                testCase.mcpResourcesCalled(),
                testCase.mcpPromptsCalled(),
                testCase.trace());
    }

    private static List<LlmTestCase> dataset(Path parent, String dataset) {
        var file = parent == null ? Path.of(dataset) : parent.resolve(dataset);
        var data = new EvaluationDataset();
        var name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".jsonl")) {
            return jsonlTestCases(file);
        } else if (name.endsWith(".csv")) {
            data.addTestCasesFromCsvFile(file, "input", "actual_output", "expected_output",
                    "context", "retrieval_context", "tools_called", "expected_tools", "metadata");
        } else if (name.endsWith(".json")) {
            return jsonTestCases(file);
        } else {
            throw new IllegalArgumentException("Unsupported dataset file type: " + file);
        }
        return data.testCases();
    }

    private static List<LlmTestCase> jsonTestCases(Path file) {
        var rows = readJsonArray(file);
        var cases = new ArrayList<LlmTestCase>();
        rows.forEach(row -> cases.add(jsonTestCase(row)));
        return List.copyOf(cases);
    }

    private static List<LlmTestCase> jsonlTestCases(Path file) {
        try {
            var cases = new ArrayList<LlmTestCase>();
            for (var line : Files.readAllLines(file)) {
                if (!line.isBlank()) {
                    cases.add(jsonTestCase(JSON.readTree(line)));
                }
            }
            return List.copyOf(cases);
        } catch (IOException error) {
            throw new IllegalArgumentException("The file " + file + " could not be read.", error);
        }
    }

    private static JsonNode readJsonArray(Path file) {
        try {
            return readJsonArray(file, Files.readString(file));
        } catch (JsonProcessingException error) {
            try {
                return readJsonArray(file, Files.readString(file).replaceAll(",\\s*([\\]}])", "$1"));
            } catch (JsonProcessingException retryError) {
                throw new IllegalArgumentException("The file " + file + " is not a valid JSON file.", retryError);
            } catch (IOException retryError) {
                throw new IllegalArgumentException("The file " + file + " could not be read.", retryError);
            }
        } catch (IOException error) {
            throw new IllegalArgumentException("The file " + file + " could not be read.", error);
        }
    }

    private static JsonNode readJsonArray(Path file, String content) throws JsonProcessingException {
        var node = JSON.readTree(content);
        if (!node.isArray()) {
            throw new IllegalArgumentException("The file " + file + " must contain a JSON array.");
        }
        return node;
    }

    private static LlmTestCase jsonTestCase(JsonNode node) {
        return LlmTestCase.builder(requiredText(node, "input"))
                .actualOutput(requiredText(node, "actual_output", "actualOutput"))
                .expectedOutput(text(node, "expected_output", "expectedOutput"))
                .context(textList(node, "context"))
                .retrievalContext(textList(node, "retrieval_context", "retrievalContext"))
                .metadata(objectMap(node, node.has("metadata") ? "metadata" : "additional_metadata"))
                .comments(text(node, "comments"))
                .tokenCost(doubleOrNull(node, "token_cost", "tokenCost"))
                .completionTime(doubleOrNull(node, "completion_time", "completionTime"))
                .customColumnKeyValues(stringMap(node, "custom_column_key_values", "customColumnKeyValues"))
                .toolsCalled(toolCalls(node, "tools_called", "toolsCalled"))
                .expectedTools(toolCalls(node, "expected_tools", "expectedTools"))
                .mcpServers(objectList(node, "mcp_servers", "mcpServers"))
                .mcpToolsCalled(objectList(node, "mcp_tools_called", "mcpToolsCalled"))
                .mcpResourcesCalled(objectList(node, "mcp_resources_called", "mcpResourcesCalled"))
                .mcpPromptsCalled(objectList(node, "mcp_prompts_called", "mcpPromptsCalled"))
                .trace(objectMap(node, "trace"))
                .name(text(node, "name"))
                .tags(textList(node, "tags"))
                .build();
    }

    private static String text(JsonNode node, String key) {
        return node.has(key) && !node.get(key).isNull() ? node.get(key).asText() : null;
    }

    private static String requiredText(JsonNode node, String key) {
        if (!node.has(key) || node.get(key).isNull() || !node.get(key).isTextual()) {
            throw new IllegalArgumentException("Required fields are missing in one or more JSON objects");
        }
        return node.get(key).asText();
    }

    private static String requiredText(JsonNode node, String key, String alias) {
        return requiredText(node, node.has(key) ? key : alias);
    }

    private static String text(JsonNode node, String key, String alias) {
        return text(node, node.has(key) ? key : alias);
    }

    private static Double doubleOrNull(JsonNode node, String key) {
        if (!node.has(key) || node.get(key).isNull()) {
            return null;
        }
        var value = node.get(key);
        if (value.isNumber()) {
            return finiteDouble(value.asDouble(), key, value.asText());
        }
        if (value.isTextual()) {
            try {
                return finiteDouble(Double.parseDouble(value.asText()), key, value.asText());
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException("Invalid value for " + key + ": " + value.asText(), error);
            }
        }
        throw new IllegalArgumentException("Invalid value for " + key + ": " + value);
    }

    private static Double doubleOrNull(JsonNode node, String key, String alias) {
        return doubleOrNull(node, node.has(key) ? key : alias);
    }

    private static double finiteDouble(double value, String key, String raw) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Invalid value for " + key + ": " + raw);
        }
        return value;
    }

    private static List<String> textList(JsonNode node, String key) {
        return convertOrNull(node, key, new TypeReference<>() {});
    }

    private static List<String> textList(JsonNode node, String key, String alias) {
        return textList(node, node.has(key) ? key : alias);
    }

    private static List<ToolCall> toolCalls(JsonNode node, String key) {
        return convertOrNull(node, key, new TypeReference<>() {});
    }

    private static List<ToolCall> toolCalls(JsonNode node, String key, String alias) {
        return toolCalls(node, node.has(key) ? key : alias);
    }

    private static List<Map<String, Object>> objectList(JsonNode node, String key) {
        return convertOrNull(node, key, new TypeReference<>() {});
    }

    private static List<Map<String, Object>> objectList(JsonNode node, String key, String alias) {
        return objectList(node, node.has(key) ? key : alias);
    }

    private static Map<String, Object> objectMap(JsonNode node, String key) {
        return convertOrNull(node, key, new TypeReference<>() {});
    }

    private static Map<String, String> stringMap(JsonNode node, String key) {
        return convertOrNull(node, key, new TypeReference<>() {});
    }

    private static Map<String, String> stringMap(JsonNode node, String key, String alias) {
        return stringMap(node, node.has(key) ? key : alias);
    }

    private static <T> T convertOrNull(JsonNode node, String key, TypeReference<T> type) {
        return node.has(key) && !node.get(key).isNull() ? JSON.convertValue(node.get(key), type) : null;
    }

    private static Metric metric(MetricSpec spec) {
        if (spec.type() == null || spec.type().isBlank()) {
            throw new IllegalArgumentException("Metric spec must define type");
        }
        var type = spec.type().toLowerCase(Locale.ROOT).replace("-", "_");
        var threshold = spec.threshold() == null ? 1.0 : spec.threshold();
        if (!Double.isFinite(threshold)) {
            throw new IllegalArgumentException("Invalid value for threshold: " + spec.threshold());
        }
        return switch (type) {
            case "exact_match" -> new ExactMatchMetric(threshold);
            case "pattern_match" -> {
                if (spec.pattern() == null || spec.pattern().isBlank()) {
                    throw new IllegalArgumentException("Pattern match metric requires pattern");
                }
                yield new PatternMatchMetric(spec.pattern(), Boolean.TRUE.equals(spec.ignoreCase()), threshold);
            }
            default -> throw new IllegalArgumentException("Unsupported metric type: " + spec.type());
        };
    }

    private static List<String> metricCacheKeys(List<MetricSpec> specs) {
        return specs.stream()
                .map(spec -> String.join("|",
                        spec.type() == null ? "" : spec.type().toLowerCase(Locale.ROOT).replace("-", "_"),
                        spec.pattern() == null ? "" : spec.pattern(),
                        String.valueOf(Boolean.TRUE.equals(spec.ignoreCase())),
                        String.valueOf(spec.threshold() == null ? 1.0 : spec.threshold())))
                .toList();
    }

    private static TestRunResult summarize(String name, List<TestCaseResult> results) {
        var total = results.size();
        var passed = (int) results.stream().filter(TestCaseResult::success).count();
        var scoreCount = results.stream().flatMap(result -> result.metricResults().stream()).count();
        var scoreSum = results.stream()
                .flatMap(result -> result.metricResults().stream())
                .mapToDouble(MetricResult::score)
                .sum();
        return new TestRunResult(name, results,
                new TestRunSummary(total, passed, total - passed, scoreCount == 0 ? 0.0 : scoreSum / scoreCount,
                        total == 0 ? 0.0 : (double) passed / total),
                aggregates(results));
    }

    private static List<MetricAggregate> aggregates(List<TestCaseResult> results) {
        var byName = new LinkedHashMap<String, List<MetricResult>>();
        results.stream()
                .flatMap(result -> result.metricResults().stream())
                .forEach(result -> byName.computeIfAbsent(result.name(), ignored -> new ArrayList<>()).add(result));
        return byName.entrySet().stream()
                .map(entry -> aggregate(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static MetricAggregate aggregate(String name, List<MetricResult> results) {
        var total = results.size();
        var passed = results.stream().filter(MetricResult::success).count();
        var score = results.stream().mapToDouble(MetricResult::score).average().orElse(0.0);
        return new MetricAggregate(name, score, total == 0 ? 0.0 : (double) passed / total, total);
    }

    private static boolean isJson(Path path) {
        var name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".json");
    }

    private static String stripExtension(String fileName) {
        var dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }

    private record EvaluationSpec(String name, String dataset, List<MetricSpec> metrics, List<CaseSpec> cases) {
    }

    private record MetricSpec(String type, String pattern, Boolean ignoreCase, Double threshold) {
    }

    private record CaseSpec(
            String name,
            String input,
            @JsonAlias("actual_output") String actualOutput,
            @JsonAlias("expected_output") String expectedOutput,
            List<String> context,
            @JsonAlias("retrieval_context") List<String> retrievalContext,
            List<String> tags,
            @JsonAlias("additional_metadata")
            Map<String, Object> metadata,
            String comments,
            @JsonAlias("token_cost") Double tokenCost,
            @JsonAlias("completion_time") Double completionTime,
            @JsonAlias("custom_column_key_values") Map<String, String> customColumnKeyValues,
            @JsonAlias("tools_called") List<ToolCall> toolsCalled,
            @JsonAlias("expected_tools") List<ToolCall> expectedTools,
            @JsonAlias("mcp_servers") List<Map<String, Object>> mcpServers,
            @JsonAlias("mcp_tools_called") List<Map<String, Object>> mcpToolsCalled,
            @JsonAlias("mcp_resources_called") List<Map<String, Object>> mcpResourcesCalled,
            @JsonAlias("mcp_prompts_called") List<Map<String, Object>> mcpPromptsCalled,
            Map<String, Object> trace) {
    }
}
