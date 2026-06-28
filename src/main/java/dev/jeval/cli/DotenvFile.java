package dev.jeval.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class DotenvFile {
    private final Path path;

    DotenvFile(Path path) {
        this.path = path;
    }

    LinkedHashMap<String, String> read() throws IOException {
        var values = new LinkedHashMap<String, String>();
        if (!Files.exists(path)) {
            return values;
        }
        for (var line : Files.readAllLines(path)) {
            var index = line.indexOf('=');
            if (index > 0) {
                values.put(line.substring(0, index), line.substring(index + 1));
            }
        }
        return values;
    }

    void update(Map<String, String> updates, Iterable<String> removals) throws IOException {
        var values = read();
        for (var key : removals) {
            values.remove(key);
        }
        updates.forEach((key, value) -> {
            if (value != null) {
                values.put(key, value);
            }
        });
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        var lines = values.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .toList();
        Files.write(path, lines);
    }
}
