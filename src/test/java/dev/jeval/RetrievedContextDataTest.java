package dev.jeval;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RetrievedContextDataTest {

    @Test
    void serializesAsSourceColonContextLikeDeepEval() {
        var context = new RetrievedContextData("Refunds are available for 30 days", "policy.md");

        assertEquals("policy.md: Refunds are available for 30 days", context.toString());
    }

    @Test
    void modelDumpUsesDeepEvalCustomSerializerString() {
        var context = new RetrievedContextData("Refunds are available for 30 days", "policy.md");

        assertEquals("policy.md: Refunds are available for 30 days", context.modelDump());
    }

    @Test
    void markerUsesFirstContextDelimiterLikeDeepEval() {
        var context = RetrievedContextData.fromMarker("deepeval_source=a,deepeval_context=b,deepeval_context=c");

        assertEquals(new RetrievedContextData("b,deepeval_context=c", "a"), context);
    }
}
