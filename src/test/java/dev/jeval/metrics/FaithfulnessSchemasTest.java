package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FaithfulnessSchemasTest {

    @Test
    void wrappersCopyLists() {
        var truths = new ArrayList<>(List.of("Paris is in France."));
        var claims = new ArrayList<>(List.of("Paris is in France."));
        var verdicts = new ArrayList<>(List.of(new FaithfulnessSchemas.FaithfulnessVerdict("yes", null)));

        var truthsResponse = new FaithfulnessSchemas.Truths(truths);
        var claimsResponse = new FaithfulnessSchemas.Claims(claims);
        var verdictsResponse = new FaithfulnessSchemas.Verdicts(verdicts);

        truths.add("ignored");
        claims.add("ignored");
        verdicts.add(new FaithfulnessSchemas.FaithfulnessVerdict("no", "ignored"));

        assertEquals(List.of("Paris is in France."), truthsResponse.truths());
        assertEquals(List.of("Paris is in France."), claimsResponse.claims());
        assertEquals(1, verdictsResponse.verdicts().size());
        assertThrows(UnsupportedOperationException.class, () -> claimsResponse.claims().add("nope"));
    }

    @Test
    void verdictAllowsDeepEvalLiteralValuesOnly() {
        assertEquals("yes", new FaithfulnessSchemas.FaithfulnessVerdict("yes", null).verdict());
        assertEquals("no", new FaithfulnessSchemas.FaithfulnessVerdict("no", null).verdict());
        assertEquals("idk", new FaithfulnessSchemas.FaithfulnessVerdict("idk", null).verdict());
        assertThrows(IllegalArgumentException.class,
                () -> new FaithfulnessSchemas.FaithfulnessVerdict("maybe", null));
    }

    @Test
    void parsesModelJson() {
        var truths = FaithfulnessSchemas.parseTruths("{\"truths\": [\"A\", \"B\",]}");
        var claims = FaithfulnessSchemas.parseClaims("prefix {\"claims\": [\"C\"]} suffix");
        var verdicts = FaithfulnessSchemas.parseVerdicts("""
                {"verdicts": [{"verdict": "idk", "reason": "unclear"}, {"verdict": "no"}]}
                """);
        var reason = FaithfulnessSchemas.parseScoreReason("{\"reason\": \"One claim is unsupported.\"}");
        var interaction = FaithfulnessSchemas.parseInteractionScore("""
                {"score":"0.5","reason":null,"claims":["C"],"truths":["T"],"verdicts":[{"verdict":"yes"}]}
                """);

        assertEquals(List.of("A", "B"), truths.truths());
        assertEquals(List.of("C"), claims.claims());
        assertEquals(List.of(
                new FaithfulnessSchemas.FaithfulnessVerdict("idk", "unclear"),
                new FaithfulnessSchemas.FaithfulnessVerdict("no", null)), verdicts.verdicts());
        assertEquals("One claim is unsupported.", reason.reason());
        assertEquals(0.5, interaction.score());
        assertEquals(List.of("C"), interaction.claims());
        assertEquals(List.of("T"), interaction.truths());
        assertEquals(List.of(new FaithfulnessSchemas.FaithfulnessVerdict("yes", null)), interaction.verdicts());
    }

    @Test
    void rejectsMissingOrInvalidFieldsLikeDeepEval() {
        assertThrows(IllegalArgumentException.class, () -> FaithfulnessSchemas.parseTruths("{}"));
        assertThrows(IllegalArgumentException.class, () -> FaithfulnessSchemas.parseTruths("{\"truths\":\"A\"}"));
        assertThrows(IllegalArgumentException.class, () -> FaithfulnessSchemas.parseTruths("{\"truths\":[1]}"));
        assertThrows(IllegalArgumentException.class, () -> FaithfulnessSchemas.parseClaims("{}"));
        assertThrows(IllegalArgumentException.class, () -> FaithfulnessSchemas.parseClaims("{\"claims\":\"C\"}"));
        assertThrows(IllegalArgumentException.class, () -> FaithfulnessSchemas.parseClaims("{\"claims\":[1]}"));
        assertThrows(IllegalArgumentException.class, () -> FaithfulnessSchemas.parseVerdicts("{}"));
        assertThrows(IllegalArgumentException.class, () -> FaithfulnessSchemas.parseVerdicts("{\"verdicts\":\"yes\"}"));
        assertThrows(IllegalArgumentException.class, () -> FaithfulnessSchemas.parseVerdicts("{\"verdicts\":[{\"reason\":\"ok\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> FaithfulnessSchemas.parseVerdicts("{\"verdicts\":[{\"verdict\":\"maybe\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> FaithfulnessSchemas.parseVerdicts("{\"verdicts\":[{\"verdict\":1}]}"));
        assertThrows(IllegalArgumentException.class, () -> FaithfulnessSchemas.parseVerdicts("{\"verdicts\":[{\"verdict\":\"yes\",\"reason\":1}]}"));
        assertThrows(IllegalArgumentException.class, () -> FaithfulnessSchemas.parseScoreReason("{}"));
        assertThrows(IllegalArgumentException.class, () -> FaithfulnessSchemas.parseScoreReason("{\"reason\":1}"));
        assertThrows(IllegalArgumentException.class, () -> FaithfulnessSchemas.parseInteractionScore("{}"));
        assertThrows(IllegalArgumentException.class, () -> FaithfulnessSchemas.parseInteractionScore("{\"score\":1,\"claims\":[],\"truths\":[],\"verdicts\":[]}"));
        assertThrows(IllegalArgumentException.class, () -> FaithfulnessSchemas.parseInteractionScore("{\"score\":1,\"reason\":null,\"truths\":[],\"verdicts\":[]}"));
        assertThrows(IllegalArgumentException.class, () -> FaithfulnessSchemas.parseInteractionScore("{\"score\":1,\"reason\":null,\"claims\":[],\"verdicts\":[]}"));
        assertThrows(IllegalArgumentException.class, () -> FaithfulnessSchemas.parseInteractionScore("{\"score\":1,\"reason\":null,\"claims\":[],\"truths\":[]}"));
        assertThrows(IllegalArgumentException.class, () -> FaithfulnessSchemas.parseInteractionScore("{\"score\":\"bad\",\"reason\":null,\"claims\":[],\"truths\":[],\"verdicts\":[]}"));
        assertThrows(IllegalArgumentException.class, () -> FaithfulnessSchemas.parseInteractionScore("{\"score\":1,\"reason\":1,\"claims\":[],\"truths\":[],\"verdicts\":[]}"));
        assertThrows(IllegalArgumentException.class, () -> FaithfulnessSchemas.parseInteractionScore("{\"score\":1,\"reason\":null,\"claims\":[1],\"truths\":[],\"verdicts\":[]}"));
        assertThrows(IllegalArgumentException.class, () -> FaithfulnessSchemas.parseInteractionScore("{\"score\":1,\"reason\":null,\"claims\":[],\"truths\":[1],\"verdicts\":[]}"));
        assertThrows(IllegalArgumentException.class, () -> FaithfulnessSchemas.parseInteractionScore("{\"score\":1,\"reason\":null,\"claims\":[],\"truths\":[],\"verdicts\":\"yes\"}"));
        assertThrows(IllegalArgumentException.class, () -> FaithfulnessSchemas.parseInteractionScore("{\"score\":1,\"reason\":null,\"claims\":[],\"truths\":[],\"verdicts\":[{\"verdict\":\"maybe\"}]}"));
    }
}
