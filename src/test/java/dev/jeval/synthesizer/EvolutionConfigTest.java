package dev.jeval.synthesizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class EvolutionConfigTest {
    @Test
    void defaultConfigMatchesDeepEvalEvolutionDefaults() {
        var config = new EvolutionConfig();

        assertEquals(1, config.numEvolutions());
        assertEquals(List.of(
                Evolution.REASONING,
                Evolution.MULTICONTEXT,
                Evolution.CONCRETIZING,
                Evolution.CONSTRAINED,
                Evolution.COMPARATIVE,
                Evolution.HYPOTHETICAL,
                Evolution.IN_BREADTH), config.evolutions());
    }

    @Test
    void rejectsNullEvolutionEntries() {
        assertThrows(IllegalArgumentException.class,
                () -> new EvolutionConfig(1, java.util.Arrays.asList(Evolution.REASONING, null)));
    }
}
