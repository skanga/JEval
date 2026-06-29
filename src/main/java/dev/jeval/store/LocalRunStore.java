package dev.jeval.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.jeval.runner.TestRunResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class LocalRunStore {
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private final Path root;

    public LocalRunStore(Path root) {
        this.root = root;
    }

    public Path write(TestRunResult result) throws IOException {
        var compatibilityDirectory = root.resolve(".jeval");
        Files.createDirectories(compatibilityDirectory);
        var compatibilityFile = compatibilityDirectory.resolve(".jeval");
        JSON.writeValue(compatibilityFile.toFile(), result);

        var deepevalDirectory = root.resolve(".deepeval");
        Files.createDirectories(deepevalDirectory);
        JSON.writeValue(deepevalDirectory.resolve(".latest_run_full.json").toFile(), result);
        JSON.writeValue(timestampedRunPath(deepevalDirectory).toFile(), result);
        return compatibilityFile;
    }

    private static Path timestampedRunPath(Path directory) {
        var base = "test_run_" + LocalDateTime.now().format(TIMESTAMP);
        var candidate = directory.resolve(base + ".json");
        for (var index = 1; Files.exists(candidate); index++) {
            candidate = directory.resolve(base + "_" + index + ".json");
        }
        return candidate;
    }
}
