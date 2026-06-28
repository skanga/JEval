package dev.jeval;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class EvaluationDatasetTest {

    @Test
    void singleTurnDatasetEvaluatesStoredTestCases() {
        var dataset = new EvaluationDataset();
        dataset.addTestCase(new LlmTestCase("2+2?", "4", "4"));
        dataset.addTestCase(new LlmTestCase("2+2?", "5", "4"));

        var results = dataset.evaluate(List.of(new ActualEqualsExpectedMetric()));

        assertAll(
                () -> assertFalse(dataset.multiTurn()),
                () -> assertEquals(2, dataset.testCases().size()),
                () -> assertTrue(results.getFirst().success()),
                () -> assertFalse(results.getLast().success()));
    }

    @Test
    void singleTurnAddTestCaseUsesDatasetRankIndex() {
        var dataset = new EvaluationDataset();
        dataset.addTestCase(new LlmTestCase("first", null, null));
        dataset.addTestCase(new LlmTestCase("second", null, null));

        assertAll(
                () -> assertEquals(0, dataset.testCases().getFirst().datasetRank()),
                () -> assertEquals(1, dataset.testCases().getLast().datasetRank()));
    }

    @Test
    void conversationalDatasetEvaluatesStoredTestCases() {
        var dataset = new EvaluationDataset();
        dataset.addTestCase(ConversationalTestCase.builder(List.of(new Turn("user", "hello"))).build());
        dataset.addTestCase(ConversationalTestCase.builder(List.of(new Turn("user", ""))).build());

        var results = dataset.evaluateConversations(List.of(new NonEmptyFirstTurnMetric()));

        assertAll(
                () -> assertTrue(dataset.multiTurn()),
                () -> assertEquals(2, dataset.conversationalTestCases().size()),
                () -> assertTrue(results.getFirst().success()),
                () -> assertFalse(results.getLast().success()));
    }

    @Test
    void conversationalAddTestCaseUsesDatasetRankIndex() {
        var dataset = new EvaluationDataset();
        dataset.addTestCase(ConversationalTestCase.builder(List.of(new Turn("user", "hello"))).build());
        dataset.addTestCase(ConversationalTestCase.builder(List.of(new Turn("user", "goodbye"))).build());

        assertAll(
                () -> assertEquals(0, dataset.conversationalTestCases().getFirst().datasetRank()),
                () -> assertEquals(1, dataset.conversationalTestCases().getLast().datasetRank()));
    }

    @Test
    void datasetRejectsMixingSingleAndMultiTurnCases() {
        var dataset = new EvaluationDataset();
        dataset.addTestCase(new LlmTestCase("2+2?", "4", "4"));

        assertThrows(IllegalArgumentException.class,
                () -> dataset.addTestCase(ConversationalTestCase.builder(List.of(new Turn("user", "hello"))).build()));
    }

    @Test
    void datasetStoresSingleTurnGoldens() {
        var dataset = new EvaluationDataset();
        dataset.addGolden(Golden.builder("input").actualOutput("actual").build());

        assertAll(
                () -> assertFalse(dataset.multiTurn()),
                () -> assertEquals(1, dataset.goldens().size()),
                () -> assertEquals(1, dataset.testCasesFromGoldens().size()));
    }

    @Test
    void singleTurnTestCasesFromGoldensUseDatasetRankIndex() {
        var dataset = new EvaluationDataset();
        dataset.addGolden(Golden.builder("first").build());
        dataset.addGolden(Golden.builder("second").build());

        var testCases = dataset.testCasesFromGoldens();

        assertAll(
                () -> assertEquals(0, testCases.getFirst().datasetRank()),
                () -> assertEquals(1, testCases.getLast().datasetRank()));
    }

    @Test
    void datasetCanBeConstructedFromSingleTurnGoldens() {
        var golden = Golden.builder("input").actualOutput("actual").build();

        var dataset = new EvaluationDataset(List.of(golden));

        assertAll(
                () -> assertFalse(dataset.multiTurn()),
                () -> assertEquals(List.of(golden), dataset.goldens()),
                () -> assertEquals(1, dataset.testCasesFromGoldens().size()));
    }

    @Test
    void singleTurnDatasetEvaluatesStoredGoldens() {
        var dataset = new EvaluationDataset();
        dataset.addGolden(Golden.builder("2+2?").actualOutput("4").expectedOutput("4").build());
        dataset.addGolden(Golden.builder("2+2?").actualOutput("5").expectedOutput("4").build());

        var results = dataset.evaluate(List.of(new ActualEqualsExpectedMetric()));

        assertAll(
                () -> assertEquals(2, results.size()),
                () -> assertTrue(results.getFirst().success()),
                () -> assertFalse(results.getLast().success()));
    }

    @Test
    void datasetRejectsMixingSingleTurnGoldenWithMultiTurnCase() {
        var dataset = new EvaluationDataset();
        dataset.addTestCase(ConversationalTestCase.builder(List.of(new Turn("user", "hello"))).build());

        assertThrows(IllegalArgumentException.class,
                () -> dataset.addGolden(Golden.builder("input").build()));
    }

    @Test
    void datasetStoresConversationalGoldens() {
        var dataset = new EvaluationDataset();
        dataset.addGolden(ConversationalGolden.builder("scenario")
                .turns(List.of(new Turn("user", "hello")))
                .build());

        assertAll(
                () -> assertTrue(dataset.multiTurn()),
                () -> assertEquals(1, dataset.conversationalGoldens().size()),
                () -> assertEquals(1, dataset.conversationalTestCasesFromGoldens().size()));
    }

    @Test
    void conversationalTestCasesFromGoldensUseDatasetRankIndex() {
        var dataset = new EvaluationDataset();
        dataset.addGolden(ConversationalGolden.builder("first")
                .turns(List.of(new Turn("user", "hello")))
                .build());
        dataset.addGolden(ConversationalGolden.builder("second")
                .turns(List.of(new Turn("user", "goodbye")))
                .build());

        var testCases = dataset.conversationalTestCasesFromGoldens();

        assertAll(
                () -> assertEquals(0, testCases.getFirst().datasetRank()),
                () -> assertEquals(1, testCases.getLast().datasetRank()));
    }

    @Test
    void datasetCanBeConstructedFromConversationalGoldens() {
        var golden = ConversationalGolden.builder("scenario")
                .turns(List.of(new Turn("user", "hello")))
                .build();

        var dataset = new EvaluationDataset(List.of(golden));

        assertAll(
                () -> assertTrue(dataset.multiTurn()),
                () -> assertEquals(List.of(golden), dataset.conversationalGoldens()),
                () -> assertEquals(1, dataset.conversationalTestCasesFromGoldens().size()));
    }

    @Test
    void conversationalDatasetEvaluatesStoredGoldens() {
        var dataset = new EvaluationDataset();
        dataset.addGolden(ConversationalGolden.builder("greeting")
                .turns(List.of(new Turn("user", "hello")))
                .build());
        dataset.addGolden(ConversationalGolden.builder("empty")
                .turns(List.of(new Turn("user", "")))
                .build());

        var results = dataset.evaluateConversations(List.of(new NonEmptyFirstTurnMetric()));

        assertAll(
                () -> assertEquals(2, results.size()),
                () -> assertTrue(results.getFirst().success()),
                () -> assertFalse(results.getLast().success()));
    }

    @Test
    void emptyDatasetCannotBeEvaluated() {
        assertThrows(IllegalStateException.class,
                () -> new EvaluationDataset().evaluate(List.of(new ActualEqualsExpectedMetric())));
    }

    private static final class ActualEqualsExpectedMetric implements Metric {
        @Override
        public MetricResult measure(LlmTestCase testCase) {
            var success = testCase.actualOutput().equals(testCase.expectedOutput());
            return new MetricResult("actual equals expected", success ? 1.0 : 0.0, 1.0, success, "");
        }
    }

    private static final class NonEmptyFirstTurnMetric implements ConversationalMetric {
        @Override
        public MetricResult measure(ConversationalTestCase testCase) {
            var success = !testCase.turns().getFirst().content().isEmpty();
            return new MetricResult("non-empty first turn", success ? 1.0 : 0.0, 1.0, success, "");
        }
    }
}
