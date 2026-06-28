package dev.jeval.runner;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jeval.EvaluationDataset;
import dev.jeval.Evaluator;
import dev.jeval.LlmTestCase;
import dev.jeval.Metric;
import dev.jeval.MetricResult;
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

public final class TestRunner {
    private static final ObjectMapper JSON = new ObjectMapper();

    public TestRunResult run(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            var results = new ArrayList<TestCaseResult>();
            try (var files = Files.walk(path)) {
                for (var file : files
                        .filter(Files::isRegularFile)
                        .filter(TestRunner::isJson)
                        .sorted()
                        .toList()) {
                    results.addAll(runFile(file).results());
                }
            }
            return summarize(path.getFileName().toString(), results);
        }
        return runFile(path);
    }

    private TestRunResult runFile(Path path) throws IOException {
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
        var results = testCases.stream()
                .map(testCase -> runCase(testCase, metrics))
                .toList();
        return summarize(spec.name() == null ? stripExtension(path.getFileName().toString()) : spec.name(), results);
    }

    private static LlmTestCase testCase(CaseSpec spec) {
        return LlmTestCase.builder(spec.input())
                .actualOutput(spec.actualOutput())
                .expectedOutput(spec.expectedOutput())
                .name(spec.name())
                .build();
    }

    private static TestCaseResult runCase(LlmTestCase testCase, List<Metric> metrics) {
        var result = Evaluator.evaluate(testCase, metrics);
        return new TestCaseResult(testCase.name(), result.success(), result.metricResults());
    }

    private static List<LlmTestCase> dataset(Path parent, String dataset) {
        var file = parent == null ? Path.of(dataset) : parent.resolve(dataset);
        var data = new EvaluationDataset();
        var name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".jsonl")) {
            return jsonlTestCases(file);
        } else if (name.endsWith(".csv")) {
            data.addTestCasesFromCsvFile(file, "input", "actual_output", "expected_output", "context", "retrieval_context");
        } else if (name.endsWith(".json")) {
            data.addTestCasesFromJsonFile(file, "input", "actual_output", "expected_output", "context", "retrieval_context");
        } else {
            throw new IllegalArgumentException("Unsupported dataset file type: " + file);
        }
        return data.testCases();
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

    private static LlmTestCase jsonTestCase(JsonNode node) {
        return LlmTestCase.builder(text(node, "input"))
                .actualOutput(text(node, "actual_output"))
                .expectedOutput(text(node, "expected_output"))
                .build();
    }

    private static String text(JsonNode node, String key) {
        return node.has(key) && !node.get(key).isNull() ? node.get(key).asText() : null;
    }

    private static Metric metric(MetricSpec spec) {
        var type = spec.type().toLowerCase(Locale.ROOT).replace("-", "_");
        var threshold = spec.threshold() == null ? 1.0 : spec.threshold();
        return switch (type) {
            case "exact_match" -> new ExactMatchMetric(threshold);
            case "pattern_match" -> new PatternMatchMetric(spec.pattern(), Boolean.TRUE.equals(spec.ignoreCase()), threshold);
            default -> throw new IllegalArgumentException("Unsupported metric type: " + spec.type());
        };
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
            @JsonAlias("expected_output") String expectedOutput) {
    }
}
