package dev.jeval;

import java.util.List;

public final class ApiTestCases {
    private ApiTestCases() {
    }

    public static LlmApiTestCase from(LlmTestCase testCase, Integer index) {
        var order = testCase.datasetRank() != null ? testCase.datasetRank() : index;
        var name = testCase.name() != null ? testCase.name() : "test_case_" + pythonNameIndex(order);
        return new LlmApiTestCase(
                name,
                testCase.input(),
                testCase.actualOutput(),
                testCase.expectedOutput(),
                testCase.context(),
                RetrievedContextData.textValues(testCase.retrievalContext()),
                testCase.toolsCalled(),
                testCase.expectedTools(),
                testCase.tokenCost(),
                testCase.completionTime(),
                testCase.imagesMapping(),
                true,
                List.of(),
                null,
                null,
                order,
                testCase.additionalMetadata(),
                testCase.comments(),
                testCase.tags(),
                testCase.trace(),
                testCase.mcpServers(),
                testCase.mcpToolsCalled(),
                testCase.mcpResourcesCalled(),
                testCase.mcpPromptsCalled());
    }

    public static ConversationalApiTestCase from(ConversationalTestCase testCase, Integer index) {
        var order = testCase.datasetRank() != null ? testCase.datasetRank() : index;
        var name = testCase.name() != null ? testCase.name() : "conversational_test_case_" + pythonNameIndex(order);
        return new ConversationalApiTestCase(
                name,
                true,
                List.of(),
                0.0,
                null,
                turns(testCase.turns()),
                order,
                testCase.scenario(),
                testCase.expectedOutcome(),
                testCase.userDescription(),
                testCase.context(),
                testCase.comments(),
                testCase.metadata(),
                testCase.imagesMapping(),
                testCase.tags(),
                testCase.mcpServers());
    }

    private static List<TurnApi> turns(List<Turn> turns) {
        return java.util.stream.IntStream.range(0, turns.size())
                .mapToObj(index -> turn(turns.get(index), index))
                .toList();
    }

    private static TurnApi turn(Turn turn, int index) {
        return new TurnApi(
                turn.role(),
                turn.content(),
                index,
                turn.userId(),
                RetrievedContextData.textValues(turn.retrievalContext()),
                turn.toolsCalled(),
                null,
                turn.mcpToolsCalled(),
                turn.mcpResourcesCalled(),
                turn.mcpPromptsCalled());
    }

    private static String pythonNameIndex(Integer order) {
        return order == null ? "None" : order.toString();
    }
}
