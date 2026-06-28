package dev.jeval.metrics.dag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DagSchemasTest {

    @Test
    void parsesDagSchemaValuesFromModelJson() {
        assertEquals("Because.", DagSchemas.parseMetricScoreReason("{\"reason\":\"Because.\"}").reason());
        assertEquals("summary", DagSchemas.parseTaskNodeOutput("{\"output\":\"summary\"}").output());
        assertEquals(List.of("a", "b"), DagSchemas.parseTaskNodeOutput("{\"output\":[\"a\",\"b\"]}").output());
        assertEquals(Map.of("x", "y"), DagSchemas.parseTaskNodeOutput("{\"output\":{\"x\":\"y\"}}").output());

        var binary = DagSchemas.parseBinaryJudgementVerdict("{\"verdict\":true,\"reason\":\"It passes.\"}");
        var nonBinary = DagSchemas.parseNonBinaryJudgementVerdict("{\"verdict\":\"partial\",\"reason\":\"Mixed.\"}");

        assertEquals(true, binary.verdict());
        assertEquals("It passes.", binary.reason());
        assertEquals("partial", nonBinary.verdict());
        assertEquals("Mixed.", nonBinary.reason());
    }

    @Test
    void rejectsMissingRequiredDagSchemaFieldsLikeDeepEval() {
        assertThrows(IllegalArgumentException.class, () -> DagSchemas.parseMetricScoreReason("{}"));
        assertThrows(IllegalArgumentException.class, () -> DagSchemas.parseTaskNodeOutput("{}"));
        assertThrows(IllegalArgumentException.class, () -> DagSchemas.parseBinaryJudgementVerdict("{}"));
        assertThrows(IllegalArgumentException.class, () -> DagSchemas.parseNonBinaryJudgementVerdict("{}"));
    }

    @Test
    void rejectsNonStringDagSchemaValuesLikeDeepEval() {
        assertThrows(IllegalArgumentException.class, () -> DagSchemas.parseMetricScoreReason("{\"reason\":1}"));
        assertThrows(IllegalArgumentException.class, () -> DagSchemas.parseTaskNodeOutput("{\"output\":1}"));
        assertThrows(IllegalArgumentException.class, () -> DagSchemas.parseTaskNodeOutput("{\"output\":[1]}"));
        assertThrows(IllegalArgumentException.class, () -> DagSchemas.parseTaskNodeOutput("{\"output\":{\"x\":1}}"));
        assertThrows(IllegalArgumentException.class, () -> DagSchemas.parseNonBinaryJudgementVerdict("{\"verdict\":1,\"reason\":\"r\"}"));
    }

    @Test
    void parsesAndRejectsBinaryVerdictValuesLikeDeepEval() {
        assertEquals(true, DagSchemas.parseBinaryJudgementVerdict("{\"verdict\":\"yes\",\"reason\":\"r\"}").verdict());
        assertEquals(false, DagSchemas.parseBinaryJudgementVerdict("{\"verdict\":\"no\",\"reason\":\"r\"}").verdict());
        assertEquals(true, DagSchemas.parseBinaryJudgementVerdict("{\"verdict\":1,\"reason\":\"r\"}").verdict());
        assertEquals(false, DagSchemas.parseBinaryJudgementVerdict("{\"verdict\":0,\"reason\":\"r\"}").verdict());

        assertThrows(IllegalArgumentException.class, () -> DagSchemas.parseBinaryJudgementVerdict("{\"verdict\":\"bad\",\"reason\":\"r\"}"));
        assertThrows(IllegalArgumentException.class, () -> DagSchemas.parseBinaryJudgementVerdict("{\"verdict\":2,\"reason\":\"r\"}"));
    }
}
