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
        assertEquals(null, config.encoding());
    }

    @Test
    void legacyConstructorKeepsCrossFileDefaults() {
        var config = new ContextConstructionConfig(3, 1, 1024, 0, 0.5, 0.0, 3);

        assertEquals(false, config.allowCrossFileContexts());
        assertEquals(null, config.targetFilesPerContext());
        assertEquals(3, config.maxFilesPerContext());
        assertEquals(1, config.maxContextLength());
        assertEquals(1, config.minContextLength());
        assertEquals(null, config.encoding());
    }

    @Test
    void acceptsDocumentEncodingLikeDeepEval() {
        var config = new ContextConstructionConfig(3, 1, 3, 1, 1024, 0, 0.5, 0.0, 3,
                false, null, 3, "UTF-16");

        assertEquals("UTF-16", config.encoding());
    }

    @Test
    void rejectsUnsupportedDocumentEncodingLikeDeepEval() {
        assertThrows(IllegalArgumentException.class,
                () -> new ContextConstructionConfig(3, 1, 3, 1, 1024, 0, 0.5, 0.0, 3,
                        false, null, 3, "not-a-charset"));
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
        assertThrows(IllegalArgumentException.class,
                () -> new ContextConstructionConfig(3, 1, 1024, 0, 0.5, 0.0, 3, true, 4, 3));
    }

    @Test
    void validatesFiniteThresholdsLikeDeepEval() {
        assertThrows(IllegalArgumentException.class,
                () -> new ContextConstructionConfig(3, 1, 3, 1, 1024, 0, Double.NaN, 0.0, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new ContextConstructionConfig(3, 1, 3, 1, 1024, 0, Double.POSITIVE_INFINITY, 0.0, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new ContextConstructionConfig(3, 1, 3, 1, 1024, 0, 0.5, Double.NaN, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new ContextConstructionConfig(3, 1, 3, 1, 1024, 0, 0.5, Double.NEGATIVE_INFINITY, 3));
    }
}
