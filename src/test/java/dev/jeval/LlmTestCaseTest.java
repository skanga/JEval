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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LlmTestCaseTest {

    @Test
    void optionalFieldsDefaultToNullLikeDeepEval() {
        var testCase = new LlmTestCase("input", "actual", "expected");

        assertAll(
                () -> assertNull(testCase.context()),
                () -> assertNull(testCase.retrievalContext()),
                () -> assertNull(testCase.additionalMetadata()),
                () -> assertNull(testCase.toolsCalled()),
                () -> assertNull(testCase.comments()),
                () -> assertNull(testCase.expectedTools()),
                () -> assertNull(testCase.tokenCost()),
                () -> assertNull(testCase.completionTime()),
                () -> assertNull(testCase.name()),
                () -> assertNull(testCase.tags()),
                () -> assertNull(testCase.customColumnKeyValues()),
                () -> assertNull(testCase.mcpServers()),
                () -> assertNull(testCase.mcpToolsCalled()),
                () -> assertNull(testCase.mcpResourcesCalled()),
                () -> assertNull(testCase.mcpPromptsCalled()),
                () -> assertFalse(testCase.multimodal()));
    }

    @Test
    void builderSupportsOptionalDeepEvalFields() {
        var context = new ArrayList<>(List.of("refund policy"));
        var tools = new ArrayList<>(List.of(new ToolCall("ImageAnalysis")));
        var metadata = new HashMap<String, Object>(Map.of("source", "unit", "version", 1.0));
        var tags = new ArrayList<>(List.of("refund", "tool"));
        var mcpServers = new ArrayList<>(List.of(Map.<String, Object>of("name", "weather")));
        var mcpToolsCalled = new ArrayList<>(List.of(Map.<String, Object>of("name", "mcp-search")));
        var mcpResourcesCalled = new ArrayList<>(List.of(Map.<String, Object>of("uri", "file://policy")));
        var mcpPromptsCalled = new ArrayList<>(List.of(Map.<String, Object>of("name", "prompt")));
        var trace = new HashMap<String, Object>(Map.of("name", "agent", "input", "book a flight"));

        var testCase = LlmTestCase.builder("input")
                .actualOutput("actual")
                .expectedOutput("expected")
                .context(context)
                .retrievalContext(List.of("retrieved policy"))
                .additionalMetadata(metadata)
                .toolsCalled(tools)
                .comments("comment")
                .expectedTools(List.of(new ToolCall("ImageAnalysis")))
                .tokenCost(0.05)
                .completionTime(1.25)
                .name("refund test")
                .tags(tags)
                .customColumnKeyValues(Map.of("case_id", "refund-1"))
                .mcpServers(mcpServers)
                .mcpToolsCalled(mcpToolsCalled)
                .mcpResourcesCalled(mcpResourcesCalled)
                .mcpPromptsCalled(mcpPromptsCalled)
                .trace(trace)
                .build();

        context.add("mutated");
        tools.add(new ToolCall("Search"));
        metadata.put("source", "mutated");
        tags.add("mutated");
        mcpServers.add(Map.of("name", "mutated"));
        mcpToolsCalled.add(Map.of("name", "mutated"));
        mcpResourcesCalled.add(Map.of("uri", "mutated"));
        mcpPromptsCalled.add(Map.of("name", "mutated"));
        trace.put("name", "mutated");

        assertAll(
                () -> assertEquals(List.of("refund policy"), testCase.context()),
                () -> assertEquals(List.of("retrieved policy"), testCase.retrievalContext()),
                () -> assertEquals(Map.of("source", "unit", "version", 1.0), testCase.additionalMetadata()),
                () -> assertEquals(List.of(new ToolCall("ImageAnalysis")), testCase.toolsCalled()),
                () -> assertEquals("comment", testCase.comments()),
                () -> assertEquals(List.of(new ToolCall("ImageAnalysis")), testCase.expectedTools()),
                () -> assertEquals(0.05, testCase.tokenCost()),
                () -> assertEquals(1.25, testCase.completionTime()),
                () -> assertEquals("refund test", testCase.name()),
                () -> assertEquals(List.of("refund", "tool"), testCase.tags()),
                () -> assertEquals(Map.of("case_id", "refund-1"), testCase.customColumnKeyValues()),
                () -> assertEquals(List.of(Map.of("name", "weather")), testCase.mcpServers()),
                () -> assertEquals(List.of(Map.of("name", "mcp-search")), testCase.mcpToolsCalled()),
                () -> assertEquals(List.of(Map.of("uri", "file://policy")), testCase.mcpResourcesCalled()),
                () -> assertEquals(List.of(Map.of("name", "prompt")), testCase.mcpPromptsCalled()),
                () -> assertEquals(Map.of("name", "agent", "input", "book a flight"), testCase.trace()));
    }

    @Test
    void metadataAliasesAdditionalMetadata() {
        var metadata = Map.<String, Object>of("source", "unit");

        var testCase = LlmTestCase.builder("input")
                .metadata(metadata)
                .build();

        assertAll(
                () -> assertEquals(metadata, testCase.metadata()),
                () -> assertEquals(metadata, testCase.additionalMetadata()));
    }

    @Test
    void metadataAndTracePreserveNullValuesLikePythonDicts() {
        var metadata = new HashMap<String, Object>();
        metadata.put("source", null);
        var trace = new HashMap<String, Object>();
        trace.put("input", null);

        var testCase = assertDoesNotThrow(() -> LlmTestCase.builder("input")
                .metadata(metadata)
                .trace(trace)
                .build());

        assertAll(
                () -> assertTrue(testCase.metadata().containsKey("source")),
                () -> assertNull(testCase.metadata().get("source")),
                () -> assertTrue(testCase.trace().containsKey("input")),
                () -> assertNull(testCase.trace().get("input")));
    }

    @Test
    void modelDumpByAliasUsesDeepEvalSerializationAliasesAndHidesPrivateFields() {
        var parameters = new LinkedHashMap<String, Object>();
        parameters.put("query", "refund");
        var metadata = Map.<String, Object>of("source", "unit");

        var testCase = LlmTestCase.builder("input")
                .actualOutput("actual")
                .expectedOutput("expected")
                .retrievalContext(List.of("retrieved"))
                .metadata(metadata)
                .toolsCalled(List.of(new ToolCall("Search", parameters, "ok")))
                .expectedTools(List.of(new ToolCall("Lookup")))
                .tokenCost(0.05)
                .completionTime(1.25)
                .mcpServers(List.of(Map.of("server_name", "policy")))
                .trace(Map.of("hidden", true))
                .datasetRank(1)
                .build();

        var dump = testCase.modelDump(true);

        assertAll(
                () -> assertEquals("actual", dump.get("actualOutput")),
                () -> assertEquals("expected", dump.get("expectedOutput")),
                () -> assertEquals(List.of("retrieved"), dump.get("retrievalContext")),
                () -> assertEquals(metadata, dump.get("metadata")),
                () -> assertEquals(0.05, dump.get("tokenCost")),
                () -> assertEquals(1.25, dump.get("completionTime")),
                () -> assertEquals(List.of(Map.of("server_name", "policy")), dump.get("mcp_servers")),
                () -> assertTrue(dump.containsKey("toolsCalled")),
                () -> assertTrue(dump.containsKey("expectedTools")),
                () -> assertFalse(dump.containsKey("mcpServers")),
                () -> assertFalse(dump.containsKey("additionalMetadata")),
                () -> assertFalse(dump.containsKey("trace")),
                () -> assertFalse(dump.containsKey("datasetRank")),
                () -> assertFalse(dump.containsKey("identifier")));
    }

    @Test
    void modelDumpSerializesRetrievedContextDataLikeDeepEval() {
        var testCase = LlmTestCase.builder("input")
                .retrievalContext(List.of(new RetrievedContextData("policy text", "policy.md")))
                .build();

        var dump = testCase.modelDump(true);

        assertEquals(List.of("policy.md: policy text"), dump.get("retrievalContext"));
    }

    @Test
    void builderRequiresInput() {
        assertThrows(IllegalArgumentException.class, () -> LlmTestCase.builder(null));
    }

    @Test
    void identifierDefaultsToUniqueUuid() {
        var first = LlmTestCase.builder("first").build();
        var second = LlmTestCase.builder("second").build();

        assertAll(
                () -> UUID.fromString(first.identifier()),
                () -> UUID.fromString(second.identifier()),
                () -> assertFalse(first.identifier().equals(second.identifier())));
    }

    @Test
    void builderSupportsDatasetPrivateMetadata() {
        var testCase = LlmTestCase.builder("input")
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
    void rejectsInvalidNumericMetadata() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> LlmTestCase.builder("input").tokenCost(-0.01).build()),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> LlmTestCase.builder("input").tokenCost(Double.NaN).build()),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> LlmTestCase.builder("input").completionTime(-0.01).build()),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> LlmTestCase.builder("input").completionTime(Double.POSITIVE_INFINITY).build()),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> LlmTestCase.builder("input").datasetRank(-1).build()));
    }

    @Test
    void autoDetectsMultimodalPlaceholdersInTextFields() {
        var testCase = LlmTestCase.builder("What is shown? [DEEPEVAL:IMAGE:image-id]")
                .actualOutput("A car")
                .expectedOutput("A car")
                .build();

        assertTrue(testCase.multimodal());
    }

    @Test
    void autoDetectsMultimodalPlaceholdersInContextFields() {
        var testCase = LlmTestCase.builder("What is shown?")
                .actualOutput("A car")
                .context(List.of("Context [DEEPEVAL:PDF:doc-id]"))
                .build();

        assertTrue(testCase.multimodal());
    }

    @Test
    void retrievalContextAcceptsRetrievedContextData() {
        var testCase = LlmTestCase.builder("What is shown?")
                .retrievalContext(List.of(new RetrievedContextData("[DEEPEVAL:IMAGE:image-id]", "retriever")))
                .build();
        Object retrieved = testCase.retrievalContext().getFirst();

        assertAll(
                () -> assertTrue(retrieved instanceof RetrievedContextData),
                () -> assertEquals(new RetrievedContextData("[DEEPEVAL:IMAGE:image-id]", "retriever"), retrieved),
                () -> assertTrue(testCase.multimodal()));
    }

    @Test
    void retrievalContextRejectsUnsupportedTypes() {
        var builder = LlmTestCase.builder("input").retrievalContext(List.of(42));

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void retrievalContextRejectsUnsupportedTypesThroughRecordConstructor() {
        assertThrows(IllegalArgumentException.class, () -> new LlmTestCase(
                "input", null, null, null, (List) List.of(42), null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, false));
    }

    @Test
    void contextRejectsNullEntries() {
        var context = new ArrayList<String>();
        context.add(null);
        var builder = LlmTestCase.builder("input").context(context);

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void contextRejectsNonStringEntries() {
        var builder = LlmTestCase.builder("input").context((List) List.of("valid", 123));

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void tagsRejectNonStringEntries() {
        var builder = LlmTestCase.builder("input").tags((List) List.of("smoke", 123));

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void toolsCalledRejectNonToolCallEntries() {
        var builder = LlmTestCase.builder("input").toolsCalled((List) List.of(new ToolCall("Search"), 123));

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void expectedToolsRejectNonToolCallEntries() {
        var builder = LlmTestCase.builder("input").expectedTools((List) List.of(new ToolCall("Search"), 123));

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void mcpServersRejectNonMapEntries() {
        var builder = LlmTestCase.builder("input").mcpServers((List) List.of(Map.of("name", "server"), 123));

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void mcpMapsPreserveNullValuesLikePythonDicts() {
        var server = new HashMap<String, Object>();
        server.put("name", "weather");
        server.put("description", null);

        var testCase = assertDoesNotThrow(() -> LlmTestCase.builder("input")
                .mcpServers(List.of(server))
                .build());

        assertAll(
                () -> assertTrue(testCase.mcpServers().getFirst().containsKey("description")),
                () -> assertNull(testCase.mcpServers().getFirst().get("description")));
    }

    @Test
    void customColumnKeyValuesRejectsNullValues() {
        var customColumns = new HashMap<String, String>();
        customColumns.put("case_id", null);
        var builder = LlmTestCase.builder("input").customColumnKeyValues(customColumns);

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void customColumnKeyValuesRejectsNonStringValues() {
        var builder = LlmTestCase.builder("input")
                .customColumnKeyValues((Map) Map.of("case_id", 123));

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void imagesMappingReturnsRegisteredImagesFromPlaceholders() {
        var image = new MllmImage("YWJj", "image/png");
        var testCase = LlmTestCase.builder("What is shown? " + image).build();

        var mapping = testCase.imagesMapping();

        assertAll(
                () -> assertEquals(1, mapping.size()),
                () -> assertTrue(mapping.containsValue(image)));
    }

    @Test
    void imagesMappingReturnsNullWhenNoRegisteredImagesExist() {
        var testCase = LlmTestCase.builder("What is shown? [DEEPEVAL:IMAGE:missing-id]").build();

        assertNull(testCase.imagesMapping());
    }
}
