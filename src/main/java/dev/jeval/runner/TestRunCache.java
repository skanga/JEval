package dev.jeval.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.jeval.LlmTestCase;
import dev.jeval.runner.TestRunResult.TestCaseResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

final class TestRunCache {
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final Path path;
    private final List<CacheEntry> entries;

    private TestRunCache(Path path, List<CacheEntry> entries) {
        this.path = path;
        this.entries = new ArrayList<>(entries);
    }

    static TestRunCache open(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return new TestRunCache(path, List.of());
        }
        try {
            var file = JSON.readValue(path.toFile(), CacheFile.class);
            return new TestRunCache(path, file.entries() == null ? List.of() : file.entries());
        } catch (IOException error) {
            return new TestRunCache(path, List.of());
        }
    }

    Optional<TestCaseResult> get(LlmTestCase testCase, List<String> metrics) {
        var key = CacheKey.of(testCase, metrics);
        return entries.stream()
                .filter(entry -> key.equals(entry.key()))
                .map(CacheEntry::result)
                .filter(Objects::nonNull)
                .findFirst();
    }

    void put(LlmTestCase testCase, List<String> metrics, TestCaseResult result) {
        if (path == null || result == null) {
            return;
        }
        var key = CacheKey.of(testCase, metrics);
        entries.removeIf(entry -> key.equals(entry.key()));
        entries.add(new CacheEntry(key, result));
        save();
    }

    private void save() {
        try {
            Files.createDirectories(path.getParent());
            JSON.writeValue(path.toFile(), new CacheFile(List.copyOf(entries)));
        } catch (IOException ignored) {
            // Caching should never change evaluation outcomes.
        }
    }

    private record CacheFile(List<CacheEntry> entries) {
    }

    private record CacheEntry(CacheKey key, TestCaseResult result) {
    }

    private record CacheKey(
            String name,
            String input,
            String actualOutput,
            String expectedOutput,
            List<String> metrics) {
        private CacheKey {
            metrics = metrics == null ? List.of() : List.copyOf(metrics);
        }

        static CacheKey of(LlmTestCase testCase, List<String> metrics) {
            return new CacheKey(
                    testCase.name(),
                    testCase.input(),
                    testCase.actualOutput(),
                    testCase.expectedOutput(),
                    metrics);
        }
    }
}
