package dev.jeval.prompt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelSettingsTest {

    @Test
    void keepsDeepEvalModelSettingsFields() {
        var settings = new ModelSettings(
                ModelProvider.OPEN_AI,
                "gpt-4.1",
                0.2,
                1024,
                0.9,
                0.1,
                0.3,
                List.of("stop"),
                ReasoningEffort.MEDIUM,
                Verbosity.HIGH);

        assertEquals(ModelProvider.OPEN_AI, settings.provider());
        assertEquals("gpt-4.1", settings.name());
        assertEquals(0.2, settings.temperature());
        assertEquals(1024, settings.maxTokens());
        assertEquals(0.9, settings.topP());
        assertEquals(0.1, settings.frequencyPenalty());
        assertEquals(0.3, settings.presencePenalty());
        assertEquals(List.of("stop"), settings.stopSequence());
        assertEquals(ReasoningEffort.MEDIUM, settings.reasoningEffort());
        assertEquals(Verbosity.HIGH, settings.verbosity());
    }

    @Test
    void copiesStopSequenceList() {
        var stops = new ArrayList<>(List.of("END"));

        var settings = new ModelSettings(null, null, null, null, null, null, null, stops, null, null);
        stops.add("mutated");

        assertEquals(List.of("END"), settings.stopSequence());
        assertThrows(UnsupportedOperationException.class, () -> settings.stopSequence().add("blocked"));
    }

    @Test
    void optionalFieldsCanBeNull() {
        var settings = new ModelSettings(null, null, null, null, null, null, null, null, null, null);

        assertNull(settings.provider());
        assertNull(settings.stopSequence());
    }

    @Test
    void rejectsInvalidNumericValues() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ModelSettings(null, null, Double.NaN, null, null, null, null, null, null, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ModelSettings(null, null, null, -1, null, null, null, null, null, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ModelSettings(null, null, null, null, Double.POSITIVE_INFINITY, null, null, null, null, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ModelSettings(null, null, null, null, null, Double.NaN, null, null, null, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ModelSettings(null, null, null, null, null, null, Double.POSITIVE_INFINITY, null, null, null)));
    }
}
