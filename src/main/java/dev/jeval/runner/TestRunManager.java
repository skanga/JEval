package dev.jeval.runner;

import dev.jeval.ConversationalApiTestCase;
import dev.jeval.ConversationalTestCase;
import dev.jeval.LlmApiTestCase;
import dev.jeval.LlmTestCase;
import dev.jeval.Utils;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

public final class TestRunManager {
    private TestRun testRun;
    private boolean disableRequest;

    public void reset() {
        testRun = null;
        disableRequest = false;
    }

    public void setTestRun(TestRun testRun) {
        this.testRun = testRun;
    }

    public void createTestRun() {
        createTestRun(null, null, false);
    }

    public void createTestRun(String identifier) {
        createTestRun(identifier, null, false);
    }

    public void createTestRun(String identifier, String fileName, boolean disableRequest) {
        this.disableRequest = disableRequest;
        testRun = new TestRun(
                fileName,
                null,
                null,
                null,
                null,
                identifier,
                null,
                null,
                null,
                null,
                0.0,
                null,
                null,
                null,
                false);
    }

    public TestRun getTestRun() {
        return getTestRun(null);
    }

    public TestRun getTestRun(String identifier) {
        if (testRun == null) {
            createTestRun(identifier);
        }
        return testRun;
    }

    public boolean disableRequest() {
        return disableRequest;
    }

    public void saveTestRun(Path path) throws IOException {
        createParentDirectories(path);
        getTestRun().save(path);
    }

    public void saveTestRun(Path path, String saveUnderKey) throws IOException {
        createParentDirectories(path);
        if (saveUnderKey == null || saveUnderKey.isBlank()) {
            saveTestRun(path);
            return;
        }
        var wrapper = Map.of(saveUnderKey, getTestRun().modelDump(true, true));
        var bytes = Utils.serializeToJson(wrapper).getBytes(StandardCharsets.UTF_8);
        try (var channel = FileChannel.open(
                path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            var buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }

    public void updateTestRun(LlmApiTestCase apiTestCase, LlmTestCase testCase) {
        if (shouldSkip(apiTestCase)) {
            return;
        }
        if (testRun == null) {
            createTestRun();
        }
        testRun = testRun.addTestCase(apiTestCase).setDatasetProperties(testCase);
    }

    public void updateTestRun(ConversationalApiTestCase apiTestCase, ConversationalTestCase testCase) {
        if (apiTestCase.metricsData() != null && apiTestCase.metricsData().isEmpty()) {
            return;
        }
        if (testRun == null) {
            createTestRun();
        }
        testRun = testRun.addTestCase(apiTestCase).setDatasetProperties(testCase);
    }

    public void clearTestRun() {
        testRun = null;
    }

    private static boolean shouldSkip(LlmApiTestCase apiTestCase) {
        return apiTestCase.metricsData() != null && apiTestCase.metricsData().isEmpty() && apiTestCase.trace() == null;
    }

    private static void createParentDirectories(Path path) throws IOException {
        var parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }
}
