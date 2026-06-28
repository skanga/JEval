package dev.jeval.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.jeval.runner.TestRunResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LocalRunStore {
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final Path root;

    public LocalRunStore(Path root) {
        this.root = root;
    }

    public Path write(TestRunResult result) throws IOException {
        var directory = root.resolve(".jeval");
        Files.createDirectories(directory);
        var file = directory.resolve(".jeval");
        JSON.writeValue(file.toFile(), result);
        return file;
    }
}
