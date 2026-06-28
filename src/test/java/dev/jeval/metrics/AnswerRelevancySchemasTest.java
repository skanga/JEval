package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnswerRelevancySchemasTest {

    @Test
    void statementsAndVerdictsCopyLists() {
        var statements = new ArrayList<>(List.of("The sky is blue."));
        var verdicts = new ArrayList<>(List.of(new AnswerRelevancySchemas.AnswerRelevancyVerdict("yes", "relevant")));

        var statementResponse = new AnswerRelevancySchemas.Statements(statements);
        var verdictResponse = new AnswerRelevancySchemas.Verdicts(verdicts);

        statements.add("ignored");
        verdicts.add(new AnswerRelevancySchemas.AnswerRelevancyVerdict("no", "ignored"));

        assertEquals(List.of("The sky is blue."), statementResponse.statements());
        assertEquals(1, verdictResponse.verdicts().size());
        assertThrows(UnsupportedOperationException.class, () -> statementResponse.statements().add("nope"));
    }

    @Test
    void verdictAllowsDeepEvalLiteralValuesOnly() {
        assertEquals("yes", new AnswerRelevancySchemas.AnswerRelevancyVerdict("yes", null).verdict());
        assertEquals("no", new AnswerRelevancySchemas.AnswerRelevancyVerdict("no", null).verdict());
        assertEquals("idk", new AnswerRelevancySchemas.AnswerRelevancyVerdict("idk", null).verdict());
        assertThrows(IllegalArgumentException.class,
                () -> new AnswerRelevancySchemas.AnswerRelevancyVerdict("maybe", null));
    }

    @Test
    void scoreReasonKeepsReason() {
        var reason = new AnswerRelevancySchemas.AnswerRelevancyScoreReason("Most statements answer the input.");

        assertEquals("Most statements answer the input.", reason.reason());
    }

    @Test
    void parsesStatementsFromModelJson() {
        var statements = AnswerRelevancySchemas.parseStatements(
                "prefix {\"statements\": [\"Return policy exists\", \"Support is available\",]} suffix");

        assertEquals(List.of("Return policy exists", "Support is available"), statements.statements());
    }

    @Test
    void parsesVerdictsFromModelJson() {
        var verdicts = AnswerRelevancySchemas.parseVerdicts("""
                {
                  "verdicts": [
                    {"verdict": "yes"},
                    {"reason": "unrelated", "verdict": "no"}
                  ]
                }
                """);

        assertEquals(List.of(
                new AnswerRelevancySchemas.AnswerRelevancyVerdict("yes", null),
                new AnswerRelevancySchemas.AnswerRelevancyVerdict("no", "unrelated")), verdicts.verdicts());
    }

    @Test
    void parsesScoreReasonFromModelJson() {
        var reason = AnswerRelevancySchemas.parseScoreReason("{\"reason\": \"One statement is unrelated.\"}");

        assertEquals("One statement is unrelated.", reason.reason());
    }

    @Test
    void rejectsMissingOrInvalidFieldsLikeDeepEval() {
        assertThrows(IllegalArgumentException.class, () -> AnswerRelevancySchemas.parseStatements("{}"));
        assertThrows(IllegalArgumentException.class, () -> AnswerRelevancySchemas.parseStatements("{\"statements\":\"one\"}"));
        assertThrows(IllegalArgumentException.class, () -> AnswerRelevancySchemas.parseStatements("{\"statements\":[1]}"));
        assertThrows(IllegalArgumentException.class, () -> AnswerRelevancySchemas.parseVerdicts("{}"));
        assertThrows(IllegalArgumentException.class, () -> AnswerRelevancySchemas.parseVerdicts("{\"verdicts\":\"yes\"}"));
        assertThrows(IllegalArgumentException.class, () -> AnswerRelevancySchemas.parseVerdicts("{\"verdicts\":[{\"reason\":\"ok\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> AnswerRelevancySchemas.parseVerdicts("{\"verdicts\":[{\"verdict\":1}]}"));
        assertThrows(IllegalArgumentException.class, () -> AnswerRelevancySchemas.parseVerdicts("{\"verdicts\":[{\"verdict\":\"yes\",\"reason\":1}]}"));
        assertThrows(IllegalArgumentException.class, () -> AnswerRelevancySchemas.parseScoreReason("{}"));
        assertThrows(IllegalArgumentException.class, () -> AnswerRelevancySchemas.parseScoreReason("{\"reason\":1}"));
    }
}
