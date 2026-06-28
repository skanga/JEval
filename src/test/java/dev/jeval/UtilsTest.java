package dev.jeval;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UtilsTest {

    private record TracePayload(String name, Double score) {
    }

    @TempDir
    Path tempDir;

    @Test
    void shortenMatchesDeepEvalCoreCases() {
        assertEquals("hello", Utils.shorten("hello", 10));
        assertEquals("hello", Utils.shorten("hello", 5));
        assertEquals("he...", Utils.shorten("helloworld", 5));
        assertEquals("", Utils.shorten("", 5));
        assertEquals("", Utils.shorten(null, 5));
    }

    @Test
    void shortenUsesDeepEvalLongDefaultWhenMaxLengthOmitted() {
        assertEquals("hello", Utils.shorten("hello"));
    }

    @Test
    void lengthDefaultsMatchDeepEvalFallbacks() {
        assertEquals(40, Utils.lenTiny());
        assertEquals(60, Utils.lenShort());
        assertEquals(120, Utils.lenMedium());
        assertEquals(240, Utils.lenLong());
    }

    @Test
    void shortenHandlesZeroLengthLongSuffixAndNonStringInput() {
        assertEquals("", Utils.shorten("abc", 0));
        assertEquals("**", Utils.shorten("abcdef", 2, "***"));
        assertEquals("...", Utils.shorten(12345, 3));
    }

    @Test
    void normalizeTextMatchesDeepEvalRules() {
        assertEquals("quick brown fox", Utils.normalizeText("The quick, brown fox!"));
        assertEquals("answer", Utils.normalizeText("  An ANSWER.  "));
    }

    @Test
    void isMissingTreatsNullAndBlankAsMissing() {
        assertEquals(true, Utils.isMissing(null));
        assertEquals(true, Utils.isMissing("  "));
        assertEquals(false, Utils.isMissing("value"));
    }

    @Test
    void checkIfMultimodalMatchesDeepEvalPlaceholderPattern() {
        assertEquals(false, Utils.checkIfMultimodal("plain text"));
        assertEquals(true, Utils.checkIfMultimodal("see [DEEPEVAL:IMAGE:https://example.com/a.png]"));
        assertEquals(true, Utils.checkIfMultimodal("read [DEEPEVAL:PDF:https://example.com/a.pdf]"));
    }

    @Test
    void convertToMultiModalArrayParsesStringPlaceholdersLikeDeepEval() {
        var image = new MllmImage("YWJj", "image/png");

        var parts = Utils.convertToMultiModalArray("before " + image + " after");

        assertEquals("before ", parts.get(0));
        assertSame(image, parts.get(1));
        assertEquals(" after", parts.get(2));
    }

    @Test
    void convertToMultiModalArrayFlattensListInputsLikeDeepEval() {
        var first = new MllmImage("Zmlyc3Q=", "image/png");
        var second = new MllmImage("c2Vjb25k", "image/png");

        var parts = Utils.convertToMultiModalArray(List.of("one " + first, "two " + second));

        assertEquals("one ", parts.get(0));
        assertSame(first, parts.get(1));
        assertEquals("two ", parts.get(2));
        assertSame(second, parts.get(3));
    }

    @Test
    void formatErrorTextIncludesExceptionTypeAndMessageLikeDeepEval() {
        assertEquals(
                "IllegalArgumentException: missing value",
                Utils.formatErrorText(new IllegalArgumentException("missing value")));
    }

    @Test
    void formatErrorTextCanIncludeStackTraceLikeDeepEval() {
        var text = Utils.formatErrorText(new IllegalStateException("bad state"), true);

        assertEquals(true, text.startsWith("IllegalStateException: bad state\njava.lang.IllegalStateException: bad state"));
        assertEquals(true, text.contains("UtilsTest.formatErrorTextCanIncludeStackTraceLikeDeepEval"));
    }

    @Test
    void requireParamReturnsProvidedValueLikeDeepEval() {
        assertEquals(
                "secret",
                Utils.requireParam("secret", "OpenAI", "OPENAI_API_KEY", "apiKey"));
    }

    @Test
    void requireParamRejectsMissingValueWithDeepEvalMessage() {
        var error = assertThrows(DeepEvalException.class, () ->
                Utils.requireParam(null, "OpenAI", "OPENAI_API_KEY", "apiKey"));

        assertEquals(
                "OpenAI is missing a required parameter. Set OPENAI_API_KEY in your environment or pass apiKey.",
                error.getMessage());
    }

    @Test
    void requireDependencyReturnsAvailableClassLikeDeepEvalImport() {
        assertSame(String.class, Utils.requireDependency("java.lang.String", "Java", null));
    }

    @Test
    void requireDependencyRejectsMissingClassWithDeepEvalMessage() {
        var error = assertThrows(DeepEvalException.class, () ->
                Utils.requireDependency("dev.jeval.DoesNotExist", "Provider", "Add the dependency."));

        assertEquals(
                "Provider requires the `dev.jeval.DoesNotExist` package. Add the dependency.",
                error.getMessage());
    }

    @Test
    void deleteFileIfExistsDeletesExistingFileLikeDeepEval() throws Exception {
        var file = tempDir.resolve("delete-me.txt");
        Files.writeString(file, "content");

        Utils.deleteFileIfExists(file);

        assertEquals(false, Files.exists(file));
    }

    @Test
    void deleteFileIfExistsIgnoresMissingFilesLikeDeepEval() {
        assertDoesNotThrow(() -> Utils.deleteFileIfExists(tempDir.resolve("missing.txt")));
    }

    @Test
    void generateUuidReturnsCanonicalUuidStringLikeDeepEval() {
        var first = Utils.generateUuid();
        var second = Utils.generateUuid();

        assertEquals(36, first.length());
        assertEquals(first, UUID.fromString(first).toString());
        assertEquals(false, first.equals(second));
    }

    @Test
    void camelToSnakeMatchesDeepEvalRegexRules() {
        assertEquals("actual_output", Utils.camelToSnake("actualOutput"));
        assertEquals("llm_test_case", Utils.camelToSnake("LLMTestCase"));
        assertEquals("api_response_id", Utils.camelToSnake("APIResponseID"));
    }

    @Test
    void convertKeysToSnakeCaseRecursesExceptMetadataValues() {
        var data = Map.of(
                "actualOutput", "answer",
                "nestedList", List.of(Map.of("expectedOutput", "expected")),
                "additionalMetadata", Map.of("innerCamelKey", "kept"),
                "metadata", Map.of("anotherInnerKey", "also kept"));

        var converted = Utils.convertKeysToSnakeCase(data);

        assertEquals(Map.of(
                "actual_output", "answer",
                "nested_list", List.of(Map.of("expected_output", "expected")),
                "additional_metadata", Map.of("innerCamelKey", "kept"),
                "metadata", Map.of("anotherInnerKey", "also kept")),
                converted);
    }

    @Test
    void prettifyListFormatsEmptyAndStringListsLikeDeepEval() {
        assertEquals("[]", Utils.prettifyList(List.of()));
        assertEquals("[\n    \"first\",\n    \"second\"\n]", Utils.prettifyList(List.of("first", "second")));
    }

    @Test
    void prettifyListFormatsBooleanAndNullLikePythonRepr() {
        var values = new java.util.ArrayList<Object>();
        values.add(true);
        values.add(false);
        values.add(null);

        assertEquals("[\n    True,\n    False,\n    None\n]", Utils.prettifyList(values));
    }

    @Test
    void prettifyListFormatsMapsLikePythonRepr() {
        var first = new LinkedHashMap<String, Object>();
        first.put("a", 1);
        first.put("b", null);
        var second = new LinkedHashMap<String, Object>();
        second.put("ok", true);

        assertEquals("[\n    {'a': 1, 'b': None},\n    {'ok': True}\n]", Utils.prettifyList(List.of(first, second)));
    }

    @Test
    void getLcsReturnsLongestCommonSubsequenceInOrder() {
        assertEquals(
                List.of("B", "D", "A", "B"),
                Utils.getLcs(
                        List.of("A", "B", "C", "B", "D", "A", "B"),
                        List.of("B", "D", "C", "A", "B", "A")));
        assertEquals(List.of(), Utils.getLcs(List.of("A"), List.of()));
    }

    @Test
    void chunkTextSplitsWordsIntoFixedSizeChunks() {
        assertEquals(
                List.of("one two", "three four", "five"),
                Utils.chunkText("one two three four five", 2));
        assertEquals(List.of("one two three"), Utils.chunkText("one two three", 20));
    }

    @Test
    void chunkTextUsesDeepEvalDefaultChunkSize() {
        assertEquals(
                List.of("one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen twenty",
                        "twenty-one"),
                Utils.chunkText("one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen twenty twenty-one"));
    }

    @Test
    void chunkTextReturnsNoChunksForNegativeSizeLikeDeepEval() {
        assertEquals(List.of(), assertDoesNotThrow(() -> Utils.chunkText("one two three", -1)));
    }

    @Test
    void chunkTextRejectsZeroSizeInsteadOfLoopingForever() {
        assertThrows(IllegalArgumentException.class, () -> Utils.chunkText("one two three", 0));
    }

    @Test
    void cleanNestedDictRemovesNullBytesFromNestedStrings() {
        var data = Map.of(
                "text", "a\u0000b",
                "nested", Map.of("value", "c\u0000d"),
                "items", List.of("e\u0000f", Map.of("leaf", "g\u0000h")));

        assertEquals(
                Map.of(
                        "text", "ab",
                        "nested", Map.of("value", "cd"),
                        "items", List.of("ef", Map.of("leaf", "gh"))),
                Utils.cleanNestedDict(data));
    }

    @Test
    void cleanNestedDictPreservesMapKeysLikeDeepEval() {
        var data = new LinkedHashMap<Object, Object>();
        data.put(10, "a\u0000b");

        assertEquals(Map.of(10, "ab"), Utils.cleanNestedDict(data));
    }

    @Test
    void serializeSortsMapKeysAndListItemsDeterministically() {
        var data = new LinkedHashMap<String, Object>();
        data.put("b", 2);
        data.put("a", List.of(Map.of("z", 1), Map.of("a", 1)));
        data.put("c", null);

        assertEquals("{\"a\": [{\"a\": 1}, {\"z\": 1}], \"b\": 2, \"c\": null}", Utils.serialize(data));
    }

    @Test
    void serializeEscapesNonAsciiLikePythonJsonDumpsDefault() {
        assertEquals("{\"text\": \"\\u00e9\"}", Utils.serialize(Map.of("text", "é")));
        assertEquals("{\"text\":\"\\u00e9\"}", Utils.serializeToJson(Map.of("text", "é")));
    }

    @Test
    void makeJsonSerializableSanitizesTracePayloadsLikeDeepEval() {
        var values = new java.util.ArrayList<Object>();
        values.add(Double.NaN);
        values.add(Double.POSITIVE_INFINITY);
        values.add(1.5);
        var data = new LinkedHashMap<Object, Object>();
        data.put(10, "a\u0000b");
        data.put("values", values);

        var sanitized = Utils.makeJsonSerializable(data);

        var expectedValues = new java.util.ArrayList<Object>();
        expectedValues.add(null);
        expectedValues.add(null);
        expectedValues.add(1.5);
        assertEquals(Map.of("10", "ab", "values", expectedValues), sanitized);
    }

    @Test
    void makeJsonSerializableForMetadataPreservesPrimitiveTypesLikeDeepEval() {
        var data = new LinkedHashMap<String, Object>();
        data.put("flag", true);
        data.put("count", 7);
        data.put("ratio", 0.25);
        data.put("missing", null);
        data.put("label", "ok");
        data.put("bad", Double.NaN);

        var expected = new LinkedHashMap<String, Object>();
        expected.put("flag", true);
        expected.put("count", 7);
        expected.put("ratio", 0.25);
        expected.put("missing", null);
        expected.put("label", "ok");
        expected.put("bad", null);
        assertEquals(expected, Utils.makeJsonSerializableForMetadata(data));
    }

    @Test
    void makeJsonSerializableMarksCircularReferencesLikeDeepEval() {
        var values = new java.util.ArrayList<Object>();
        values.add("root");
        values.add(values);

        assertEquals(List.of("root", "<circular>"), Utils.makeJsonSerializable(values));
    }

    @Test
    void makeJsonSerializableMarksRepeatedReferencesLikeDeepEval() {
        var shared = new java.util.ArrayList<Object>();
        shared.add("leaf");
        var values = new java.util.ArrayList<Object>();
        values.add(shared);
        values.add(shared);

        assertEquals(List.of(List.of("leaf"), "<circular>"), Utils.makeJsonSerializable(values));
    }

    @Test
    void makeJsonSerializableSerializesRecordsLikeDeepEvalObjects() {
        var sanitized = Utils.makeJsonSerializable(new TracePayload("a\u0000b", Double.NaN));

        var expected = new LinkedHashMap<String, Object>();
        expected.put("name", "ab");
        expected.put("score", null);
        assertEquals(expected, sanitized);
    }

    @Test
    void makeJsonSerializableSerializesSetsLikeDeepEvalObjects() {
        var values = new java.util.LinkedHashSet<Object>();
        values.add("x\u0000y");
        values.add(Double.NEGATIVE_INFINITY);

        var expected = new java.util.ArrayList<Object>();
        expected.add("xy");
        expected.add(null);
        assertEquals(expected, Utils.makeJsonSerializable(values));
    }

    @Test
    void makeJsonSerializableSerializesArraysLikeDeepEvalSequences() {
        var expected = new java.util.ArrayList<Object>();
        expected.add("ab");
        expected.add(null);

        assertEquals(expected, Utils.makeJsonSerializable(new Object[]{"a\u0000b", Double.POSITIVE_INFINITY}));
    }

    @Test
    void serializeToJsonUsesTraceSafeSerializationLikeDeepEval() {
        var values = new java.util.ArrayList<Object>();
        values.add(Double.NaN);
        values.add("x\u0000y");
        var data = new LinkedHashMap<String, Object>();
        data.put("values", values);

        assertEquals("{\"values\":[null,\"xy\"]}", Utils.serializeToJson(data));
    }

    @Test
    void batcherReturnsFixedSizeBatchesAndLeftovers() {
        assertEquals(
                List.of(List.of(1, 2), List.of(3, 4), List.of(5)),
                Utils.batcher(List.of(1, 2, 3, 4, 5), 2));
        assertEquals(List.of(), Utils.batcher(List.of(), 4));
    }

    @Test
    void batcherUsesDeepEvalDefaultBatchSize() {
        assertEquals(
                List.of(List.of(1, 2, 3, 4), List.of(5)),
                Utils.batcher(List.of(1, 2, 3, 4, 5)));
    }

    @Test
    void batcherReturnsSingleLeftoverBatchForNonpositiveSizeLikeDeepEval() {
        assertEquals(
                List.of(List.of(1, 2, 3)),
                assertDoesNotThrow(() -> Utils.batcher(List.of(1, 2, 3), -1)));
        assertEquals(
                List.of(List.of(1, 2, 3)),
                assertDoesNotThrow(() -> Utils.batcher(List.of(1, 2, 3), 0)));
    }

    @Test
    void softmaxNormalizesEachRowLikeDeepEval() {
        var result = Utils.softmax(List.of(
                List.of(1.0, 2.0, 3.0),
                List.of(2.0, 2.0)));

        assertEquals(0.09003057317038046, result.getFirst().get(0), 1e-15);
        assertEquals(0.24472847105479764, result.getFirst().get(1), 1e-15);
        assertEquals(0.6652409557748218, result.getFirst().get(2), 1e-15);
        assertEquals(List.of(0.5, 0.5), result.get(1));
    }

    @Test
    void cosineSimilarityMatchesDeepEvalFormula() {
        assertEquals(
                32.0 / (Math.sqrt(14.0) * Math.sqrt(77.0)),
                Utils.cosineSimilarity(List.of(1.0, 2.0, 3.0), List.of(4.0, 5.0, 6.0)),
                1e-15);
    }
}
