package dev.jeval;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class DeepEvalTest {

    @Test
    void versionMatchesPinnedDeepEvalVersion() {
        assertEquals("4.0.7", DeepEval.VERSION);
    }

    @Test
    void compareVersionsMatchesDeepEvalNormalizeAndGreaterThanBehavior() {
        assertTrue(DeepEval.compareVersions("4.0.8", "4.0.7"));
        assertTrue(DeepEval.compareVersions("4.1.0", "4.0.99"));
        assertFalse(DeepEval.compareVersions("4.0.0", "4"));
        assertFalse(DeepEval.compareVersions("4.0.7", "4.0.7"));
        assertFalse(DeepEval.compareVersions("4.0.7", "4.0.8"));
    }

    @Test
    void updateWarningOptInMatchesDeepEvalEnvironmentFlag() {
        assertTrue(DeepEval.updateWarningOptIn(Map.of("DEEPEVAL_UPDATE_WARNING_OPT_IN", "1")));
        assertFalse(DeepEval.updateWarningOptIn(Map.of("DEEPEVAL_UPDATE_WARNING_OPT_IN", "true")));
        assertFalse(DeepEval.updateWarningOptIn(Map.of()));
    }
}
