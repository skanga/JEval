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
        assertEquals(3, config.maxContextLength());
        assertEquals(1, config.minContextLength());
    }

    @Test
    void legacyConstructorKeepsCrossFileDefaults() {
        var config = new ContextConstructionConfig(3, 1, 1024, 0, 0.5, 0.0, 3);

        assertEquals(false, config.allowCrossFileContexts());
        assertEquals(null, config.targetFilesPerContext());
        assertEquals(3, config.maxFilesPerContext());
        assertEquals(1, config.maxContextLength());
        assertEquals(1, config.minContextLength());
    }

    @Test
    void validatesContextLengthOptionsLikeDeepEval() {
        assertThrows(IllegalArgumentException.class,
                () -> new ContextConstructionConfig(3, 1, 0, 1, 1024, 0, 0.5, 0.0, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new ContextConstructionConfig(3, 1, 2, 0, 1024, 0, 0.5, 0.0, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new ContextConstructionConfig(3, 1, 1, 2, 1024, 0, 0.5, 0.0, 3));
    }

    @Test
    void validatesCrossFileMergeOptionsLikeDeepEval() {
        assertThrows(IllegalArgumentException.class,
                () -> new ContextConstructionConfig(3, 1, 1024, 0, 0.5, 0.0, 3, true, 1, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new ContextConstructionConfig(3, 1, 1024, 0, 0.5, 0.0, 3, true, 2, 1));
    }
}
