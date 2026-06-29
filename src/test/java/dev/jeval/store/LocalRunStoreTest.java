package dev.jeval.store;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.runner.TestRunner;
import java.nio.file.Files;
import java.nio.file.Path;
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
}
