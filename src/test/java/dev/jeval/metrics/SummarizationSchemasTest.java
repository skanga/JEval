package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SummarizationSchemasTest {

    @Test
    void verdictsCopyLists() {
        var values = new ArrayList<>(List.of(
                new SummarizationSchemas.SummarizationAlignmentVerdict("yes", "Supported.")));

        var verdicts = new SummarizationSchemas.Verdicts(values);

        values.add(new SummarizationSchemas.SummarizationAlignmentVerdict("no", "ignored"));

        assertEquals(1, verdicts.verdicts().size());
        assertThrows(UnsupportedOperationException.class,
                () -> verdicts.verdicts().add(
                        new SummarizationSchemas.SummarizationAlignmentVerdict("no", "nope")));
    }

    @Test
    void parsesModelJson() {
        var verdicts = SummarizationSchemas.parseVerdicts("""
                prefix {"verdicts": [
                  {"verdict": "yes"},
                  {"verdict": "idk", "reason": "Redundant."}
                ]} suffix
                """);
        var questions = SummarizationSchemas.parseQuestions("{\"questions\": [\"What changed?\"]}");
        var answers = SummarizationSchemas.parseAnswers("{\"answers\": [\"yes\", \"no\"]}");
        var reason = SummarizationSchemas.parseScoreReason("{\"reason\": \"Coverage is incomplete.\"}");

        assertEquals(List.of(
                new SummarizationSchemas.SummarizationAlignmentVerdict("yes", null),
                new SummarizationSchemas.SummarizationAlignmentVerdict("idk", "Redundant.")),
                verdicts.verdicts());
        assertEquals(List.of("What changed?"), questions.questions());
        assertEquals(List.of("yes", "no"), answers.answers());
        assertEquals("Coverage is incomplete.", reason.reason());
    }

    @Test
    void rejectsInvalidFieldsLikePydanticSchema() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> SummarizationSchemas.parseVerdicts("{}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> SummarizationSchemas.parseVerdicts("{\"verdicts\":\"yes\"}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> SummarizationSchemas.parseVerdicts("{\"verdicts\":[{\"reason\":\"ok\"}]}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> SummarizationSchemas.parseVerdicts("{\"verdicts\":[{\"verdict\":\"maybe\"}]}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> SummarizationSchemas.parseVerdicts("{\"verdicts\":[{\"verdict\":1}]}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> SummarizationSchemas.parseVerdicts(
                                "{\"verdicts\":[{\"verdict\":\"yes\",\"reason\":1}]}")),
                () -> assertThrows(IllegalArgumentException.class, () -> SummarizationSchemas.parseQuestions("{}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> SummarizationSchemas.parseQuestions("{\"questions\":\"What changed?\"}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> SummarizationSchemas.parseQuestions("{\"questions\":[1]}")),
                () -> assertThrows(IllegalArgumentException.class, () -> SummarizationSchemas.parseAnswers("{}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> SummarizationSchemas.parseAnswers("{\"answers\":\"yes\"}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> SummarizationSchemas.parseAnswers("{\"answers\":[1]}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> SummarizationSchemas.parseScoreReason("{}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> SummarizationSchemas.parseScoreReason("{\"reason\":1}")));
    }
}
