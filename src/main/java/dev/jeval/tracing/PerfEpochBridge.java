package dev.jeval.tracing;

import java.time.Instant;

public final class PerfEpochBridge {
    private static Long offsetNanos;

    private PerfEpochBridge() {
    }

    public static synchronized void initClockBridge() {
        offsetNanos = System.nanoTime() - epochNanos(Instant.now());
    }

    public static double epochNanosToPerfSeconds(long epochNanos) {
        return (epochNanos + offset()) / 1_000_000_000.0;
    }

    public static double perfSecondsNow() {
        return System.nanoTime() / 1_000_000_000.0;
    }

    public static long perfSecondsToEpochNanos(double perfSeconds) {
        return Math.round(perfSeconds * 1_000_000_000.0) - offset();
    }

    private static long offset() {
        if (offsetNanos == null) {
            throw new IllegalStateException("initClockBridge() must be called first!");
        }
        return offsetNanos;
    }

    private static long epochNanos(Instant instant) {
        return instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
    }
}
