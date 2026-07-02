package dev.jeval.tracing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TraceTestingManagerTest {

    @Test
    void waitForTestDictReturnsExistingTestDict() throws Exception {
        var manager = new TraceTestingManager();
        manager.setTestDict(Map.of("score", 1));

        assertEquals(Map.of("score", 1), manager.waitForTestDict(Duration.ofMillis(50), Duration.ofMillis(5)));
    }

    @Test
    void waitForTestDictReturnsEmptyMapOnTimeoutLikeDeepEval() throws Exception {
        var manager = new TraceTestingManager();

        assertEquals(Map.of(), manager.waitForTestDict(Duration.ofMillis(10), Duration.ofMillis(1)));
    }
}
