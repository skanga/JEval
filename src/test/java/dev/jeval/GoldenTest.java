package dev.jeval;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GoldenTest {

    @Test
    void goldenCopiesOptionalCollections() {
        var context = new ArrayList<>(List.of("policy"));
        var tools = new ArrayList<>(List.of(new ToolCall("Search")));

        var golden = Golden.builder("input")
                .actualOutput("actual")
                .expectedOutput("expected")
                .context(context)
                .retrievalContext(List.of("retrieved"))
                .toolsCalled(tools)
                .expectedTools(List.of(new ToolCall("Search")))
                .additionalMetadata(Map.of("source", "unit"))
                .build();
        context.add("mutated");
        tools.add(new ToolCall("Lookup"));

        assertAll(
                () -> assertEquals(List.of("policy"), golden.context()),
                () -> assertEquals(List.of("retrieved"), golden.retrievalContext()),
                () -> assertEquals(List.of(new ToolCall("Search")), golden.toolsCalled()),
                () -> assertEquals(List.of(new ToolCall("Search")), golden.expectedTools()),
                () -> assertFalse(golden.multimodal()));
    }

    @Test
    void goldenMetadataAliasesAdditionalMetadata() {
        var metadata = Map.<String, Object>of("source", "unit");

        var golden = Golden.builder("input")
                .metadata(metadata)
                .build();

        assertAll(
                () -> assertEquals(metadata, golden.additionalMetadata()),
                () -> assertEquals(metadata, golden.metadata()));
    }

    @Test
    void goldenMetadataPreservesNullValuesLikePythonDicts() {
        var metadata = new HashMap<String, Object>();
        metadata.put("source", null);

        var golden = assertDoesNotThrow(() -> Golden.builder("input")
                .metadata(metadata)
                .build());

        assertAll(
                () -> assertTrue(golden.metadata().containsKey("source")),
                () -> assertEquals(null, golden.metadata().get("source")));
    }

    @Test
    void conversationalGoldenMetadataAliasesAdditionalMetadata() {
        var metadata = Map.<String, Object>of("source", "unit");

        var golden = ConversationalGolden.builder("scenario")
                .metadata(metadata)
                .build();

        assertAll(
                () -> assertEquals(metadata, golden.additionalMetadata()),
                () -> assertEquals(metadata, golden.metadata()));
    }

    @Test
    void conversationalGoldenMetadataPreservesNullValuesLikePythonDicts() {
        var metadata = new HashMap<String, Object>();
        metadata.put("source", null);

        var golden = assertDoesNotThrow(() -> ConversationalGolden.builder("scenario")
                .metadata(metadata)
                .build());

        assertAll(
                () -> assertTrue(golden.metadata().containsKey("source")),
                () -> assertEquals(null, golden.metadata().get("source")));
    }

    @Test
    void goldensSupportDatasetPrivateMetadata() {
        var golden = Golden.builder("input")
                .datasetRank(1)
                .datasetAlias("single_alias")
                .datasetId("single_id")
                .build();
        var conversationalGolden = ConversationalGolden.builder("scenario")
                .datasetRank(2)
                .datasetAlias("multi_alias")
                .datasetId("multi_id")
                .build();

        assertAll(
                () -> assertEquals(1, golden.datasetRank()),
                () -> assertEquals("single_alias", golden.datasetAlias()),
                () -> assertEquals("single_id", golden.datasetId()),
                () -> assertEquals(2, conversationalGolden.datasetRank()),
                () -> assertEquals("multi_alias", conversationalGolden.datasetAlias()),
                () -> assertEquals("multi_id", conversationalGolden.datasetId()));
    }

    @Test
    void goldenModelDumpByAliasUsesDeepEvalSerializationAliases() {
        var golden = Golden.builder("input")
                .actualOutput("actual")
                .expectedOutput("expected")
                .retrievalContext(List.of("retrieved"))
                .additionalMetadata(Map.of("source", "unit"))
                .toolsCalled(List.of(new ToolCall("Search", Map.of("query", "refund"), "ok")))
                .expectedTools(List.of(new ToolCall("Lookup")))
                .sourceFile("source.txt")
                .customColumnKeyValues(Map.of("case_id", "case-1"))
                .build();

        var dump = golden.modelDump(true);
        var tool = ((List<Map<String, Object>>) dump.get("toolsCalled")).getFirst();

        assertAll(
                () -> assertEquals("actual", dump.get("actualOutput")),
                () -> assertEquals("expected", dump.get("expectedOutput")),
                () -> assertEquals(List.of("retrieved"), dump.get("retrievalContext")),
                () -> assertEquals(Map.of("source", "unit"), dump.get("additionalMetadata")),
                () -> assertEquals("source.txt", dump.get("sourceFile")),
                () -> assertEquals(Map.of("case_id", "case-1"), dump.get("customColumnKeyValues")),
                () -> assertEquals(Map.of("query", "refund"), tool.get("inputParameters")),
                () -> assertFalse(dump.containsKey("actual_output")),
                () -> assertFalse(dump.containsKey("multimodal")));
    }

    @Test
    void conversationalGoldenModelDumpByAliasUsesDeepEvalSerializationAliases() {
        var golden = ConversationalGolden.builder("scenario")
                .expectedOutcome("outcome")
                .userDescription("user")
                .turns(List.of(Turn.builder("assistant", "answer")
                        .userId("u1")
                        .toolsCalled(List.of(new ToolCall("Search", Map.of("query", "refund"), "ok")))
                        .build()))
                .additionalMetadata(Map.of("source", "unit"))
                .customColumnKeyValues(Map.of("case_id", "case-1"))
                .build();

        var dump = golden.modelDump(true);
        var turn = ((List<Map<String, Object>>) dump.get("turns")).getFirst();
        var tool = ((List<Map<String, Object>>) turn.get("tools_called")).getFirst();

        assertAll(
                () -> assertEquals("outcome", dump.get("expectedOutcome")),
                () -> assertEquals("user", dump.get("userDescription")),
                () -> assertEquals(Map.of("source", "unit"), dump.get("additionalMetadata")),
                () -> assertEquals(Map.of("case_id", "case-1"), dump.get("customColumnKeyValues")),
                () -> assertEquals("u1", turn.get("user_id")),
                () -> assertEquals(Map.of("query", "refund"), tool.get("inputParameters")),
                () -> assertFalse(dump.containsKey("expected_outcome")),
                () -> assertFalse(dump.containsKey("multimodal")));
    }

    @Test
    void goldenDetectsMultimodalPlaceholders() {
        var golden = Golden.builder("Describe [DEEPEVAL:IMAGE:image-id]")
                .actualOutput("A car")
                .build();

        assertTrue(golden.multimodal());
    }

    @Test
    void goldenDoesNotUseExpectedOutputPlaceholderForMultimodalFlagLikeDeepEval() {
        var image = new MllmImage("YWJj", "image/png");
        var golden = Golden.builder("Describe the image")
                .expectedOutput("A car " + image)
                .build();

        assertAll(
                () -> assertFalse(golden.multimodal()),
                () -> assertEquals(1, golden.imagesMapping().size()),
                () -> assertTrue(golden.imagesMapping().containsValue(image)));
    }

    @Test
    void goldenRetrievalContextAcceptsRetrievedContextData() {
        var golden = Golden.builder("input")
                .retrievalContext(List.of(new RetrievedContextData("[DEEPEVAL:IMAGE:image-id]", "retriever")))
                .build();
        Object retrieved = golden.retrievalContext().getFirst();

        assertAll(
                () -> assertTrue(retrieved instanceof RetrievedContextData),
                () -> assertEquals(new RetrievedContextData("[DEEPEVAL:IMAGE:image-id]", "retriever"), retrieved),
                () -> assertTrue(golden.multimodal()));
    }

    @Test
    void goldenRetrievalContextRejectsUnsupportedTypes() {
        var builder = Golden.builder("input").retrievalContext(List.of(42));

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void goldenRetrievalContextRejectsUnsupportedTypesThroughRecordConstructor() {
        assertThrows(IllegalArgumentException.class, () -> new Golden(
                "input", null, null, null, (List) List.of(42), null, null, null, null, null, null,
                null, null, null, null, false));
    }

    @Test
    void goldenContextRejectsNullEntries() {
        var context = new ArrayList<String>();
        context.add(null);
        var builder = Golden.builder("input").context(context);

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void goldenContextRejectsNonStringEntries() {
        var builder = Golden.builder("input").context((List) List.of("valid", 123));

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void goldenToolsCalledRejectNonToolCallEntries() {
        var builder = Golden.builder("input").toolsCalled((List) List.of(new ToolCall("Search"), 123));

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void goldenExpectedToolsRejectNonToolCallEntries() {
        var builder = Golden.builder("input").expectedTools((List) List.of(new ToolCall("Search"), 123));

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void goldenCustomColumnKeyValuesRejectsNullValues() {
        var customColumns = new HashMap<String, String>();
        customColumns.put("case_id", null);
        var builder = Golden.builder("input").customColumnKeyValues(customColumns);

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void goldenCustomColumnKeyValuesRejectsNonStringValues() {
        var builder = Golden.builder("input").customColumnKeyValues((Map) Map.of("case_id", 123));

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void goldenImagesMappingReturnsRegisteredImages() {
        var image = new MllmImage("YWJj", "image/png");
        var golden = Golden.builder("Describe " + image).build();

        var mapping = golden.imagesMapping();

        assertAll(
                () -> assertEquals(1, mapping.size()),
                () -> assertTrue(mapping.containsValue(image)));
    }

    @Test
    void goldenImagesMappingReturnsNullWhenNoRegisteredImagesExist() {
        var golden = Golden.builder("Describe [DEEPEVAL:IMAGE:missing-id]").build();

        assertEquals(null, golden.imagesMapping());
    }

    @Test
    void conversationalGoldenDetectsMultimodalPlaceholdersInTurns() {
        var golden = ConversationalGolden.builder("Scenario")
                .turns(List.of(new Turn("user", "Describe [DEEPEVAL:PDF:doc-id]")))
                .build();

        assertTrue(golden.multimodal());
    }

    @Test
    void conversationalGoldenDoesNotUseContextPlaceholderForMultimodalFlagLikeDeepEval() {
        var image = new MllmImage("YWJj", "image/png");
        var golden = ConversationalGolden.builder("Scenario")
                .context(List.of("Context " + image))
                .build();

        assertAll(
                () -> assertFalse(golden.multimodal()),
                () -> assertEquals(1, golden.imagesMapping().size()),
                () -> assertTrue(golden.imagesMapping().containsValue(image)));
    }

    @Test
    void conversationalGoldenContextRejectsNullEntries() {
        var context = new ArrayList<String>();
        context.add(null);
        var builder = ConversationalGolden.builder("Scenario").context(context);

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void conversationalGoldenContextRejectsNonStringEntries() {
        var builder = ConversationalGolden.builder("Scenario").context((List) List.of("valid", 123));

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void conversationalGoldenCustomColumnKeyValuesRejectsNullValues() {
        var customColumns = new HashMap<String, String>();
        customColumns.put("case_id", null);
        var builder = ConversationalGolden.builder("Scenario").customColumnKeyValues(customColumns);

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void conversationalGoldenCustomColumnKeyValuesRejectsNonStringValues() {
        var builder = ConversationalGolden.builder("Scenario").customColumnKeyValues((Map) Map.of("case_id", 123));

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void conversationalGoldenImagesMappingReturnsRegisteredImages() {
        var image = new MllmImage("YWJj", "image/png");
        var golden = ConversationalGolden.builder("Scenario")
                .turns(List.of(new Turn("user", "Describe " + image)))
                .build();

        var mapping = golden.imagesMapping();

        assertAll(
                () -> assertEquals(1, mapping.size()),
                () -> assertTrue(mapping.containsValue(image)));
    }

    @Test
    void conversationalGoldenImagesMappingReturnsNullWhenNoRegisteredImagesExist() {
        var golden = ConversationalGolden.builder("Scenario")
                .turns(List.of(new Turn("user", "[DEEPEVAL:IMAGE:missing-id]")))
                .build();

        assertEquals(null, golden.imagesMapping());
    }
}
