package dev.jeval.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OptimizerConfigTest {

    @Test
    void asyncConfigDefaultsMatchDeepEval() {
        var config = new AsyncConfig();

        assertTrue(config.runAsync());
        assertEquals(0.0, config.throttleValue());
        assertEquals(20, config.maxConcurrent());
    }

    @Test
    void asyncConfigRejectsInvalidBounds() {
        assertThrows(IllegalArgumentException.class, () -> new AsyncConfig(true, 0.0, 0));
        assertThrows(IllegalArgumentException.class, () -> new AsyncConfig(true, -0.1, 1));
    }

    @Test
    void displayConfigDefaultsMatchOptimizerDeepEvalConfig() {
        var config = new DisplayConfig();

        assertTrue(config.showIndicator());
        assertFalse(config.announceTies());
    }

    @Test
    void configRecordsPreserveExplicitValues() {
        assertEquals(new AsyncConfig(false, 0.25, 3), new AsyncConfig(false, 0.25, 3));
        assertEquals(new DisplayConfig(false, true), new DisplayConfig(false, true));
    }
}
