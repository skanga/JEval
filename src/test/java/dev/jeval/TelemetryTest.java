package dev.jeval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TelemetryTest {
    @TempDir
    Path tempDir;

    @Test
    void featureValuesMatchDeepEvalTelemetry() {
        assertEquals("redteaming", Telemetry.Feature.REDTEAMING.value());
        assertEquals("synthesizer", Telemetry.Feature.SYNTHESIZER.value());
        assertEquals("evaluation", Telemetry.Feature.EVALUATION.value());
        assertEquals("component_evaluation", Telemetry.Feature.COMPONENT_EVALUATION.value());
        assertEquals("guardrail", Telemetry.Feature.GUARDRAIL.value());
        assertEquals("benchmark", Telemetry.Feature.BENCHMARK.value());
        assertEquals("conversation_simulator", Telemetry.Feature.CONVERSATION_SIMULATOR.value());
        assertEquals("unknown", Telemetry.Feature.UNKNOWN.value());
        assertEquals("tracing_integration", Telemetry.Feature.TRACING_INTEGRATION.value());
    }

    @Test
    void telemetryFileHelpersMatchDeepEvalLocalBehavior() throws Exception {
        var telemetry = new Telemetry(tempDir, Map.of());

        assertEquals(".deepeval_telemetry.txt", Telemetry.TELEMETRY_DATA_FILE);
        assertEquals(tempDir.resolve(Telemetry.TELEMETRY_DATA_FILE), telemetry.path());
        assertEquals("new", telemetry.getStatus());
        assertEquals(Telemetry.Feature.UNKNOWN, telemetry.getLastFeature());
        assertEquals("NA", telemetry.getLoggedInWith());

        telemetry.writeTelemetryFile(Map.of("LOGGED_IN_WITH", "github"));

        assertEquals("github", telemetry.getLoggedInWith());
    }

    @Test
    void uniqueIdAndFeatureStatusMatchDeepEvalTelemetry() throws Exception {
        var telemetry = new Telemetry(tempDir, Map.of());

        var uniqueId = telemetry.getUniqueId();
        assertFalse(uniqueId.isBlank());
        assertEquals("new", telemetry.getStatus());

        assertEquals(uniqueId, telemetry.getUniqueId());
        assertEquals("old", telemetry.getStatus());

        assertEquals("new", telemetry.getFeatureStatus(Telemetry.Feature.SYNTHESIZER));
        telemetry.setLastFeature(Telemetry.Feature.SYNTHESIZER);
        assertEquals(Telemetry.Feature.SYNTHESIZER, telemetry.getLastFeature());
        assertEquals("old", telemetry.getFeatureStatus(Telemetry.Feature.SYNTHESIZER));
    }

    @Test
    void telemetryOptOutSkipsWritesLikeDeepEval() throws Exception {
        var telemetry = new Telemetry(tempDir, Map.of("DEEPEVAL_TELEMETRY_OPT_OUT", "true"));

        assertTrue(telemetry.telemetryOptOut());
        assertEquals("telemetry-opted-out", telemetry.getUniqueId());
        telemetry.writeTelemetryFile(Map.of("DEEPEVAL_ID", "abc"));

        assertFalse(Files.exists(telemetry.path()));
    }
}
