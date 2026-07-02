package dev.jeval.tracing;

import java.time.Duration;
import java.util.Map;

public final class TraceTestingManager {
    public static final TraceTestingManager INSTANCE = new TraceTestingManager();

    private volatile String testName;
    private volatile Map<String, Object> testDict;

    public String testName() {
        return testName;
    }

    public void setTestName(String testName) {
        this.testName = testName;
    }

    public Map<String, Object> testDict() {
        return testDict;
    }

    public void setTestDict(Map<String, ?> testDict) {
        this.testDict = testDict == null ? null : Map.copyOf(testDict);
    }

    public Map<String, Object> waitForTestDict(Duration timeout, Duration pollInterval) throws InterruptedException {
        var deadline = System.nanoTime() + timeout.toNanos();
        while (testDict == null && System.nanoTime() < deadline) {
            Thread.sleep(Math.max(1L, pollInterval.toMillis()));
        }
        return testDict == null ? Map.of() : testDict;
    }
}
