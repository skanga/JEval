package dev.jeval.synthesizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ContextConstructionConfigTest {
    @Test
    void defaultConfigMatchesDeepEvalCrossFileDefaults() {
        var config = ContextConstructionConfig.DEFAULT;

        assertEquals(false, config.allowCrossFileContexts());
        assertEquals(null, config.targetFilesPerContext());
        assertEquals(3, config.maxFilesPerContext());
    }

    @Test
    void legacyConstructorKeepsCrossFileDefaults() {
        var config = new ContextConstructionConfig(3, 1, 1024, 0, 0.5, 0.0, 3);

        assertEquals(false, config.allowCrossFileContexts());
        assertEquals(null, config.targetFilesPerContext());
        assertEquals(3, config.maxFilesPerContext());
    }

    @Test
    void validatesCrossFileMergeOptionsLikeDeepEval() {
        assertThrows(IllegalArgumentException.class,
                () -> new ContextConstructionConfig(3, 1, 1024, 0, 0.5, 0.0, 3, true, 1, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new ContextConstructionConfig(3, 1, 1024, 0, 0.5, 0.0, 3, true, 2, 1));
    }
}
