package dev.jeval.tracing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PerfEpochBridgeTest {

    @Test
    void convertsBetweenEpochNanosAndPerfSecondsLikeDeepEvalBridge() {
        assertThrows(IllegalStateException.class, () -> PerfEpochBridge.epochNanosToPerfSeconds(1L));

        PerfEpochBridge.initClockBridge();
        var epochNanos = epochNanos(Instant.now());
        var perfSeconds = PerfEpochBridge.epochNanosToPerfSeconds(epochNanos);
        var roundTripEpochNanos = PerfEpochBridge.perfSecondsToEpochNanos(perfSeconds);

        assertTrue(PerfEpochBridge.perfSecondsNow() > 0.0);
        assertEquals(epochNanos, roundTripEpochNanos, 1_000L);
    }

    private static long epochNanos(Instant instant) {
        return instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
    }
}
