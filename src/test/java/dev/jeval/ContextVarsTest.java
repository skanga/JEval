package dev.jeval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ContextVarsTest {

    @Test
    void currentGoldenDefaultsToNullAndResetsWithToken() {
        assertNull(ContextVars.getCurrentGolden());

        var first = Golden.builder("first").build();
        var second = Golden.builder("second").build();

        var firstToken = ContextVars.setCurrentGolden(first);
        var secondToken = ContextVars.setCurrentGolden(second);

        assertEquals(second, ContextVars.getCurrentGolden());

        ContextVars.resetCurrentGolden(secondToken);
        assertEquals(first, ContextVars.getCurrentGolden());

        ContextVars.resetCurrentGolden(firstToken);
        assertNull(ContextVars.getCurrentGolden());
    }

    @Test
    void setCurrentGoldenAcceptsNullLikeDeepEval() {
        var token = ContextVars.setCurrentGolden(Golden.builder("input").build());

        ContextVars.setCurrentGolden(null);

        assertNull(ContextVars.getCurrentGolden());
        ContextVars.resetCurrentGolden(token);
    }
}
