package dev.jeval.synthesizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class FiltrationConfigTest {
    @Test
    void defaultConfigMatchesDeepEvalFiltrationDefaults() {
        var config = new FiltrationConfig();

        assertEquals(0.5, config.syntheticInputQualityThreshold());
        assertEquals(3, config.maxQualityRetries());
        assertEquals(null, config.criticModel());
    }

    @Test
    void validatesThresholdAndRetryCount() {
        assertThrows(IllegalArgumentException.class, () -> new FiltrationConfig(-0.1, 3, null));
        assertThrows(IllegalArgumentException.class, () -> new FiltrationConfig(1.1, 3, null));
        assertThrows(IllegalArgumentException.class, () -> new FiltrationConfig(Double.NaN, 3, null));
        assertThrows(IllegalArgumentException.class, () -> new FiltrationConfig(Double.POSITIVE_INFINITY, 3, null));
        assertThrows(IllegalArgumentException.class, () -> new FiltrationConfig(Double.NEGATIVE_INFINITY, 3, null));
        assertThrows(IllegalArgumentException.class, () -> new FiltrationConfig(0.5, -1, null));
    }
}
