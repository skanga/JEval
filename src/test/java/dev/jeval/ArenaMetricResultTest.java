package dev.jeval;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ArenaMetricResultTest {

    @Test
    void requiresNameAndWinner() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ArenaMetricResult(null, "model-a", true, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ArenaMetricResult(" ", "model-a", true, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ArenaMetricResult("preference", null, true, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ArenaMetricResult("preference", " ", true, null)));
    }
}
