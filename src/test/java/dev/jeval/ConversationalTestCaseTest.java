package dev.jeval;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConversationalTestCaseTest {

    @Test
    void turnCopiesOptionalListsAndMaps() {
        var retrievalContext = new ArrayList<>(List.of("policy"));
        var toolsCalled = new ArrayList<>(List.of(new ToolCall("Search")));
        var mcpToolsCalled = new ArrayList<>(List.of(Map.<String, Object>of("name", "mcp-search")));
        var mcpResourcesCalled = new ArrayList<>(List.of(Map.<String, Object>of("uri", "file://policy")));
        var mcpPromptsCalled = new ArrayList<>(List.of(Map.<String, Object>of("name", "prompt")));
        var metadata = Map.<String, Object>of("source", "unit");

        var turn = Turn.builder("assistant", "answer")
                .retrievalContext(retrievalContext)
                .toolsCalled(toolsCalled)
                .mcpToolsCalled(mcpToolsCalled)
                .mcpResourcesCalled(mcpResourcesCalled)
                .mcpPromptsCalled(mcpPromptsCalled)
                .metadata(metadata)
                .build();
        retrievalContext.add("mutated");
        toolsCalled.add(new ToolCall("Lookup"));
        mcpToolsCalled.add(Map.of("name", "mutated"));
        mcpResourcesCalled.add(Map.of("uri", "mutated"));
        mcpPromptsCalled.add(Map.of("name", "mutated"));

        assertAll(
                () -> assertEquals("assistant", turn.role()),
                () -> assertEquals("answer", turn.content()),
                () -> assertEquals(List.of("policy"), turn.retrievalContext()),
                () -> assertEquals(List.of(new ToolCall("Search")), turn.toolsCalled()),
                () -> assertEquals(List.of(Map.of("name", "mcp-search")), turn.mcpToolsCalled()),
                () -> assertEquals(List.of(Map.of("uri", "file://policy")), turn.mcpResourcesCalled()),
                () -> assertEquals(List.of(Map.of("name", "prompt")), turn.mcpPromptsCalled()),
                () -> assertEquals(metadata, turn.metadata()));
    }

    @Test
    void turnAdditionalMetadataAliasesMetadata() {
        var metadata = Map.<String, Object>of("source", "unit");

        var turn = Turn.builder("assistant", "answer")
                .additionalMetadata(metadata)
                .build();

        assertAll(
                () -> assertEquals(metadata, turn.metadata()),
                () -> assertEquals(metadata, turn.additionalMetadata()));
    }

    @Test
    void turnStringRepresentationMatchesDeepEvalRepr() {
        var minimal = new Turn("user", "Hello");
        var optional = Turn.builder("assistant", "Hi there!")
                .userId("user123")
                .metadata(Map.of("model", "gpt-4"))
                .build();

        assertAll(
                () -> assertEquals("Turn(role='user', content='Hello')", minimal.toString()),
                () -> assertTrue(optional.toString().contains("role='assistant'")),
                () -> assertTrue(optional.toString().contains("content='Hi there!'")),
                () -> assertTrue(optional.toString().contains("user_id='user123'")),
                () -> assertTrue(optional.toString().contains("metadata=")));
    }

    @Test
    void turnStringRepresentationFormatsMetadataLikePythonRepr() {
        var turn = Turn.builder("assistant", "answer")
                .metadata(Map.of("model", "gpt-4"))
                .build();

        assertTrue(turn.toString().contains("metadata={'model': 'gpt-4'}"));
    }

    @Test
    void turnStringRepresentationPreservesMetadataInsertionOrderLikePythonRepr() {
        var metadata = new java.util.LinkedHashMap<String, Object>();
        metadata.put("b", "first");
        metadata.put("a", "second");

        var turn = Turn.builder("assistant", "answer")
                .metadata(metadata)
                .build();

        assertTrue(turn.toString().contains("metadata={'b': 'first', 'a': 'second'}"));
    }

    @Test
    void turnStringRepresentationFormatsRetrievalContextLikePythonRepr() {
        var turn = Turn.builder("assistant", "answer")
                .retrievalContext(List.of("policy", "refund"))
                .build();

        assertTrue(turn.toString().contains("retrieval_context=['policy', 'refund']"));
    }

    @Test
    void turnStringRepresentationFormatsRetrievedContextDataLikePythonRepr() {
        var turn = Turn.builder("assistant", "answer")
                .retrievalContext(List.of(new RetrievedContextData("retrieved text", "source.md")))
                .build();

        assertTrue(turn.toString()
                .contains("retrieval_context=[RetrievedContextData(context='retrieved text', source='source.md')]"));
    }

    @Test
    void turnStringRepresentationEscapesContentLikePythonRepr() {
        assertTrue(new Turn("assistant", "line\nbreak")
                .toString()
                .contains("content='line\\nbreak'"));
    }

    @Test
    void turnStringRepresentationChoosesDoubleQuotesForApostropheContentLikePythonRepr() {
        assertTrue(new Turn("assistant", "can't")
                .toString()
                .contains("content=\"can't\""));
    }

    @Test
    void turnStringRepresentationFormatsMcpToolCallsLikePythonRepr() {
        var turn = Turn.builder("assistant", "answer")
                .mcpToolsCalled(List.of(Map.of("name", "mcp-search")))
                .build();

        assertTrue(turn.toString().contains("mcp_tools_called=[{'name': 'mcp-search'}]"));
    }

    @Test
    void turnStringRepresentationPreservesMcpMapInsertionOrderLikePythonRepr() {
        var tool = new java.util.LinkedHashMap<String, Object>();
        tool.put("z_key", "first");
        tool.put("a_key", "second");

        var turn = Turn.builder("assistant", "answer")
                .mcpToolsCalled(List.of(tool))
                .build();

        assertTrue(turn.toString().contains("mcp_tools_called=[{'z_key': 'first', 'a_key': 'second'}]"));
    }

    @Test
    void turnMetadataPreservesNullValuesLikePythonDicts() {
        var metadata = new HashMap<String, Object>();
        metadata.put("source", null);

        var turn = assertDoesNotThrow(() -> Turn.builder("assistant", "answer")
                .metadata(metadata)
                .build());

        assertAll(
                () -> assertTrue(turn.metadata().containsKey("source")),
                () -> assertNull(turn.metadata().get("source")));
    }

    @Test
    void turnModelDumpMatchesDeepEvalAndCanExcludeNulls() {
        var turn = Turn.builder("assistant", "Response")
                .userId("user123")
                .retrievalContext(List.of("context1", "context2"))
                .toolsCalled(List.of(new ToolCall("test_tool", "Test", "Testing", null, Map.of())))
                .metadata(Map.of("key", "value"))
                .build();

        var dump = turn.modelDump();
        var compact = new Turn("user", "Hello").modelDump(true);

        assertAll(
                () -> assertEquals("assistant", dump.get("role")),
                () -> assertEquals("Response", dump.get("content")),
                () -> assertEquals("user123", dump.get("user_id")),
                () -> assertEquals(List.of("context1", "context2"), dump.get("retrieval_context")),
                () -> assertTrue(dump.containsKey("tools_called")),
                () -> assertEquals(Map.of("key", "value"), dump.get("metadata")),
                () -> assertFalse(compact.containsKey("user_id")),
                () -> assertFalse(compact.containsKey("retrieval_context")),
                () -> assertFalse(compact.containsKey("tools_called")),
                () -> assertFalse(compact.containsKey("metadata")));
    }

    @Test
    void turnMcpFieldsDefaultToNull() {
        var turn = new Turn("user", "hello");

        assertAll(
                () -> assertNull(turn.mcpToolsCalled()),
                () -> assertNull(turn.mcpResourcesCalled()),
                () -> assertNull(turn.mcpPromptsCalled()));
    }

    @Test
    void turnRetrievalContextAcceptsRetrievedContextData() {
        var turn = Turn.builder("user", "hello")
                .retrievalContext(List.of(new RetrievedContextData("retrieved text", "source.md")))
                .build();
        Object retrieved = turn.retrievalContext().getFirst();

        assertAll(
                () -> assertTrue(retrieved instanceof RetrievedContextData),
                () -> assertEquals(new RetrievedContextData("retrieved text", "source.md"), retrieved));
    }

    @Test
    void turnRetrievalContextRejectsUnsupportedTypes() {
        var builder = Turn.builder("user", "hello").retrievalContext(List.of(42));

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void turnRetrievalContextRejectsUnsupportedTypesThroughRecordConstructor() {
        assertThrows(IllegalArgumentException.class, () -> new Turn(
                "user", "hello", null, (List) List.of(42), null, null, null, null, null));
    }

    @Test
    void turnToolsCalledRejectsNullEntries() {
        var toolsCalled = new ArrayList<ToolCall>();
        toolsCalled.add(null);
        var builder = Turn.builder("assistant", "hello").toolsCalled(toolsCalled);

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void turnToolsCalledRejectsNonToolCallEntries() {
        var builder = Turn.builder("assistant", "hello")
                .toolsCalled((List) List.of(new ToolCall("Search"), 123));

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void turnMcpToolsCalledRejectsNonMapEntries() {
        var builder = Turn.builder("assistant", "hello")
                .mcpToolsCalled((List) List.of(Map.of("name", "tool"), 123));

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void turnMcpMapsPreserveNullValuesLikePythonDicts() {
        var tool = new HashMap<String, Object>();
        tool.put("name", "search");
        tool.put("error", null);

        var turn = assertDoesNotThrow(() -> Turn.builder("assistant", "hello")
                .mcpToolsCalled(List.of(tool))
                .build());

        assertAll(
                () -> assertTrue(turn.mcpToolsCalled().getFirst().containsKey("error")),
                () -> assertNull(turn.mcpToolsCalled().getFirst().get("error")));
    }

    @Test
    void conversationalTestCaseRequiresTurns() {
        assertThrows(IllegalArgumentException.class, () -> ConversationalTestCase.builder(List.of()).build());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void conversationalTestCaseRejectsNonTurnEntries() {
        assertThrows(IllegalArgumentException.class,
                () -> ConversationalTestCase.builder((List) List.of("not a turn")).build());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void conversationalTestCaseAcceptsMapTurnsLikeDeepEval() {
        var mcpTool = new java.util.LinkedHashMap<String, Object>();
        mcpTool.put("name", "mcp-search");
        var mcpResource = new java.util.LinkedHashMap<String, Object>();
        mcpResource.put("uri", "file://policy");
        var mcpPrompt = new java.util.LinkedHashMap<String, Object>();
        mcpPrompt.put("name", "prompt");
        var turn = new java.util.LinkedHashMap<String, Object>();
        turn.put("role", "user");
        turn.put("content", "hi");
        turn.put("user_id", "u1");
        turn.put("retrieval_context", List.of("ctx"));
        turn.put("tools_called", List.of(Map.of("name", "Search", "input_parameters",
                Map.of("query", "refund"), "output", "30 days")));
        turn.put("mcp_tools_called", List.of(mcpTool));
        turn.put("mcp_resources_called", List.of(mcpResource));
        turn.put("mcp_prompts_called", List.of(mcpPrompt));
        turn.put("metadata", Map.of("source", "json"));

        var testCase = ConversationalTestCase.builder((List) List.of(turn))
                .build();

        assertEquals(Turn.builder("user", "hi")
                .userId("u1")
                .retrievalContext(List.of("ctx"))
                .toolsCalled(List.of(new ToolCall("Search", Map.of("query", "refund"), "30 days")))
                .mcpToolsCalled(List.of(mcpTool))
                .mcpResourcesCalled(List.of(mcpResource))
                .mcpPromptsCalled(List.of(mcpPrompt))
                .metadata(Map.of("source", "json"))
                .build(), testCase.turns().getFirst());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void mapTurnIgnoresCamelCaseMcpFieldsLikeDeepEval() {
        var turn = new java.util.LinkedHashMap<String, Object>();
        turn.put("role", "assistant");
        turn.put("content", "hi");
        turn.put("mcpToolsCalled", List.of(Map.of("name", "mcp-search")));
        turn.put("mcpResourcesCalled", List.of(Map.of("uri", "file://policy")));
        turn.put("mcpPromptsCalled", List.of(Map.of("name", "prompt")));

        var testCase = ConversationalTestCase.builder((List) List.of(turn))
                .build();

        assertAll(
                () -> assertNull(testCase.turns().getFirst().mcpToolsCalled()),
                () -> assertNull(testCase.turns().getFirst().mcpResourcesCalled()),
                () -> assertNull(testCase.turns().getFirst().mcpPromptsCalled()));
    }

    @Test
    void optionalFieldsDefaultToNullAndMultimodalFalse() {
        var testCase = ConversationalTestCase.builder(List.of(new Turn("user", "hello"))).build();

        assertAll(
                () -> assertNull(testCase.scenario()),
                () -> assertNull(testCase.expectedOutcome()),
                () -> assertNull(testCase.chatbotRole()),
                () -> assertNull(testCase.mcpServers()),
                () -> assertFalse(testCase.multimodal()));
    }

    @Test
    void conversationalTestCaseCopiesTurnsAndContext() {
        var turns = new ArrayList<>(List.of(new Turn("user", "hello")));
        var context = new ArrayList<>(List.of("shared context"));

        var testCase = ConversationalTestCase.builder(turns)
                .context(context)
                .expectedOutcome("answer politely")
                .chatbotRole("helpful assistant")
                .build();
        turns.add(new Turn("assistant", "mutated"));
        context.add("mutated");

        assertAll(
                () -> assertEquals(1, testCase.turns().size()),
                () -> assertEquals(List.of("shared context"), testCase.context()),
                () -> assertEquals("answer politely", testCase.expectedOutcome()),
                () -> assertEquals("helpful assistant", testCase.chatbotRole()));
    }

    @Test
    void conversationalTestCaseContextRejectsNullEntries() {
        var context = new ArrayList<String>();
        context.add(null);
        var builder = ConversationalTestCase.builder(List.of(new Turn("user", "hello"))).context(context);

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void conversationalTestCaseContextRejectsNonStringEntries() {
        var builder = ConversationalTestCase.builder(List.of(new Turn("user", "hello")))
                .context((List) List.of("valid", 123));

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void conversationalTestCaseContextAcceptsRetrievedContextData() {
        var builder = ConversationalTestCase.builder(List.of(new Turn("user", "hello")))
                .context((List) List.of(new RetrievedContextData("shared context", "source.md")));

        var testCase = builder.build();

        assertEquals(List.of("shared context"), testCase.context());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void conversationalTestCaseTagsRejectNonStringEntries() {
        var builder = ConversationalTestCase.builder(List.of(new Turn("user", "hello")))
                .tags((List) List.of("smoke", 123));

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void conversationalTestCaseAdditionalMetadataAliasesMetadata() {
        var metadata = Map.<String, Object>of("priority", "high");

        var testCase = ConversationalTestCase.builder(List.of(new Turn("user", "hello")))
                .additionalMetadata(metadata)
                .build();

        assertAll(
                () -> assertEquals(metadata, testCase.metadata()),
                () -> assertEquals(metadata, testCase.additionalMetadata()));
    }

    @Test
    void conversationalTestCaseMetadataPreservesNullValuesLikePythonDicts() {
        var metadata = new HashMap<String, Object>();
        metadata.put("priority", null);

        var testCase = assertDoesNotThrow(() -> ConversationalTestCase.builder(List.of(new Turn("user", "hello")))
                .metadata(metadata)
                .build());

        assertAll(
                () -> assertTrue(testCase.metadata().containsKey("priority")),
                () -> assertNull(testCase.metadata().get("priority")));
    }

    @Test
    void conversationalTestCaseModelDumpUsesDeepEvalAliasesAndHidesPrivateFields() {
        var metadata = Map.<String, Object>of("key", "value");
        var testCase = ConversationalTestCase.builder(List.of(
                        new Turn("user", "Hello"),
                        new Turn("assistant", "Hi!")))
                .scenario("Test scenario")
                .name("Test name")
                .userDescription("Test user")
                .expectedOutcome("Success")
                .chatbotRole("Assistant")
                .metadata(metadata)
                .tags(List.of("tag1", "tag2"))
                .mcpServers(List.of(Map.of("server_name", "policy")))
                .datasetRank(1)
                .build();

        var dump = testCase.modelDump(true);

        assertAll(
                () -> assertTrue(dump.containsKey("turns")),
                () -> assertEquals(2, ((List<?>) dump.get("turns")).size()),
                () -> assertEquals("Test scenario", dump.get("scenario")),
                () -> assertEquals("Test name", dump.get("name")),
                () -> assertEquals("Test user", dump.get("userDescription")),
                () -> assertEquals("Success", dump.get("expectedOutcome")),
                () -> assertEquals("Assistant", dump.get("chatbotRole")),
                () -> assertEquals(metadata, dump.get("metadata")),
                () -> assertEquals(List.of("tag1", "tag2"), dump.get("tags")),
                () -> assertEquals(List.of(Map.of("server_name", "policy")), dump.get("mcp_servers")),
                () -> assertEquals(false, dump.get("multimodal")),
                () -> assertFalse(dump.containsKey("mcpServers")),
                () -> assertFalse(dump.containsKey("additionalMetadata")),
                () -> assertFalse(dump.containsKey("datasetRank")),
                () -> assertFalse(dump.containsKey("datasetAlias")),
                () -> assertFalse(dump.containsKey("datasetId")));
    }

    @Test
    void conversationalTestCaseModelDumpByAliasPropagatesToNestedTurns() {
        var turn = Turn.builder("assistant", "Hi")
                .toolsCalled(List.of(new ToolCall("Search", Map.of("query", "refund"), "ok")))
                .build();
        var testCase = ConversationalTestCase.builder(List.of(turn)).build();

        var dump = testCase.modelDump(true);
        var turns = (List<?>) dump.get("turns");
        var firstTurn = (Map<?, ?>) turns.getFirst();
        var tools = (List<?>) firstTurn.get("tools_called");
        var tool = (Map<?, ?>) tools.getFirst();

        assertAll(
                () -> assertEquals(Map.of("query", "refund"), tool.get("inputParameters")),
                () -> assertFalse(tool.containsKey("input_parameters")));
    }

    @Test
    void conversationalTestCaseSupportsDatasetPrivateMetadata() {
        var testCase = ConversationalTestCase.builder(List.of(new Turn("user", "hello")))
                .datasetRank(1)
                .datasetAlias("test_alias")
                .datasetId("test_id")
                .build();

        assertAll(
                () -> assertEquals(1, testCase.datasetRank()),
                () -> assertEquals("test_alias", testCase.datasetAlias()),
                () -> assertEquals("test_id", testCase.datasetId()));
    }

    @Test
    void conversationalTestCaseCopiesMcpServers() {
        var mcpServers = new ArrayList<>(List.of(Map.<String, Object>of("server_name", "policy")));

        var testCase = ConversationalTestCase.builder(List.of(new Turn("user", "hello")))
                .mcpServers(mcpServers)
                .build();
        mcpServers.add(Map.of("server_name", "mutated"));

        assertEquals(List.of(Map.of("server_name", "policy")), testCase.mcpServers());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void conversationalTestCaseMcpServersRejectNonMapEntries() {
        var builder = ConversationalTestCase.builder(List.of(new Turn("user", "hello")))
                .mcpServers((List) List.of(Map.of("server_name", "policy"), 123));

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void conversationalTestCaseMcpServersPreserveNullValuesLikePythonDicts() {
        var server = new HashMap<String, Object>();
        server.put("server_name", "policy");
        server.put("description", null);

        var testCase = assertDoesNotThrow(() -> ConversationalTestCase.builder(List.of(new Turn("user", "hello")))
                .mcpServers(List.of(server))
                .build());

        assertAll(
                () -> assertTrue(testCase.mcpServers().getFirst().containsKey("description")),
                () -> assertNull(testCase.mcpServers().getFirst().get("description")));
    }

    @Test
    void autoDetectsMultimodalPlaceholdersInConversationFields() {
        var imagePlaceholder = "[DEEPEVAL:IMAGE:image-id]";
        var testCase = ConversationalTestCase.builder(List.of(
                        Turn.builder("user", "What is shown? " + imagePlaceholder)
                                .retrievalContext(List.of("image context"))
                                .build()))
                .expectedOutcome("Describe " + imagePlaceholder)
                .build();

        assertTrue(testCase.multimodal());
    }

    @Test
    void contextPlaceholderDoesNotSetMultimodalFlagLikeDeepEval() {
        var image = new MllmImage("YWJj", "image/png");
        var testCase = ConversationalTestCase.builder(List.of(new Turn("user", "hello")))
                .context(List.of("Context " + image))
                .build();

        assertAll(
                () -> assertFalse(testCase.multimodal()),
                () -> assertEquals(1, testCase.imagesMapping().size()),
                () -> assertTrue(testCase.imagesMapping().containsValue(image)));
    }

    @Test
    void imagesMappingReturnsRegisteredImagesFromTurns() {
        var image = new MllmImage("YWJj", "image/png");
        var testCase = ConversationalTestCase.builder(List.of(new Turn("user", "What is shown? " + image))).build();

        var mapping = testCase.imagesMapping();

        assertAll(
                () -> assertEquals(1, mapping.size()),
                () -> assertTrue(mapping.containsValue(image)));
    }

    @Test
    void imagesMappingReturnsNullWhenNoRegisteredImagesExist() {
        var testCase = ConversationalTestCase.builder(List.of(new Turn("user", "[DEEPEVAL:IMAGE:missing-id]"))).build();

        assertNull(testCase.imagesMapping());
    }
}
