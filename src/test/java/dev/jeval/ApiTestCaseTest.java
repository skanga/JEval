package dev.jeval;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ApiTestCaseTest {

    @Test
    void createsSingleTurnApiTestCaseFromLlmTestCase() {
        var tool = new ToolCall("search");
        var testCase = LlmTestCase.builder("input")
                .actualOutput("actual")
                .expectedOutput("expected")
                .context(List.of("context"))
                .retrievalContext(List.of(new RetrievedContextData("retrieved", "source")))
                .toolsCalled(List.of(tool))
                .expectedTools(List.of(tool))
                .additionalMetadata(Map.of("k", "v"))
                .comments("comment")
                .tags(List.of("tag"))
                .tokenCost(0.1)
                .completionTime(1.2)
                .name("named case")
                .mcpServers(List.of(Map.of("name", "weather")))
                .mcpToolsCalled(List.of(Map.of("name", "mcp-search")))
                .mcpResourcesCalled(List.of(Map.of("uri", "file://policy")))
                .mcpPromptsCalled(List.of(Map.of("name", "prompt")))
                .build();

        var api = ApiTestCases.from(testCase, 7);

        assertAll(
                () -> assertEquals("named case", api.name()),
                () -> assertEquals("input", api.input()),
                () -> assertEquals("actual", api.actualOutput()),
                () -> assertEquals("expected", api.expectedOutput()),
                () -> assertEquals(List.of("context"), api.context()),
                () -> assertEquals(List.of("retrieved"), api.retrievalContext()),
                () -> assertEquals(List.of(tool), api.toolsCalled()),
                () -> assertEquals(List.of(tool), api.expectedTools()),
                () -> assertEquals(0.1, api.tokenCost()),
                () -> assertEquals(1.2, api.completionTime()),
                () -> assertTrue(api.success()),
                () -> assertEquals(List.of(), api.metricsData()),
                () -> assertNull(api.runDuration()),
                () -> assertNull(api.evaluationCost()),
                () -> assertEquals(7, api.order()),
                () -> assertEquals(Map.of("k", "v"), api.metadata()),
                () -> assertEquals("comment", api.comments()),
                () -> assertEquals(List.of("tag"), api.tags()),
                () -> assertEquals(List.of(Map.of("name", "weather")), api.mcpServers()),
                () -> assertEquals(List.of(Map.of("name", "mcp-search")), api.mcpToolsCalled()),
                () -> assertEquals(List.of(Map.of("uri", "file://policy")), api.mcpResourcesCalled()),
                () -> assertEquals(List.of(Map.of("name", "prompt")), api.mcpPromptsCalled()));
    }

    @Test
    void singleTurnApiOrderUsesDatasetRankBeforeIndex() {
        var testCase = LlmTestCase.builder("input")
                .datasetRank(2)
                .build();

        var api = ApiTestCases.from(testCase, 7);

        assertEquals(2, api.order());
    }

    @Test
    void singleTurnApiNameFormatsMissingIndexLikeDeepEval() {
        var api = ApiTestCases.from(LlmTestCase.builder("input").build(), null);

        assertEquals("test_case_None", api.name());
    }

    @Test
    void apiConversionUsesNullForEmptyRetrievalContextLikeDeepEval() {
        var singleTurn = LlmTestCase.builder("input")
                .retrievalContext(List.of())
                .build();
        var conversational = ConversationalTestCase.builder(List.of(Turn.builder("user", "hello")
                        .retrievalContext(List.of())
                        .build()))
                .build();

        assertAll(
                () -> assertNull(ApiTestCases.from(singleTurn, 1).retrievalContext()),
                () -> assertNull(ApiTestCases.from(conversational, 1).turns().getFirst().retrievalContext()));
    }

    @Test
    void llmApiTestCasePreservesNullMcpValuesLikePythonDicts() {
        var server = new HashMap<String, Object>();
        server.put("name", "weather");
        server.put("description", null);
        var testCase = LlmTestCase.builder("input")
                .mcpServers(List.of(server))
                .build();

        var api = assertDoesNotThrow(() -> ApiTestCases.from(testCase, 1));

        assertAll(
                () -> assertTrue(api.mcpServers().getFirst().containsKey("description")),
                () -> assertNull(api.mcpServers().getFirst().get("description")));
    }

    @Test
    void llmApiTestCasePreservesNullMetadataValuesLikePythonDicts() {
        var metadata = new HashMap<String, Object>();
        metadata.put("source", null);
        var testCase = LlmTestCase.builder("input")
                .metadata(metadata)
                .build();

        var api = assertDoesNotThrow(() -> ApiTestCases.from(testCase, 1));

        assertAll(
                () -> assertTrue(api.metadata().containsKey("source")),
                () -> assertNull(api.metadata().get("source")));
    }

    @Test
    void llmApiTestCaseModelDumpUsesDeepEvalAliasesAndMetadata() {
        var metadata = Map.<String, Object>of("source", "test");
        var api = ApiTestCases.from(LlmTestCase.builder("input")
                .actualOutput("actual")
                .metadata(metadata)
                .trace(Map.of("name", "root"))
                .build(), 1);

        var dump = api.modelDump(true);

        assertAll(
                () -> assertEquals("actual", dump.get("actualOutput")),
                () -> assertEquals(metadata, dump.get("metadata")),
                () -> assertEquals(Map.of("name", "root"), dump.get("trace")),
                () -> assertTrue(dump.containsKey("metricsData")),
                () -> assertFalse(dump.containsKey("actual_output")));
    }

    @Test
    void apiModelDumpSerializesNestedToolCallsWithAliases() {
        var api = ApiTestCases.from(LlmTestCase.builder("input")
                .toolsCalled(List.of(new ToolCall("Search", Map.of("query", "refund"), "ok")))
                .build(), 1);

        var dump = api.modelDump(true);
        var tool = ((List<Map<String, Object>>) dump.get("toolsCalled")).getFirst();

        assertAll(
                () -> assertEquals("Search", tool.get("name")),
                () -> assertEquals(Map.of("query", "refund"), tool.get("inputParameters")),
                () -> assertFalse(tool.containsKey("input_parameters")));
    }

    @Test
    void createsConversationalApiTestCaseWithOrderedTurns() {
        var tool = new ToolCall("search");
        var testCase = ConversationalTestCase.builder(List.of(
                        Turn.builder("user", "Find flights")
                                .userId("u1")
                                .retrievalContext(List.of(new RetrievedContextData("retrieved", "source")))
                                .toolsCalled(List.of(tool))
                                .mcpToolsCalled(List.of(Map.of("name", "mcp-search")))
                                .mcpResourcesCalled(List.of(Map.of("uri", "file://policy")))
                                .mcpPromptsCalled(List.of(Map.of("name", "prompt")))
                                .build()))
                .scenario("Book a flight")
                .expectedOutcome("User gets options")
                .userDescription("Traveler")
                .context(List.of("travel"))
                .metadata(Map.of("k", "v"))
                .comments("comment")
                .tags(List.of("tag"))
                .name("conversation")
                .mcpServers(List.of(Map.of("server_name", "policy")))
                .build();

        var api = ApiTestCases.from(testCase, 3);
        var turn = api.turns().getFirst();

        assertAll(
                () -> assertEquals("conversation", api.name()),
                () -> assertTrue(api.success()),
                () -> assertEquals(List.of(), api.metricsData()),
                () -> assertEquals(0.0, api.runDuration()),
                () -> assertNull(api.evaluationCost()),
                () -> assertEquals(3, api.order()),
                () -> assertEquals("Book a flight", api.scenario()),
                () -> assertEquals("User gets options", api.expectedOutcome()),
                () -> assertEquals("Traveler", api.userDescription()),
                () -> assertEquals(List.of("travel"), api.context()),
                () -> assertEquals(Map.of("k", "v"), api.metadata()),
                () -> assertEquals("comment", api.comments()),
                () -> assertEquals(List.of("tag"), api.tags()),
                () -> assertEquals(List.of(Map.of("server_name", "policy")), api.mcpServers()),
                () -> assertEquals("user", turn.role()),
                () -> assertEquals("Find flights", turn.content()),
                () -> assertEquals("u1", turn.userId()),
                () -> assertEquals(List.of("retrieved"), turn.retrievalContext()),
                () -> assertEquals(List.of(tool), turn.toolsCalled()),
                () -> assertEquals(List.of(Map.of("name", "mcp-search")), turn.mcpToolsCalled()),
                () -> assertEquals(List.of(Map.of("uri", "file://policy")), turn.mcpResourcesCalled()),
                () -> assertEquals(List.of(Map.of("name", "prompt")), turn.mcpPromptsCalled()),
                () -> assertEquals(0, turn.order()));
    }

    @Test
    void conversationalApiOrderUsesDatasetRankBeforeIndex() {
        var testCase = ConversationalTestCase.builder(List.of(new Turn("user", "hello")))
                .datasetRank(4)
                .build();

        var api = ApiTestCases.from(testCase, 9);

        assertEquals(4, api.order());
    }

    @Test
    void conversationalApiNameFormatsMissingIndexLikeDeepEval() {
        var api = ApiTestCases.from(ConversationalTestCase.builder(List.of(new Turn("user", "hello"))).build(), null);

        assertEquals("conversational_test_case_None", api.name());
    }

    @Test
    void conversationalApiTestCasePreservesNullMcpValuesLikePythonDicts() {
        var tool = new HashMap<String, Object>();
        tool.put("name", "search");
        tool.put("error", null);
        var server = new HashMap<String, Object>();
        server.put("server_name", "policy");
        server.put("description", null);
        var testCase = ConversationalTestCase.builder(List.of(Turn.builder("assistant", "answer")
                        .mcpToolsCalled(List.of(tool))
                        .build()))
                .mcpServers(List.of(server))
                .build();

        var api = assertDoesNotThrow(() -> ApiTestCases.from(testCase, 1));

        assertAll(
                () -> assertTrue(api.mcpServers().getFirst().containsKey("description")),
                () -> assertNull(api.mcpServers().getFirst().get("description")),
                () -> assertTrue(api.turns().getFirst().mcpToolsCalled().getFirst().containsKey("error")),
                () -> assertNull(api.turns().getFirst().mcpToolsCalled().getFirst().get("error")));
    }

    @Test
    void conversationalApiTestCasePreservesNullMetadataValuesLikePythonDicts() {
        var metadata = new HashMap<String, Object>();
        metadata.put("source", null);
        var testCase = ConversationalTestCase.builder(List.of(new Turn("user", "hello")))
                .metadata(metadata)
                .build();

        var api = assertDoesNotThrow(() -> ApiTestCases.from(testCase, 1));

        assertAll(
                () -> assertTrue(api.metadata().containsKey("source")),
                () -> assertNull(api.metadata().get("source")));
    }

    @Test
    void conversationalApiTestCaseModelDumpUsesDeepEvalAliasesAndMetadata() {
        var metadata = Map.<String, Object>of("source", "test");
        var api = ApiTestCases.from(ConversationalTestCase.builder(List.of(Turn.builder("user", "hello")
                        .userId("u1")
                        .build()))
                .expectedOutcome("done")
                .metadata(metadata)
                .build(), 1);

        var dump = api.modelDump(true);
        var turn = ((List<Map<String, Object>>) dump.get("turns")).getFirst();

        assertAll(
                () -> assertEquals("done", dump.get("expectedOutcome")),
                () -> assertEquals(metadata, dump.get("metadata")),
                () -> assertEquals("u1", turn.get("userId")),
                () -> assertFalse(dump.containsKey("expected_outcome")),
                () -> assertFalse(turn.containsKey("user_id")));
    }
}
