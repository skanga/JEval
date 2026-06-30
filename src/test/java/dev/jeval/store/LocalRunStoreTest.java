package dev.jeval.store;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.runner.TestRunner;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalRunStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void writesLatestRunToJevalDirectory() throws Exception {
        var file = tempDir.resolve("eval.json");
        Files.writeString(file, """
                {
                  "name": "stored",
                  "metrics": [{"type": "exact_match"}],
                  "cases": [
                    {"name": "one", "input": "q", "actualOutput": "a", "expectedOutput": "a"}
                  ]
                }
                """);
        var result = new TestRunner().run(file);

        new LocalRunStore(tempDir).write(result);

        var storeFile = tempDir.resolve(".jeval").resolve(".jeval");
        assertTrue(Files.exists(storeFile));
        var content = Files.readString(storeFile);
        assertTrue(content.contains("\"name\" : \"stored\""));
        assertTrue(content.contains("\"passed\" : 1"));

        var rollingFile = tempDir.resolve(".deepeval").resolve(".latest_run_full.json");
        assertTrue(Files.exists(rollingFile));
        var rollingContent = Files.readString(rollingFile);
        assertTrue(rollingContent.contains("\"name\" : \"stored\""));
        assertTrue(rollingContent.contains("\"passed\" : 1"));

        try (var files = Files.list(tempDir.resolve(".deepeval"))) {
            var timestamped = files
                    .filter(path -> path.getFileName().toString().matches("test_run_\\d{8}_\\d{6}(?:_\\d+)?\\.json"))
                    .toList();
            assertTrue(timestamped.size() == 1);
            assertTrue(Files.readString(timestamped.getFirst()).contains("\"name\" : \"stored\""));
        }
    }

    @Test
    void timestampedRunPathUsesDeepEvalCollisionSuffixes() throws Exception {
        var directory = tempDir.resolve(".deepeval");
        Files.createDirectories(directory);
        var timestamp = LocalDateTime.of(2026, 6, 30, 12, 34, 56);
        Files.createFile(directory.resolve("test_run_20260630_123456.json"));

        var second = LocalRunStore.timestampedRunPath(directory, timestamp);
        Files.createFile(second);
        var third = LocalRunStore.timestampedRunPath(directory, timestamp);

        assertTrue(second.getFileName().toString().endsWith("_2.json"));
        assertTrue(third.getFileName().toString().endsWith("_3.json"));
    }

    @Test
    void resolveTargetDirectorySupportsFolderSubfolderAndEnvFallbackLikeDeepEval() {
        var configured = tempDir.resolve("configured");
        var fromEnv = tempDir.resolve("from-env");

        assertTrue(LocalRunStore.resolveTargetDirectory(
                configured.toString(), "test_runs", Map.of("DEEPEVAL_RESULTS_FOLDER", fromEnv.toString()))
                .equals(configured.resolve("test_runs")));
        assertTrue(LocalRunStore.resolveTargetDirectory(null, "", Map.of("DEEPEVAL_RESULTS_FOLDER", fromEnv.toString()))
                .equals(fromEnv));
        assertTrue(LocalRunStore.resolveTargetDirectory(null, "ignored", Map.of()) == null);
    }
}
