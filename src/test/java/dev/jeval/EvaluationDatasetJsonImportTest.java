package dev.jeval;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EvaluationDatasetJsonImportTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void addTestCasesFromJsonFileImportsSingleTurnCases() throws IOException {
        var file = tempDir.resolve("test-cases.json");
        Files.writeString(file, """
                [
                  {
                    "input": "What if these shoes don't fit?",
                    "actual_output": "We offer a 30-day full refund.",
                    "expected_output": "We offer a 30-day full refund.",
                    "context": ["Refund policy"],
                    "retrieval_context": ["Customers get a 30-day refund"]
                  }
                ]
                """);
        var dataset = new EvaluationDataset();

        dataset.addTestCasesFromJsonFile(file, "input", "actual_output", "expected_output",
                "context", "retrieval_context");

        var testCase = dataset.testCases().getFirst();
        assertAll(
                () -> assertEquals(1, dataset.testCases().size()),
                () -> assertEquals("What if these shoes don't fit?", testCase.input()),
                () -> assertEquals("We offer a 30-day full refund.", testCase.actualOutput()),
                () -> assertEquals("We offer a 30-day full refund.", testCase.expectedOutput()),
                () -> assertEquals(List.of("Refund policy"), testCase.context()),
                () -> assertEquals(List.of("Customers get a 30-day refund"), testCase.retrievalContext()));
    }

    @Test
    void addTestCasesFromJsonFileAcceptsCamelCaseAliasesForDefaultKeys() throws IOException {
        var file = tempDir.resolve("camel-case-test-cases.json");
        Files.writeString(file, """
                [
                  {
                    "input": "Can I get a refund?",
                    "actualOutput": "I searched the policy.",
                    "expectedOutput": "I searched the policy.",
                    "retrievalContext": ["Customers get a 30-day refund"],
                    "tokenCost": 0.42,
                    "completionTime": 2.5,
                    "customColumnKeyValues": {"risk": "high"},
                    "toolsCalled": [{"name": "PolicySearch"}],
                    "expectedTools": [{"name": "PolicySearch"}],
                    "mcpServers": [{"server_name": "policy"}],
                    "mcpToolsCalled": [{"name": "mcp-search"}],
                    "mcpResourcesCalled": [{"uri": "file://policy"}],
                    "mcpPromptsCalled": [{"name": "policy-prompt"}]
                  }
                ]
                """);
        var dataset = new EvaluationDataset();

        dataset.addTestCasesFromJsonFile(file, "input", "actual_output", "expected_output",
                "context", "retrieval_context", "tools_called", "expected_tools", "metadata");

        var testCase = dataset.testCases().getFirst();
        assertAll(
                () -> assertEquals("I searched the policy.", testCase.actualOutput()),
                () -> assertEquals("I searched the policy.", testCase.expectedOutput()),
                () -> assertEquals(List.of("Customers get a 30-day refund"), testCase.retrievalContext()),
                () -> assertEquals(0.42, testCase.tokenCost()),
                () -> assertEquals(2.5, testCase.completionTime()),
                () -> assertEquals(Map.of("risk", "high"), testCase.customColumnKeyValues()),
                () -> assertEquals(List.of(new ToolCall("PolicySearch")), testCase.toolsCalled()),
                () -> assertEquals(List.of(new ToolCall("PolicySearch")), testCase.expectedTools()),
                () -> assertEquals(List.of(Map.of("server_name", "policy")), testCase.mcpServers()),
                () -> assertEquals(List.of(Map.of("name", "mcp-search")), testCase.mcpToolsCalled()),
                () -> assertEquals(List.of(Map.of("uri", "file://policy")), testCase.mcpResourcesCalled()),
                () -> assertEquals(List.of(Map.of("name", "policy-prompt")), testCase.mcpPromptsCalled()));
    }

    @Test
    void addTestCasesFromJsonFileDefaultsMissingToolListsToEmptyLikeDeepEval() throws IOException {
        var file = tempDir.resolve("test-cases-no-tools.json");
        Files.writeString(file, """
                [{"input": "Ask", "actual_output": "Ans"}]
                """);
        var dataset = new EvaluationDataset();

        dataset.addTestCasesFromJsonFile(file, "input", "actual_output", "expected_output",
                "context", "retrieval_context", "tools_called", "expected_tools");

        var testCase = dataset.testCases().getFirst();
        assertAll(
                () -> assertEquals(List.of(), testCase.toolsCalled()),
                () -> assertEquals(List.of(), testCase.expectedTools()));
    }

    @Test
    void addTestCasesFromJsonFileRejectsNullToolListsLikeDeepEval() throws IOException {
        var file = tempDir.resolve("test-cases-null-tools.json");
        Files.writeString(file, """
                [{"input": "Ask", "actual_output": "Ans", "tools_called": null}]
                """);

        assertThrows(IllegalArgumentException.class,
                () -> new EvaluationDataset().addTestCasesFromJsonFile(file, "input", "actual_output",
                        "expected_output", "context", "retrieval_context", "tools_called", "expected_tools"));
    }

    @Test
    void addTestCasesFromCsvFileImportsSingleTurnCases() throws IOException {
        var file = tempDir.resolve("test-cases.csv");
        Files.writeString(file, """
                input,actual_output,expected_output,context,retrieval_context
                "What if these shoes don't fit?",We offer a 30-day full refund.,We offer a 30-day full refund.,Refund policy;Orders,Customers get a 30-day refund
                """);
        var dataset = new EvaluationDataset();

        dataset.addTestCasesFromCsvFile(file, "input", "actual_output", "expected_output",
                "context", "retrieval_context");

        var testCase = dataset.testCases().getFirst();
        assertAll(
                () -> assertEquals(1, dataset.testCases().size()),
                () -> assertEquals("What if these shoes don't fit?", testCase.input()),
                () -> assertEquals("We offer a 30-day full refund.", testCase.actualOutput()),
                () -> assertEquals("We offer a 30-day full refund.", testCase.expectedOutput()),
                () -> assertEquals(List.of("Refund policy", "Orders"), testCase.context()),
                () -> assertEquals(List.of("Customers get a 30-day refund"), testCase.retrievalContext()));
    }

    @Test
    void addTestCasesFromCsvFileSplitsContextsWithCustomDelimiters() throws IOException {
        var file = tempDir.resolve("custom-delimiter-test-cases.csv");
        Files.writeString(file, """
                input,actual_output,context,retrieval_context
                Ask,Ans,ctx one/ctx two,rctx one~rctx two
                """);
        var dataset = new EvaluationDataset();

        dataset.addTestCasesFromCsvFile(file, "input", "actual_output", "expected_output",
                "context", "/", "retrieval_context", "~", "tools_called", "expected_tools",
                "additional_metadata");

        var testCase = dataset.testCases().getFirst();
        assertAll(
                () -> assertEquals(List.of("ctx one", "ctx two"), testCase.context()),
                () -> assertEquals(List.of("rctx one", "rctx two"), testCase.retrievalContext()));
    }

    @Test
    void addTestCasesFromFilesRejectNonFiniteNumericFields() throws IOException {
        var json = tempDir.resolve("test-cases-infinite-number.json");
        Files.writeString(json, """
                [{"input": "Ask", "actual_output": "Ans", "token_cost": 1e999}]
                """);
        var csv = tempDir.resolve("test-cases-infinity.csv");
        Files.writeString(csv, """
                input,actual_output,token_cost
                Ask,Ans,Infinity
                """);

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new EvaluationDataset().addTestCasesFromJsonFile(json, "input", "actual_output",
                                "expected_output", "context", "retrieval_context")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new EvaluationDataset().addTestCasesFromCsvFile(csv, "input", "actual_output",
                                "expected_output", "context", "retrieval_context")));
    }

    @Test
    void addTestCasesFromCsvFileImportsToolCalls() throws IOException {
        var file = tempDir.resolve("tool-cases.csv");
        Files.writeString(file, """
                input,actual_output,tools_called,expected_tools
                Can I get a refund?,I searched the policy.,"[{""name"":""PolicySearch"",""input_parameters"":{""query"":""refund""},""output"":""30 days""}]","[{""name"":""PolicySearch""}]"
                """);
        var dataset = new EvaluationDataset();

        dataset.addTestCasesFromCsvFile(file, "input", "actual_output", "expected_output",
                "context", "retrieval_context", "tools_called", "expected_tools");

        var testCase = dataset.testCases().getFirst();
        assertAll(
                () -> assertEquals(List.of(new ToolCall("PolicySearch", Map.of("query", "refund"), "30 days")),
                        testCase.toolsCalled()),
                () -> assertEquals(List.of(new ToolCall("PolicySearch")), testCase.expectedTools()));
    }

    @Test
    void addTestCasesFromCsvFileUsesDeepEvalDefaultToolDelimiters() throws IOException {
        var file = tempDir.resolve("tool-name-cases.csv");
        Files.writeString(file, """
                input,actual_output,tools_called,expected_tools
                Ask,Ans,search;lookup,finalize;notify
                """);
        var dataset = new EvaluationDataset();

        dataset.addTestCasesFromCsvFile(file, "input", "actual_output", "expected_output",
                "context", "retrieval_context", "tools_called", "expected_tools");

        var testCase = dataset.testCases().getFirst();
        assertAll(
                () -> assertEquals(List.of(new ToolCall("search"), new ToolCall("lookup")), testCase.toolsCalled()),
                () -> assertEquals(List.of(new ToolCall("finalize"), new ToolCall("notify")),
                        testCase.expectedTools()));
    }

    @Test
    void addTestCasesFromCsvFileImportsAdditionalMetadata() throws IOException {
        var file = tempDir.resolve("metadata-cases.csv");
        Files.writeString(file, """
                input,actual_output,additional_metadata
                Can I get a refund?,I searched the policy.,"{""source"":""support"",""priority"":""high""}"
                """);
        var dataset = new EvaluationDataset();

        dataset.addTestCasesFromCsvFile(file, "input", "actual_output", "expected_output",
                "context", "retrieval_context", "tools_called", "expected_tools", "additional_metadata");

        assertEquals(Map.of("source", "support", "priority", "high"),
                dataset.testCases().getFirst().additionalMetadata());
    }

    @Test
    void csvImportsUseEmptyListsForMissingCollections() throws IOException {
        var testCasesFile = tempDir.resolve("no-context-cases.csv");
        Files.writeString(testCasesFile, """
                input,actual_output
                Ask,Ans
                """);
        var testCases = new EvaluationDataset();

        testCases.addTestCasesFromCsvFile(testCasesFile, "input", "actual_output", "expected_output",
                "context", "retrieval_context");

        var goldensFile = tempDir.resolve("no-context-goldens.csv");
        Files.writeString(goldensFile, """
                input,actual_output
                Ask,Ans
                """);
        var goldens = new EvaluationDataset();

        goldens.addGoldensFromCsvFile(goldensFile);

        assertAll(
                () -> assertEquals(List.of(), testCases.testCases().getFirst().context()),
                () -> assertEquals(List.of(), testCases.testCases().getFirst().retrievalContext()),
                () -> assertEquals(List.of(), testCases.testCases().getFirst().toolsCalled()),
                () -> assertEquals(List.of(), testCases.testCases().getFirst().expectedTools()),
                () -> assertEquals(List.of(), goldens.goldens().getFirst().context()),
                () -> assertEquals(List.of(), goldens.goldens().getFirst().retrievalContext()),
                () -> assertEquals(List.of(), goldens.goldens().getFirst().toolsCalled()),
                () -> assertEquals(List.of(), goldens.goldens().getFirst().expectedTools()));
    }

    @Test
    void addTestCasesFromJsonFileImportsAdditionalMetadata() throws IOException {
        var file = tempDir.resolve("metadata-cases.json");
        Files.writeString(file, """
                [
                  {
                    "input": "Can I get a refund?",
                    "actual_output": "I searched the policy.",
                    "additional_metadata": {"source": "support", "priority": "high"}
                  }
                ]
                """);
        var dataset = new EvaluationDataset();

        dataset.addTestCasesFromJsonFile(file, "input", "actual_output", "expected_output",
                "context", "retrieval_context", "tools_called", "expected_tools", "additional_metadata");

        assertEquals(Map.of("source", "support", "priority", "high"),
                dataset.testCases().getFirst().additionalMetadata());
    }

    @Test
    void addTestCasesFromJsonFileRejectsMissingRequiredFields() throws IOException {
        var file = tempDir.resolve("missing.json");
        Files.writeString(file, """
                [{"input": "hello"}]
                """);

        assertThrows(IllegalArgumentException.class,
                () -> new EvaluationDataset().addTestCasesFromJsonFile(file, "input", "actual_output",
                        "expected_output", "context", "retrieval_context"));
    }

    @Test
    void addTestCasesFromJsonFileRejectsInvalidJson() throws IOException {
        var file = tempDir.resolve("invalid.json");
        Files.writeString(file, "{not-json");

        assertThrows(IllegalArgumentException.class,
                () -> new EvaluationDataset().addTestCasesFromJsonFile(file, "input", "actual_output",
                        "expected_output", "context", "retrieval_context"));
    }

    @Test
    void addGoldensFromJsonlFileReportsInvalidLineNumberLikeDeepEval() throws IOException {
        var file = tempDir.resolve("invalid-line.jsonl");
        Files.writeString(file, """
                {"input":"first"}
                {not-json}
                """);

        var error = assertThrows(IllegalArgumentException.class,
                () -> new EvaluationDataset().addGoldensFromJsonlFile(file));

        assertTrue(error.getMessage().contains("line 2"));
    }

    @Test
    void addTestCasesFromJsonFileAcceptsTrailingCommasLikeDeepEval() throws IOException {
        var file = tempDir.resolve("trailing-comma.json");
        Files.writeString(file, """
                [
                  {
                    "input": "Ask",
                    "actual_output": "Ans",
                  },
                ]
                """);
        var dataset = new EvaluationDataset();

        dataset.addTestCasesFromJsonFile(file, "input", "actual_output", "expected_output",
                "context", "retrieval_context");

        assertEquals("Ask", dataset.testCases().getFirst().input());
    }

    @Test
    void addTestCasesFromJsonFileRejectsNonStringContextEntriesLikeDeepEval() throws IOException {
        var file = tempDir.resolve("non-string-context.json");
        Files.writeString(file, """
                [{"input": "Ask", "actual_output": "Ans", "context": [123]}]
                """);

        assertThrows(IllegalArgumentException.class,
                () -> new EvaluationDataset().addTestCasesFromJsonFile(file, "input", "actual_output",
                        "expected_output", "context", "retrieval_context"));
    }

    @Test
    void addGoldensFromJsonFileRejectsNonStringCustomColumnValuesLikeDeepEval() throws IOException {
        var file = tempDir.resolve("non-string-custom-columns.json");
        Files.writeString(file, """
                [{"input": "Ask", "custom_column_key_values": {"case_id": 123}}]
                """);

        assertThrows(IllegalArgumentException.class, () -> new EvaluationDataset().addGoldensFromJsonFile(file));
    }

    @Test
    void addGoldensFromJsonFileRejectsNonStringInputLikeDeepEval() throws IOException {
        var file = tempDir.resolve("non-string-golden-input.json");
        Files.writeString(file, """
                [{"input": 123}]
                """);

        assertThrows(IllegalArgumentException.class, () -> new EvaluationDataset().addGoldensFromJsonFile(file));
    }

    @Test
    void addGoldensFromJsonFileRejectsNonStringScenarioLikeDeepEval() throws IOException {
        var file = tempDir.resolve("non-string-scenario.json");
        Files.writeString(file, """
                [{"scenario": 123, "turns": [{"role": "user", "content": "hi"}]}]
                """);

        assertThrows(IllegalArgumentException.class, () -> new EvaluationDataset().addGoldensFromJsonFile(file));
    }

    @Test
    void addGoldensFromJsonFileTreatsFalseScenarioAsSingleTurnLikeDeepEval() throws IOException {
        var file = tempDir.resolve("false-scenario.json");
        Files.writeString(file, """
                [{"scenario": false, "input": "Ask"}]
                """);
        var dataset = new EvaluationDataset();

        dataset.addGoldensFromJsonFile(file);

        assertAll(
                () -> assertEquals(false, dataset.multiTurn()),
                () -> assertEquals("Ask", dataset.goldens().getFirst().input()));
    }

    @Test
    void addGoldensFromJsonFileRejectsNonStringTurnContentLikeDeepEval() throws IOException {
        var file = tempDir.resolve("non-string-turn-content.json");
        Files.writeString(file, """
                [{"scenario": "Support chat", "turns": [{"role": "user", "content": 123}]}]
                """);

        assertThrows(IllegalArgumentException.class, () -> new EvaluationDataset().addGoldensFromJsonFile(file));
    }

    @Test
    void addGoldensFromJsonFileDefaultsMissingTurnsToEmptyLikeDeepEval() throws IOException {
        var file = tempDir.resolve("missing-turns.json");
        Files.writeString(file, """
                [{"scenario": "Support chat"}]
                """);
        var dataset = new EvaluationDataset();

        dataset.addGoldensFromJsonFile(file);

        assertEquals(List.of(), dataset.conversationalGoldens().getFirst().turns());
    }

    @Test
    void addGoldensFromJsonFileDefaultsNullTurnsToEmptyLikeDeepEval() throws IOException {
        var file = tempDir.resolve("null-turns.json");
        Files.writeString(file, """
                [{"scenario": "Support chat", "turns": null}]
                """);
        var dataset = new EvaluationDataset();

        dataset.addGoldensFromJsonFile(file);

        assertEquals(List.of(), dataset.conversationalGoldens().getFirst().turns());
    }

    @Test
    void addGoldensFromJsonFileDefaultsFalseTurnsToEmptyLikeDeepEval() throws IOException {
        var file = tempDir.resolve("false-turns.json");
        Files.writeString(file, """
                [{"scenario": "Support chat", "turns": false}]
                """);
        var dataset = new EvaluationDataset();

        dataset.addGoldensFromJsonFile(file);

        assertEquals(List.of(), dataset.conversationalGoldens().getFirst().turns());
    }

    @Test
    void addGoldensFromCsvFileRejectsNonStringCustomColumnValuesLikeDeepEval() throws IOException {
        var file = tempDir.resolve("non-string-custom-columns.csv");
        Files.writeString(file, """
                input,custom_column_key_values
                Ask,"{""case_id"":123}"
                """);

        assertThrows(IllegalArgumentException.class, () -> new EvaluationDataset().addGoldensFromCsvFile(file));
    }

    @Test
    void saveAsJsonFileRejectsEmptyDataset() {
        assertThrows(IllegalStateException.class,
                () -> new EvaluationDataset().saveAsJsonFile(tempDir.resolve("empty.json")));
    }

    @Test
    void addTestCasesFromJsonFileImportsToolCalls() throws IOException {
        var file = tempDir.resolve("tools.json");
        Files.writeString(file, """
                [
                  {
                    "input": "Can I get a refund?",
                    "actual_output": "I searched the policy.",
                    "tools_called": [
                      {
                        "name": "PolicySearch",
                        "description": "Search policies",
                        "reasoning": "Refund question",
                        "input_parameters": {"query": "refund"},
                        "output": "30 days"
                      }
                    ],
                    "expected_tools": [
                      {"name": "PolicySearch", "input_parameters": {"query": "refund"}, "output": "30 days"}
                    ]
                  }
                ]
                """);
        var dataset = new EvaluationDataset();

        dataset.addTestCasesFromJsonFile(file, "input", "actual_output", "expected_output",
                "context", "retrieval_context", "tools_called", "expected_tools");

        var testCase = dataset.testCases().getFirst();
        var toolCalled = testCase.toolsCalled().getFirst();
        assertAll(
                () -> assertEquals(List.of(new ToolCall("PolicySearch", "Search policies", "Refund question",
                        Map.of("query", "refund"), "30 days")), testCase.toolsCalled()),
                () -> assertEquals("Search policies", toolCalled.description()),
                () -> assertEquals("Refund question", toolCalled.reasoning()),
                () -> assertEquals(List.of(new ToolCall("PolicySearch", Map.of("query", "refund"), "30 days")),
                        testCase.expectedTools()));
    }

    @Test
    void addTestCasesFromJsonFileRejectsNonStringToolNamesLikeDeepEval() throws IOException {
        var file = tempDir.resolve("non-string-tool-name.json");
        Files.writeString(file, """
                [
                  {
                    "input": "Can I get a refund?",
                    "actual_output": "I searched the policy.",
                    "tools_called": [{"name": 123}]
                  }
                ]
                """);

        assertThrows(IllegalArgumentException.class,
                () -> new EvaluationDataset().addTestCasesFromJsonFile(file, "input", "actual_output",
                        "expected_output", "context", "retrieval_context", "tools_called", "expected_tools"));
    }

    @Test
    void addTestCasesFromJsonFileRejectsNonStringToolDescriptionLikeDeepEval() throws IOException {
        var file = tempDir.resolve("non-string-tool-description.json");
        Files.writeString(file, """
                [
                  {
                    "input": "Can I get a refund?",
                    "actual_output": "I searched the policy.",
                    "tools_called": [{"name": "PolicySearch", "description": 123}]
                  }
                ]
                """);

        assertThrows(IllegalArgumentException.class,
                () -> new EvaluationDataset().addTestCasesFromJsonFile(file, "input", "actual_output",
                        "expected_output", "context", "retrieval_context", "tools_called", "expected_tools"));
    }

    @Test
    void addTestCasesFromJsonFileRejectsStringToolListsLikeDeepEval() throws IOException {
        var file = tempDir.resolve("string-tool-list.json");
        Files.writeString(file, """
                [
                  {
                    "input": "Can I get a refund?",
                    "actual_output": "I searched the policy.",
                    "tools_called": "[{\\"name\\":\\"PolicySearch\\"}]"
                  }
                ]
                """);

        assertThrows(IllegalArgumentException.class,
                () -> new EvaluationDataset().addTestCasesFromJsonFile(file, "input", "actual_output",
                        "expected_output", "context", "retrieval_context", "tools_called", "expected_tools"));
    }

    @Test
    void addTestCasesFromJsonFileRejectsNonStringRequiredTextFieldsLikeDeepEval() throws IOException {
        var file = tempDir.resolve("non-string-required-text.json");
        Files.writeString(file, """
                [{"input": "Ask", "actual_output": 123}]
                """);

        assertThrows(IllegalArgumentException.class,
                () -> new EvaluationDataset().addTestCasesFromJsonFile(file, "input", "actual_output",
                        "expected_output", "context", "retrieval_context"));
    }

    @Test
    void addTestCasesFromJsonFileRejectsNonStringOptionalTextFieldsLikeDeepEval() throws IOException {
        var file = tempDir.resolve("non-string-optional-text.json");
        Files.writeString(file, """
                [{"input": "Ask", "actual_output": "Ans", "expected_output": 123}]
                """);

        assertThrows(IllegalArgumentException.class,
                () -> new EvaluationDataset().addTestCasesFromJsonFile(file, "input", "actual_output",
                        "expected_output", "context", "retrieval_context"));
    }

    @Test
    void addTestCasesFromJsonFileImportsMcpFields() throws IOException {
        var file = tempDir.resolve("testcases-mcp.json");
        Files.writeString(file, """
                [
                  {
                    "input": "Find policy",
                    "actual_output": "Policy found",
                    "mcp_servers": [{"server_name": "policy"}],
                    "mcp_tools_called": [{"name": "mcp-search"}],
                    "mcp_resources_called": [{"uri": "file://policy"}],
                    "mcp_prompts_called": [{"name": "policy-prompt"}]
                  }
                ]
                """);
        var dataset = new EvaluationDataset();

        dataset.addTestCasesFromJsonFile(file, "input", "actual_output", "expected_output",
                "context", "retrieval_context");

        var testCase = dataset.testCases().getFirst();
        assertAll(
                () -> assertEquals("policy", testCase.mcpServers().getFirst().get("server_name")),
                () -> assertEquals("mcp-search", testCase.mcpToolsCalled().getFirst().get("name")),
                () -> assertEquals("file://policy", testCase.mcpResourcesCalled().getFirst().get("uri")),
                () -> assertEquals("policy-prompt", testCase.mcpPromptsCalled().getFirst().get("name")));
    }

    @Test
    void addTestCasesFromJsonFileParsesMcpFieldsFromJsonStrings() throws IOException {
        var file = tempDir.resolve("testcases-mcp-strings.json");
        Files.writeString(file, """
                [
                  {
                    "input": "Find policy",
                    "actual_output": "Policy found",
                    "mcp_servers": "[{\\"server_name\\":\\"policy\\"}]",
                    "mcp_tools_called": "[{\\"name\\":\\"mcp-search\\"}]",
                    "mcp_resources_called": "[{\\"uri\\":\\"file://policy\\"}]",
                    "mcp_prompts_called": "[{\\"name\\":\\"policy-prompt\\"}]"
                  }
                ]
                """);
        var dataset = new EvaluationDataset();

        dataset.addTestCasesFromJsonFile(file, "input", "actual_output", "expected_output",
                "context", "retrieval_context");

        var testCase = dataset.testCases().getFirst();
        assertAll(
                () -> assertEquals("policy", testCase.mcpServers().getFirst().get("server_name")),
                () -> assertEquals("mcp-search", testCase.mcpToolsCalled().getFirst().get("name")),
                () -> assertEquals("file://policy", testCase.mcpResourcesCalled().getFirst().get("uri")),
                () -> assertEquals("policy-prompt", testCase.mcpPromptsCalled().getFirst().get("name")));
    }

    @Test
    void saveAsJsonFileExportsSingleTurnTestCases() throws IOException {
        var file = tempDir.resolve("export.json");
        var dataset = new EvaluationDataset();
        dataset.addTestCase(LlmTestCase.builder("input")
                .actualOutput("actual")
                .expectedOutput("expected")
                .context(List.of("context"))
                .retrievalContext(List.of("retrieved"))
                .build());

        dataset.saveAsJsonFile(file);

        var imported = new EvaluationDataset();
        imported.addTestCasesFromJsonFile(file, "input", "actual_output", "expected_output",
                "context", "retrieval_context");
        var testCase = imported.testCases().getFirst();
        assertAll(
                () -> assertEquals("input", testCase.input()),
                () -> assertEquals("actual", testCase.actualOutput()),
                () -> assertEquals("expected", testCase.expectedOutput()),
                () -> assertEquals(List.of("context"), testCase.context()),
                () -> assertEquals(List.of("retrieved"), testCase.retrievalContext()));
    }

    @Test
    void jsonRoundTripsRetrievedContextDataMarkers() throws IOException {
        var file = tempDir.resolve("retrieved-context-data.json");
        var dataset = new EvaluationDataset();
        dataset.addGolden(Golden.builder("input")
                .actualOutput("actual")
                .retrievalContext(List.of(new RetrievedContextData("chunk", "source.md"), "plain"))
                .build());

        dataset.saveAsJsonFile(file);

        var raw = MAPPER.readTree(file.toFile());
        assertEquals("deepeval_source=source.md,deepeval_context=chunk",
                raw.get(0).get("retrieval_context").get(0).asText());

        var reloaded = new EvaluationDataset();
        reloaded.addGoldensFromJsonFile(file);
        Object retrieved = reloaded.goldens().getFirst().retrievalContext().getFirst();
        assertAll(
                () -> assertTrue(retrieved instanceof RetrievedContextData),
                () -> assertEquals(new RetrievedContextData("chunk", "source.md"), retrieved),
                () -> assertEquals("plain", reloaded.goldens().getFirst().retrievalContext().get(1)));
    }

    @Test
    void saveAsJsonFileExportsToolCalls() throws IOException {
        var file = tempDir.resolve("export-tools.json");
        var dataset = new EvaluationDataset();
        var tool = new ToolCall("PolicySearch", Map.of("query", "refund"), "30 days");
        dataset.addTestCase(LlmTestCase.builder("input")
                .actualOutput("actual")
                .toolsCalled(List.of(tool))
                .expectedTools(List.of(tool))
                .build());

        dataset.saveAsJsonFile(file);

        var imported = new EvaluationDataset();
        imported.addTestCasesFromJsonFile(file, "input", "actual_output", "expected_output",
                "context", "retrieval_context", "tools_called", "expected_tools");
        var testCase = imported.testCases().getFirst();
        assertAll(
                () -> assertEquals(List.of(tool), testCase.toolsCalled()),
                () -> assertEquals(List.of(tool), testCase.expectedTools()));
    }

    @Test
    void saveAsJsonFileUsesDeepEvalToolInputParametersAlias() throws IOException {
        var file = tempDir.resolve("export-tool-alias.json");
        var dataset = new EvaluationDataset();
        dataset.addGolden(Golden.builder("input")
                .toolsCalled(List.of(new ToolCall("PolicySearch", Map.of("query", "refund"), "30 days")))
                .build());

        dataset.saveAsJsonFile(file);

        var tool = MAPPER.readTree(file.toFile()).get(0).get("tools_called").get(0);
        assertAll(
                () -> assertTrue(tool.has("inputParameters")),
                () -> assertFalse(tool.has("input_parameters")));
    }

    @Test
    void saveAsJsonFileCanExcludeTestCases() throws IOException {
        var file = tempDir.resolve("goldens-only.json");
        var dataset = new EvaluationDataset();
        dataset.addTestCase(LlmTestCase.builder("case input")
                .actualOutput("case actual")
                .build());
        dataset.addGolden(Golden.builder("golden input")
                .actualOutput("golden actual")
                .build());

        dataset.saveAsJsonFile(file, false);

        var loaded = MAPPER.readTree(file.toFile());
        assertAll(
                () -> assertEquals(1, loaded.size()),
                () -> assertEquals("golden input", loaded.get(0).get("input").asText()));
    }

    @Test
    void saveAsJsonFileWritesNullStandardFieldsLikeDeepEval() throws IOException {
        var file = tempDir.resolve("golden-nulls.json");
        var dataset = new EvaluationDataset();
        dataset.addGolden(Golden.builder("input").build());

        dataset.saveAsJsonFile(file);

        var row = MAPPER.readTree(file.toFile()).get(0);
        assertAll(
                () -> assertTrue(row.has("actual_output")),
                () -> assertTrue(row.get("actual_output").isNull()),
                () -> assertTrue(row.has("tools_called")),
                () -> assertTrue(row.get("tools_called").isNull()));
    }

    @Test
    void saveAsJsonFileWritesEmptyToolListsAsNullLikeDeepEval() throws IOException {
        var file = tempDir.resolve("empty-tools-export.json");
        var dataset = new EvaluationDataset();
        dataset.addGolden(Golden.builder("input")
                .toolsCalled(List.of())
                .expectedTools(List.of())
                .build());

        dataset.saveAsJsonFile(file);

        var row = MAPPER.readTree(file.toFile()).get(0);
        assertAll(
                () -> assertTrue(row.get("tools_called").isNull()),
                () -> assertTrue(row.get("expected_tools").isNull()));
    }

    @Test
    void saveAsDispatchesFormatAndDefaultsToGoldensOnly() throws IOException {
        var dataset = new EvaluationDataset();
        dataset.addTestCase(LlmTestCase.builder("case input").actualOutput("case actual").build());
        dataset.addGolden(Golden.builder("golden input").actualOutput("golden actual").build());

        var path = dataset.saveAs("json", tempDir.resolve("exports"), "goldens");

        var loaded = MAPPER.readTree(path.toFile());
        assertAll(
                () -> assertEquals(tempDir.resolve("exports/goldens.json"), path),
                () -> assertTrue(Files.exists(path)),
                () -> assertEquals(1, loaded.size()),
                () -> assertEquals("golden input", loaded.get(0).get("input").asText()));
    }

    @Test
    void saveAsCanIncludeTestCasesAndRejectsInvalidFormats() throws IOException {
        var dataset = new EvaluationDataset();
        dataset.addTestCase(LlmTestCase.builder("case input").actualOutput("case actual").build());
        dataset.addGolden(Golden.builder("golden input").actualOutput("golden actual").build());

        var path = dataset.saveAs("jsonl", tempDir, "all-data", true);

        assertAll(
                () -> assertEquals(tempDir.resolve("all-data.jsonl"), path),
                () -> assertEquals(2, Files.readAllLines(path).size()),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> dataset.saveAs("xml", tempDir, "bad")));
    }

    @Test
    void saveAsIncludesGoldensBeforeTestCasesLikeDeepEval() throws IOException {
        var dataset = new EvaluationDataset();
        dataset.addTestCase(LlmTestCase.builder("case input").actualOutput("case actual").build());
        dataset.addGolden(Golden.builder("golden input").actualOutput("golden actual").build());

        var path = dataset.saveAs("json", tempDir, "ordered", true);

        var loaded = MAPPER.readTree(path.toFile());
        assertAll(
                () -> assertEquals("golden input", loaded.get(0).get("input").asText()),
                () -> assertEquals("case input", loaded.get(1).get("input").asText()));
    }

    @Test
    void saveAsCanOmitFileNameLikeDeepEval() {
        var dataset = new EvaluationDataset();
        dataset.addGolden(Golden.builder("input").actualOutput("actual").build());

        var path = dataset.saveAs("csv", tempDir);

        assertAll(
                () -> assertTrue(Files.exists(path)),
                () -> assertEquals(tempDir, path.getParent()),
                () -> assertTrue(path.getFileName().toString().endsWith(".csv")));
    }

    @Test
    void saveAsCanOmitFileNameAndIncludeTestCases() throws IOException {
        var dataset = new EvaluationDataset();
        dataset.addGolden(Golden.builder("golden input").actualOutput("golden actual").build());
        dataset.addTestCase(LlmTestCase.builder("case input").actualOutput("case actual").build());

        var path = dataset.saveAs("json", tempDir, true);

        assertAll(
                () -> assertTrue(Files.exists(path)),
                () -> assertEquals(tempDir, path.getParent()),
                () -> assertTrue(path.getFileName().toString().endsWith(".json")),
                () -> assertEquals(2, MAPPER.readTree(path.toFile()).size()));
    }

    @Test
    void saveMethodsCreateMissingParentDirectories() {
        var dataset = new EvaluationDataset();
        dataset.addGolden(Golden.builder("input").actualOutput("actual").build());

        var json = tempDir.resolve("nested/json/export.json");
        var jsonl = tempDir.resolve("nested/jsonl/export.jsonl");
        var csv = tempDir.resolve("nested/csv/export.csv");

        dataset.saveAsJsonFile(json);
        dataset.saveAsJsonlFile(jsonl);
        dataset.saveAsCsvFile(csv);

        assertAll(
                () -> assertTrue(Files.exists(json)),
                () -> assertTrue(Files.exists(jsonl)),
                () -> assertTrue(Files.exists(csv)));
    }

    @Test
    void addGoldensFromJsonFileImportsSingleTurnGoldens() throws IOException {
        var file = tempDir.resolve("goldens.json");
        Files.writeString(file, """
                [
                  {
                    "input": "Ask",
                    "actual_output": "Ans",
                    "expected_output": "Ans",
                    "context": ["ctx"],
                    "retrieval_context": ["rctx"],
                    "tools_called": [{"name": "search"}],
                    "expected_tools": [{"name": "finalize"}],
                    "additional_metadata": {"k": "v"},
                    "custom_column_key_values": {"col": "val"},
                    "name": "n",
                    "comments": "c",
                    "source_file": "src.txt"
                  }
                ]
                """);
        var dataset = new EvaluationDataset();

        dataset.addGoldensFromJsonFile(file);

        var golden = dataset.goldens().getFirst();
        assertAll(
                () -> assertEquals("Ask", golden.input()),
                () -> assertEquals("Ans", golden.actualOutput()),
                () -> assertEquals("Ans", golden.expectedOutput()),
                () -> assertEquals(List.of("ctx"), golden.context()),
                () -> assertEquals(List.of("rctx"), golden.retrievalContext()),
                () -> assertEquals("search", golden.toolsCalled().getFirst().name()),
                () -> assertEquals("finalize", golden.expectedTools().getFirst().name()),
                () -> assertEquals(Map.of("k", "v"), golden.additionalMetadata()),
                () -> assertEquals(Map.of("col", "val"), golden.customColumnKeyValues()),
                () -> assertEquals("n", golden.name()),
                () -> assertEquals("c", golden.comments()),
                () -> assertEquals("src.txt", golden.sourceFile()));
    }

    @Test
    void addGoldensFromJsonFileAcceptsCamelCaseAliasesForDefaultSingleTurnKeys() throws IOException {
        var file = tempDir.resolve("camel-case-goldens.json");
        Files.writeString(file, """
                [
                  {
                    "input": "Ask",
                    "actualOutput": "Ans",
                    "expectedOutput": "Ans",
                    "retrievalContext": ["rctx"],
                    "toolsCalled": [{"name": "search"}],
                    "expectedTools": [{"name": "finalize"}],
                    "customColumnKeyValues": {"col": "val"},
                    "sourceFile": "src.txt"
                  }
                ]
                """);
        var dataset = new EvaluationDataset();

        dataset.addGoldensFromJsonFile(file);

        var golden = dataset.goldens().getFirst();
        assertAll(
                () -> assertEquals("Ans", golden.actualOutput()),
                () -> assertEquals("Ans", golden.expectedOutput()),
                () -> assertEquals(List.of("rctx"), golden.retrievalContext()),
                () -> assertEquals(List.of(new ToolCall("search")), golden.toolsCalled()),
                () -> assertEquals(List.of(new ToolCall("finalize")), golden.expectedTools()),
                () -> assertEquals(Map.of("col", "val"), golden.customColumnKeyValues()),
                () -> assertEquals("src.txt", golden.sourceFile()));
    }

    @Test
    void addGoldensFromJsonFileRejectsStringContextLikeDeepEval() throws IOException {
        var file = tempDir.resolve("string-context-golden.json");
        Files.writeString(file, """
                [{"input": "Ask", "context": "ctx one|ctx two"}]
                """);

        assertThrows(IllegalArgumentException.class, () -> new EvaluationDataset().addGoldensFromJsonFile(file));
    }

    @Test
    void addGoldensFromJsonFileImportsCustomSingleTurnKeys() throws IOException {
        var file = tempDir.resolve("custom-goldens.json");
        Files.writeString(file, """
                [
                  {
                    "prompt": "Ask",
                    "actual": "Ans",
                    "expected": "Ans",
                    "ctx": ["ctx"],
                    "retrieved": ["rctx"],
                    "called": [{"name": "search"}],
                    "wanted": [{"name": "finalize"}],
                    "meta": {"k": "v"},
                    "columns": {"col": "val"},
                    "label": "n",
                    "note": "c",
                    "origin": "src.txt"
                  }
                ]
                """);
        var dataset = new EvaluationDataset();

        dataset.addGoldensFromJsonFile(file, "prompt", "actual", "expected", "ctx", "retrieved",
                "called", "wanted", "meta", "columns", "label", "note", "origin");

        var golden = dataset.goldens().getFirst();
        assertAll(
                () -> assertEquals("Ask", golden.input()),
                () -> assertEquals("Ans", golden.actualOutput()),
                () -> assertEquals("Ans", golden.expectedOutput()),
                () -> assertEquals(List.of("ctx"), golden.context()),
                () -> assertEquals(List.of("rctx"), golden.retrievalContext()),
                () -> assertEquals("search", golden.toolsCalled().getFirst().name()),
                () -> assertEquals("finalize", golden.expectedTools().getFirst().name()),
                () -> assertEquals(Map.of("k", "v"), golden.additionalMetadata()),
                () -> assertEquals(Map.of("col", "val"), golden.customColumnKeyValues()),
                () -> assertEquals("n", golden.name()),
                () -> assertEquals("c", golden.comments()),
                () -> assertEquals("src.txt", golden.sourceFile()));
    }

    @Test
    void saveAsJsonFileExportsGoldenMetadata() throws IOException {
        var file = tempDir.resolve("export-golden.json");
        var dataset = new EvaluationDataset();
        dataset.addGolden(Golden.builder("Ask")
                .actualOutput("Ans")
                .expectedOutput("Ans")
                .additionalMetadata(Map.of("k", "v"))
                .customColumnKeyValues(Map.of("col", "val"))
                .build());

        dataset.saveAsJsonFile(file);

        var imported = new EvaluationDataset();
        imported.addGoldensFromJsonFile(file);
        var golden = imported.goldens().getFirst();
        assertAll(
                () -> assertEquals(Map.of("k", "v"), golden.additionalMetadata()),
                () -> assertEquals(Map.of("col", "val"), golden.customColumnKeyValues()));
    }

    @Test
    void saveAsJsonFileExportsConversationalGoldens() throws IOException {
        var file = tempDir.resolve("conversational.json");
        var dataset = new EvaluationDataset();
        dataset.addGolden(ConversationalGolden.builder("Book a flight to Tokyo")
                .expectedOutcome("User gets flight options")
                .userDescription("User is trying to find flights")
                .context(List.of("Flights", "Travel"))
                .turns(List.of(
                        Turn.builder("user", "Find me a flight to Tokyo")
                                .mcpToolsCalled(List.of(Map.of("name", "mcp-search")))
                                .mcpResourcesCalled(List.of(Map.of("uri", "file://flights")))
                                .mcpPromptsCalled(List.of(Map.of("name", "travel-prompt")))
                                .build(),
                        new Turn("assistant", "Here are some flight options to Tokyo")))
                .name("Name")
                .comments("Comment")
                .build());

        dataset.saveAsJsonFile(file);

        var row = MAPPER.readTree(file.toFile()).get(0);
        assertAll(
                () -> assertEquals("Book a flight to Tokyo", row.get("scenario").asText()),
                () -> assertEquals("User gets flight options", row.get("expected_outcome").asText()),
                () -> assertEquals("User is trying to find flights", row.get("user_description").asText()),
                () -> assertEquals("Name", row.get("name").asText()),
                () -> assertEquals("Comment", row.get("comments").asText()),
                () -> assertEquals("user", row.get("turns").get(0).get("role").asText()),
                () -> assertEquals("Find me a flight to Tokyo", row.get("turns").get(0).get("content").asText()),
                () -> assertEquals("mcp-search",
                        row.get("turns").get(0).get("mcp_tools_called").get(0).get("name").asText()),
                () -> assertEquals("file://flights",
                        row.get("turns").get(0).get("mcp_resources_called").get(0).get("uri").asText()),
                () -> assertEquals("travel-prompt",
                        row.get("turns").get(0).get("mcp_prompts_called").get(0).get("name").asText()),
                () -> assertEquals("assistant", row.get("turns").get(1).get("role").asText()));
    }

    @Test
    void saveAsJsonFileWritesNullTurnFieldsLikeDeepEval() throws IOException {
        var file = tempDir.resolve("conversational-null-turn-fields.json");
        var dataset = new EvaluationDataset();
        dataset.addGolden(ConversationalGolden.builder("Book a flight")
                .turns(List.of(new Turn("user", "Find flights")))
                .build());

        dataset.saveAsJsonFile(file);

        var turn = MAPPER.readTree(file.toFile()).get(0).get("turns").get(0);
        assertAll(
                () -> assertTrue(turn.get("user_id").isNull()),
                () -> assertTrue(turn.get("retrieval_context").isNull()),
                () -> assertTrue(turn.get("tools_called").isNull()),
                () -> assertTrue(turn.get("mcp_tools_called").isNull()),
                () -> assertTrue(turn.get("mcp_resources_called").isNull()),
                () -> assertTrue(turn.get("mcp_prompts_called").isNull()),
                () -> assertTrue(turn.get("metadata").isNull()));
    }

    @Test
    void saveAsJsonFileWritesEmptyConversationalTurnsAsNullLikeDeepEval() throws IOException {
        var file = tempDir.resolve("conversational-empty-turns.json");
        var dataset = new EvaluationDataset();
        dataset.addGolden(ConversationalGolden.builder("Book a flight")
                .turns(List.of())
                .build());

        dataset.saveAsJsonFile(file);

        var row = MAPPER.readTree(file.toFile()).get(0);
        assertTrue(row.get("turns").isNull());
    }

    @Test
    void addGoldensFromJsonFileImportsConversationalGoldens() throws IOException {
        var file = tempDir.resolve("conversational-import.json");
        Files.writeString(file, """
                [
                  {
                    "scenario": "Book a flight",
                    "expected_outcome": "User gets options",
                    "user_description": "Traveler",
                    "context": ["travel"],
                    "turns": [
                      {
                        "role": "user",
                        "content": "Find flights",
                        "user_id": "u1",
                        "retrieval_context": ["r"],
                        "tools_called": [{"name": "search"}],
                        "mcp_tools_called": [{"name": "mcp-search"}],
                        "mcp_resources_called": [{"uri": "file://policy"}],
                        "mcp_prompts_called": [{"name": "prompt"}],
                        "additional_metadata": {"k": "v"}
                      }
                    ],
                    "additional_metadata": {"gk": "gv"},
                    "custom_column_key_values": {"col": "val"},
                    "name": "trip",
                    "comments": "c"
                  }
                ]
                """);
        var dataset = new EvaluationDataset();

        dataset.addGoldensFromJsonFile(file);

        var golden = dataset.conversationalGoldens().getFirst();
        var turn = golden.turns().getFirst();
        assertAll(
                () -> assertEquals(true, dataset.multiTurn()),
                () -> assertEquals("Book a flight", golden.scenario()),
                () -> assertEquals("User gets options", golden.expectedOutcome()),
                () -> assertEquals("Traveler", golden.userDescription()),
                () -> assertEquals(List.of("travel"), golden.context()),
                () -> assertEquals(Map.of("gk", "gv"), golden.additionalMetadata()),
                () -> assertEquals(Map.of("col", "val"), golden.customColumnKeyValues()),
                () -> assertEquals("trip", golden.name()),
                () -> assertEquals("c", golden.comments()),
                () -> assertEquals("user", turn.role()),
                () -> assertEquals("Find flights", turn.content()),
                () -> assertEquals("u1", turn.userId()),
                () -> assertEquals(List.of("r"), turn.retrievalContext()),
                () -> assertEquals("search", turn.toolsCalled().getFirst().name()),
                () -> assertEquals("mcp-search", turn.mcpToolsCalled().getFirst().get("name")),
                () -> assertEquals("file://policy", turn.mcpResourcesCalled().getFirst().get("uri")),
                () -> assertEquals("prompt", turn.mcpPromptsCalled().getFirst().get("name")),
                () -> assertEquals(Map.of("k", "v"), turn.metadata()));
    }

    @Test
    void addGoldensFromJsonFileAcceptsCamelCaseAliasesForDefaultConversationalKeys() throws IOException {
        var file = tempDir.resolve("camel-case-conversational-import.json");
        Files.writeString(file, """
                [
                  {
                    "scenario": "Book a flight",
                    "expectedOutcome": "User gets options",
                    "userDescription": "Traveler",
                    "turns": [{"role": "user", "content": "Find flights"}],
                    "metadata": {"gk": "gv"},
                    "customColumnKeyValues": {"col": "val"}
                  }
                ]
                """);
        var dataset = new EvaluationDataset();

        dataset.addGoldensFromJsonFile(file);

        var golden = dataset.conversationalGoldens().getFirst();
        assertAll(
                () -> assertEquals("User gets options", golden.expectedOutcome()),
                () -> assertEquals("Traveler", golden.userDescription()),
                () -> assertEquals(Map.of("gk", "gv"), golden.additionalMetadata()),
                () -> assertEquals(Map.of("col", "val"), golden.customColumnKeyValues()));
    }

    @Test
    void structuredTurnsUseMetadataKeyLikeDeepEval() throws IOException {
        var file = tempDir.resolve("turn-metadata.json");
        Files.writeString(file, """
                [
                  {
                    "scenario": "Book a flight",
                    "turns": [
                      {"role": "user", "content": "Find flights", "metadata": {"k": "v"}}
                    ]
                  }
                ]
                """);
        var imported = new EvaluationDataset();

        imported.addGoldensFromJsonFile(file);

        var loadedTurn = imported.conversationalGoldens().getFirst().turns().getFirst();
        var exported = tempDir.resolve("turn-metadata-export.json");
        var exportedDataset = new EvaluationDataset();
        exportedDataset.addGolden(ConversationalGolden.builder("Book a flight")
                .turns(List.of(Turn.builder("user", "Find flights")
                        .metadata(Map.of("k", "v"))
                        .build()))
                .build());
        exportedDataset.saveAsJsonFile(exported);
        var exportedTurn = MAPPER.readTree(exported.toFile()).get(0).get("turns").get(0);

        assertAll(
                () -> assertEquals(Map.of("k", "v"), loadedTurn.metadata()),
                () -> assertEquals("v", exportedTurn.get("metadata").get("k").asText()));
    }

    @Test
    void structuredTurnsAcceptCamelCaseAliases() throws IOException {
        var file = tempDir.resolve("turn-camel-case-aliases.json");
        Files.writeString(file, """
                [
                  {
                    "scenario": "Book a flight",
                    "turns": [
                      {
                        "role": "user",
                        "content": "Find flights",
                        "userId": "u1",
                        "retrievalContext": ["r"],
                        "toolsCalled": [{"name": "search"}],
                        "mcpToolsCalled": [{"name": "mcp-search"}],
                        "mcpResourcesCalled": [{"uri": "file://policy"}],
                        "mcpPromptsCalled": [{"name": "prompt"}],
                        "additionalMetadata": {"k": "v"}
                      }
                    ]
                  }
                ]
                """);
        var dataset = new EvaluationDataset();

        dataset.addGoldensFromJsonFile(file);

        var turn = dataset.conversationalGoldens().getFirst().turns().getFirst();
        assertAll(
                () -> assertEquals("u1", turn.userId()),
                () -> assertEquals(List.of("r"), turn.retrievalContext()),
                () -> assertEquals("search", turn.toolsCalled().getFirst().name()),
                () -> assertEquals("mcp-search", turn.mcpToolsCalled().getFirst().get("name")),
                () -> assertEquals("file://policy", turn.mcpResourcesCalled().getFirst().get("uri")),
                () -> assertEquals("prompt", turn.mcpPromptsCalled().getFirst().get("name")),
                () -> assertEquals(Map.of("k", "v"), turn.metadata()));
    }

    @Test
    void addGoldensFromJsonFileImportsCustomConversationalKeys() throws IOException {
        var file = tempDir.resolve("custom-conversational-import.json");
        Files.writeString(file, """
                [
                  {
                    "situation": "Book a flight",
                    "outcome": "User gets options",
                    "user": "Traveler",
                    "ctx": ["travel"],
                    "dialogue": [
                      {"role": "user", "content": "Find flights"}
                    ],
                    "meta": {"gk": "gv"},
                    "columns": {"col": "val"},
                    "label": "trip",
                    "note": "c"
                  }
                ]
                """);
        var dataset = new EvaluationDataset();

        dataset.addGoldensFromJsonFile(file, "input", "actual_output", "expected_output", "ctx",
                "retrieval_context", "tools_called", "expected_tools", "meta", "columns",
                "label", "note", "source_file", "situation", "dialogue", "outcome", "user");

        var golden = dataset.conversationalGoldens().getFirst();
        assertAll(
                () -> assertEquals(true, dataset.multiTurn()),
                () -> assertEquals("Book a flight", golden.scenario()),
                () -> assertEquals("User gets options", golden.expectedOutcome()),
                () -> assertEquals("Traveler", golden.userDescription()),
                () -> assertEquals(List.of("travel"), golden.context()),
                () -> assertEquals(Map.of("gk", "gv"), golden.additionalMetadata()),
                () -> assertEquals(Map.of("col", "val"), golden.customColumnKeyValues()),
                () -> assertEquals("trip", golden.name()),
                () -> assertEquals("c", golden.comments()),
                () -> assertEquals("user", golden.turns().getFirst().role()),
                () -> assertEquals("Find flights", golden.turns().getFirst().content()));
    }

    @Test
    void addGoldensFromJsonFileRejectsMixedSingleAndConversationalGoldens() throws IOException {
        var file = tempDir.resolve("mixed.json");
        Files.writeString(file, """
                [
                  {"input": "Ask", "actual_output": "Ans"},
                  {"scenario": "Book a flight", "turns": [{"role": "user", "content": "hi"}]}
                ]
                """);

        assertThrows(IllegalArgumentException.class, () -> new EvaluationDataset().addGoldensFromJsonFile(file));
    }

    @Test
    void jsonlRoundTripsSingleTurnGoldens() throws IOException {
        var file = tempDir.resolve("single.jsonl");
        var dataset = new EvaluationDataset();
        dataset.addGolden(Golden.builder("Ask")
                .actualOutput("Ans")
                .expectedOutput("Ans")
                .context(List.of("ctx"))
                .retrievalContext(List.of("rctx"))
                .toolsCalled(List.of(new ToolCall("search")))
                .expectedTools(List.of(new ToolCall("finalize")))
                .additionalMetadata(Map.of("k", "v"))
                .customColumnKeyValues(Map.of("col", "val"))
                .build());

        dataset.saveAsJsonlFile(file);

        var loaded = new EvaluationDataset();
        loaded.addGoldensFromJsonlFile(file);
        var golden = loaded.goldens().getFirst();
        assertAll(
                () -> assertEquals("Ask", golden.input()),
                () -> assertEquals(List.of("ctx"), golden.context()),
                () -> assertEquals(List.of("rctx"), golden.retrievalContext()),
                () -> assertEquals("search", golden.toolsCalled().getFirst().name()),
                () -> assertEquals("finalize", golden.expectedTools().getFirst().name()),
                () -> assertEquals(Map.of("k", "v"), golden.additionalMetadata()),
                () -> assertEquals(Map.of("col", "val"), golden.customColumnKeyValues()));
    }

    @Test
    void saveAsJsonlFileWritesSingleTurnShapeLikeDeepEval() throws IOException {
        var file = tempDir.resolve("single-shape.jsonl");
        var dataset = new EvaluationDataset();
        dataset.addGolden(Golden.builder("Ask")
                .actualOutput("Ans")
                .context(List.of("ctx one", "ctx two"))
                .retrievalContext(List.of("rctx one", "rctx two"))
                .name("n")
                .comments("c")
                .sourceFile("src.txt")
                .build());

        dataset.saveAsJsonlFile(file);

        var row = MAPPER.readTree(Files.readString(file));
        assertAll(
                () -> assertEquals("ctx one|ctx two", row.get("context").asText()),
                () -> assertEquals("rctx one|rctx two", row.get("retrieval_context").asText()),
                () -> assertEquals(false, row.has("name")),
                () -> assertEquals(false, row.has("comments")),
                () -> assertEquals(false, row.has("source_file")));
    }

    @Test
    void saveAsJsonlFileCanExcludeTestCases() throws IOException {
        var file = tempDir.resolve("goldens-only.jsonl");
        var dataset = new EvaluationDataset();
        dataset.addTestCase(LlmTestCase.builder("case input")
                .actualOutput("case actual")
                .build());
        dataset.addGolden(Golden.builder("golden input")
                .actualOutput("golden actual")
                .build());

        dataset.saveAsJsonlFile(file, false);

        var rows = Files.readAllLines(file);
        assertAll(
                () -> assertEquals(1, rows.size()),
                () -> assertEquals("golden input", MAPPER.readTree(rows.getFirst()).get("input").asText()));
    }

    @Test
    void addGoldensFromJsonlFileImportsCustomSingleTurnKeys() throws IOException {
        var file = tempDir.resolve("custom-single.jsonl");
        Files.writeString(file, """
                {"prompt":"Ask","actual":"Ans","expected":"Ans","ctx":["ctx"],"retrieved":["rctx"],"called":[{"name":"search"}],"wanted":[{"name":"finalize"}],"meta":{"k":"v"},"columns":{"col":"val"},"label":"n","note":"c","origin":"src.txt"}
                """);
        var dataset = new EvaluationDataset();

        dataset.addGoldensFromJsonlFile(file, "prompt", "actual", "expected", "ctx", "retrieved",
                "called", "wanted", "meta", "columns", "label", "note", "origin");

        var golden = dataset.goldens().getFirst();
        assertAll(
                () -> assertEquals("Ask", golden.input()),
                () -> assertEquals("Ans", golden.actualOutput()),
                () -> assertEquals("Ans", golden.expectedOutput()),
                () -> assertEquals(List.of("ctx"), golden.context()),
                () -> assertEquals(List.of("rctx"), golden.retrievalContext()),
                () -> assertEquals("search", golden.toolsCalled().getFirst().name()),
                () -> assertEquals("finalize", golden.expectedTools().getFirst().name()),
                () -> assertEquals(Map.of("k", "v"), golden.additionalMetadata()),
                () -> assertEquals(Map.of("col", "val"), golden.customColumnKeyValues()),
                () -> assertEquals("n", golden.name()),
                () -> assertEquals("c", golden.comments()),
                () -> assertEquals("src.txt", golden.sourceFile()));
    }

    @Test
    void addGoldensFromJsonlFileSplitsStringContexts() throws IOException {
        var file = tempDir.resolve("string-context.jsonl");
        Files.writeString(file, """
                {"input":"Ask","actual_output":"Ans","context":"ctx one|ctx two","retrieval_context":"rctx one|rctx two"}
                """);
        var dataset = new EvaluationDataset();

        dataset.addGoldensFromJsonlFile(file);

        var golden = dataset.goldens().getFirst();
        assertAll(
                () -> assertEquals(List.of("ctx one", "ctx two"), golden.context()),
                () -> assertEquals(List.of("rctx one", "rctx two"), golden.retrievalContext()));
    }

    @Test
    void addGoldensFromJsonlFileSplitsStringContextsWithCustomDelimiters() throws IOException {
        var file = tempDir.resolve("string-context-delimiters.jsonl");
        Files.writeString(file, """
                {"input":"Ask","actual_output":"Ans","context":"ctx one;ctx two","retrieval_context":"rctx one/rctx two"}
                """);
        var dataset = new EvaluationDataset();

        dataset.addGoldensFromJsonlFile(file, "input", "actual_output", "expected_output", "context", ";",
                "retrieval_context", "/", "tools_called", "expected_tools", "additional_metadata",
                "custom_column_key_values", "name", "comments", "source_file");

        var golden = dataset.goldens().getFirst();
        assertAll(
                () -> assertEquals(List.of("ctx one", "ctx two"), golden.context()),
                () -> assertEquals(List.of("rctx one", "rctx two"), golden.retrievalContext()));
    }

    @Test
    void addGoldensFromJsonlFileTreatsBlankScenarioAsSingleTurn() throws IOException {
        var file = tempDir.resolve("blank-scenario.jsonl");
        Files.writeString(file, """
                {"scenario":"","input":"Ask","actual_output":"Ans"}
                """);
        var dataset = new EvaluationDataset();

        dataset.addGoldensFromJsonlFile(file);

        assertAll(
                () -> assertEquals(false, dataset.multiTurn()),
                () -> assertEquals("Ask", dataset.goldens().getFirst().input()));
    }

    @Test
    void addGoldensFromJsonlFileParsesToolListsFromJsonStrings() throws IOException {
        var file = tempDir.resolve("tools-as-strings.jsonl");
        Files.writeString(file, """
                {"input":"Ask","tools_called":"[{\\"name\\":\\"search\\",\\"output\\":\\"ok\\"}]","expected_tools":"[{\\"name\\":\\"finalize\\"}]"}
                """);
        var dataset = new EvaluationDataset();

        dataset.addGoldensFromJsonlFile(file);

        var golden = dataset.goldens().getFirst();
        assertAll(
                () -> assertEquals(List.of(new ToolCall("search", null, "ok")), golden.toolsCalled()),
                () -> assertEquals(List.of(new ToolCall("finalize")), golden.expectedTools()));
    }

    @Test
    void addGoldensFromJsonlFileParsesToolStringsWithTrailingCommasLikeDeepEval() throws IOException {
        var file = tempDir.resolve("tools-as-strings-trailing-comma.jsonl");
        Files.writeString(file, """
                {"input":"Ask","tools_called":"[{\\"name\\":\\"search\\"},]"}
                """);
        var dataset = new EvaluationDataset();

        dataset.addGoldensFromJsonlFile(file);

        assertEquals(List.of(new ToolCall("search")), dataset.goldens().getFirst().toolsCalled());
    }

    @Test
    void addGoldensFromJsonlFileTreatsEmptyToolListsAsNullLikeDeepEval() throws IOException {
        var file = tempDir.resolve("empty-tools.jsonl");
        Files.writeString(file, """
                {"input":"Ask","tools_called":[],"expected_tools":[]}
                """);
        var dataset = new EvaluationDataset();

        dataset.addGoldensFromJsonlFile(file);

        var golden = dataset.goldens().getFirst();
        assertAll(
                () -> assertEquals(null, golden.toolsCalled()),
                () -> assertEquals(null, golden.expectedTools()));
    }

    @Test
    void addGoldensFromJsonlFileTreatsFalseToolListsAsNullLikeDeepEval() throws IOException {
        var file = tempDir.resolve("false-tools.jsonl");
        Files.writeString(file, """
                {"input":"Ask","tools_called":false,"expected_tools":false}
                """);
        var dataset = new EvaluationDataset();

        dataset.addGoldensFromJsonlFile(file);

        var golden = dataset.goldens().getFirst();
        assertAll(
                () -> assertEquals(null, golden.toolsCalled()),
                () -> assertEquals(null, golden.expectedTools()));
    }

    @Test
    void jsonlRoundTripsConversationalGoldens() throws IOException {
        var file = tempDir.resolve("conversation.jsonl");
        var dataset = new EvaluationDataset();
        dataset.addGolden(ConversationalGolden.builder("Book a flight")
                .expectedOutcome("User gets options")
                .userDescription("Traveler")
                .context(List.of("travel"))
                .turns(List.of(Turn.builder("user", "Find flights")
                        .userId("u1")
                        .retrievalContext(List.of("r"))
                        .toolsCalled(List.of(new ToolCall("search")))
                        .build()))
                .additionalMetadata(Map.of("k", "v"))
                .customColumnKeyValues(Map.of("col", "val"))
                .build());

        dataset.saveAsJsonlFile(file);

        var loaded = new EvaluationDataset();
        loaded.addGoldensFromJsonlFile(file);
        var golden = loaded.conversationalGoldens().getFirst();
        assertAll(
                () -> assertEquals("Book a flight", golden.scenario()),
                () -> assertEquals(List.of("travel"), golden.context()),
                () -> assertEquals("u1", golden.turns().getFirst().userId()),
                () -> assertEquals("search", golden.turns().getFirst().toolsCalled().getFirst().name()),
                () -> assertEquals(Map.of("k", "v"), golden.additionalMetadata()),
                () -> assertEquals(Map.of("col", "val"), golden.customColumnKeyValues()));
    }

    @Test
    void addGoldensFromJsonlFileParsesTurnsFromJsonStringLikeDeepEval() throws IOException {
        var file = tempDir.resolve("turns-as-string.jsonl");
        Files.writeString(file, """
                {"scenario":"Support chat","turns":"[{\\"role\\":\\"user\\",\\"content\\":\\"hi\\"}]"}
                """);
        var dataset = new EvaluationDataset();

        dataset.addGoldensFromJsonlFile(file);

        var turn = dataset.conversationalGoldens().getFirst().turns().getFirst();
        assertAll(
                () -> assertEquals("user", turn.role()),
                () -> assertEquals("hi", turn.content()));
    }

    @Test
    void csvRoundTripsSingleTurnGoldens() throws IOException {
        var file = tempDir.resolve("single.csv");
        var dataset = new EvaluationDataset();
        dataset.addGolden(Golden.builder("Ask")
                .actualOutput("Ans")
                .expectedOutput("Ans")
                .context(List.of("ctx", "extra"))
                .retrievalContext(List.of("rctx", "more"))
                .toolsCalled(List.of(new ToolCall("search", Map.of("query", "refund"), "30 days")))
                .expectedTools(List.of(new ToolCall("finalize")))
                .additionalMetadata(Map.of("k", "v"))
                .customColumnKeyValues(Map.of("col", "val"))
                .name("n")
                .comments("c")
                .sourceFile("src.txt")
                .build());

        dataset.saveAsCsvFile(file);

        var loaded = new EvaluationDataset();
        loaded.addGoldensFromCsvFile(file);
        var golden = loaded.goldens().getFirst();
        assertAll(
                () -> assertEquals("Ask", golden.input()),
                () -> assertEquals("Ans", golden.actualOutput()),
                () -> assertEquals("Ans", golden.expectedOutput()),
                () -> assertEquals(List.of("ctx", "extra"), golden.context()),
                () -> assertEquals(List.of("rctx", "more"), golden.retrievalContext()),
                () -> assertEquals(List.of(new ToolCall("search", Map.of("query", "refund"), "30 days")),
                        golden.toolsCalled()),
                () -> assertEquals(List.of(new ToolCall("finalize")), golden.expectedTools()),
                () -> assertEquals(Map.of("k", "v"), golden.additionalMetadata()),
                () -> assertEquals(Map.of("col", "val"), golden.customColumnKeyValues()),
                () -> assertEquals("n", golden.name()),
                () -> assertEquals("c", golden.comments()),
                () -> assertEquals("src.txt", golden.sourceFile()));
    }

    @Test
    void addGoldensFromCsvFileAcceptsCamelCaseAliasesForDefaultSingleTurnColumns() throws IOException {
        var file = tempDir.resolve("camel-case-single.csv");
        Files.writeString(file, """
                input,actualOutput,expectedOutput,retrievalContext,toolsCalled,expectedTools,metadata,customColumnKeyValues,sourceFile
                Ask,Ans,Ans,rctx,search,finalize,"{""k"":""v""}","{""col"":""val""}",src.txt
                """);
        var dataset = new EvaluationDataset();

        dataset.addGoldensFromCsvFile(file);

        var golden = dataset.goldens().getFirst();
        assertAll(
                () -> assertEquals("Ans", golden.actualOutput()),
                () -> assertEquals("Ans", golden.expectedOutput()),
                () -> assertEquals(List.of("rctx"), golden.retrievalContext()),
                () -> assertEquals(List.of(new ToolCall("search")), golden.toolsCalled()),
                () -> assertEquals(List.of(new ToolCall("finalize")), golden.expectedTools()),
                () -> assertEquals(Map.of("k", "v"), golden.additionalMetadata()),
                () -> assertEquals(Map.of("col", "val"), golden.customColumnKeyValues()),
                () -> assertEquals("src.txt", golden.sourceFile()));
    }

    @Test
    void saveAsCsvFileCanExcludeTestCases() throws IOException {
        var file = tempDir.resolve("goldens-only.csv");
        var dataset = new EvaluationDataset();
        dataset.addTestCase(LlmTestCase.builder("case input")
                .actualOutput("case actual")
                .build());
        dataset.addGolden(Golden.builder("golden input")
                .actualOutput("golden actual")
                .build());

        dataset.saveAsCsvFile(file, false);

        var rows = Files.readAllLines(file);
        assertAll(
                () -> assertEquals(2, rows.size()),
                () -> assertEquals("golden input", rows.get(1).split(",", -1)[0]));
    }

    @Test
    void addGoldensFromCsvFileImportsCustomSingleTurnColumns() throws IOException {
        var file = tempDir.resolve("custom-single.csv");
        Files.writeString(file, """
                prompt,actual,expected,ctx,retrieved
                Ask,Ans,Ans,ctx one|ctx two,rctx
                """);
        var dataset = new EvaluationDataset();

        dataset.addGoldensFromCsvFile(file, "prompt", "actual", "expected", "ctx", "retrieved");

        var golden = dataset.goldens().getFirst();
        assertAll(
                () -> assertEquals("Ask", golden.input()),
                () -> assertEquals("Ans", golden.actualOutput()),
                () -> assertEquals("Ans", golden.expectedOutput()),
                () -> assertEquals(List.of("ctx one", "ctx two"), golden.context()),
                () -> assertEquals(List.of("rctx"), golden.retrievalContext()));
    }

    @Test
    void addGoldensFromCsvFileSplitsContextsWithCustomDelimiters() throws IOException {
        var file = tempDir.resolve("custom-delimiter-goldens.csv");
        Files.writeString(file, """
                input,actual_output,context,retrieval_context
                Ask,Ans,ctx one/ctx two,rctx one~rctx two
                """);
        var dataset = new EvaluationDataset();

        dataset.addGoldensFromCsvFile(file, "input", "actual_output", "expected_output", "context", "/",
                "retrieval_context", "~", "tools_called", "expected_tools", "comments", "name",
                "source_file", "additional_metadata");

        var golden = dataset.goldens().getFirst();
        assertAll(
                () -> assertEquals(List.of("ctx one", "ctx two"), golden.context()),
                () -> assertEquals(List.of("rctx one", "rctx two"), golden.retrievalContext()));
    }

    @Test
    void addGoldensFromCsvFileImportsCustomSingleTurnToolAndMetadataColumns() throws IOException {
        var file = tempDir.resolve("custom-single-metadata.csv");
        Files.writeString(file, """
                prompt,actual,expected,called,wanted,note,label,origin,meta,columns
                Ask,Ans,Ans,"[{""name"":""search"",""input_parameters"":{""query"":""refund""},""output"":""30 days""}]","[{""name"":""finalize""}]",comment,name,src.txt,"{""k"":""v""}","{""col"":""val""}"
                """);
        var dataset = new EvaluationDataset();

        dataset.addGoldensFromCsvFile(file, "prompt", "actual", "expected", "context", "retrieval_context",
                "called", "wanted", "note", "label", "origin", "meta", "columns");

        var golden = dataset.goldens().getFirst();
        assertAll(
                () -> assertEquals(List.of(new ToolCall("search", Map.of("query", "refund"), "30 days")),
                        golden.toolsCalled()),
                () -> assertEquals(List.of(new ToolCall("finalize")), golden.expectedTools()),
                () -> assertEquals("comment", golden.comments()),
                () -> assertEquals("name", golden.name()),
                () -> assertEquals("src.txt", golden.sourceFile()),
                () -> assertEquals(Map.of("k", "v"), golden.additionalMetadata()),
                () -> assertEquals(Map.of("col", "val"), golden.customColumnKeyValues()));
    }

    @Test
    void addGoldensFromCsvFileUsesDeepEvalDefaultToolDelimiters() throws IOException {
        var file = tempDir.resolve("tool-name-goldens.csv");
        Files.writeString(file, """
                input,actual_output,tools_called,expected_tools
                Ask,Ans,search;lookup,finalize;notify
                """);
        var dataset = new EvaluationDataset();

        dataset.addGoldensFromCsvFile(file);

        var golden = dataset.goldens().getFirst();
        assertAll(
                () -> assertEquals(List.of(new ToolCall("search"), new ToolCall("lookup")), golden.toolsCalled()),
                () -> assertEquals(List.of(new ToolCall("finalize"), new ToolCall("notify")), golden.expectedTools()));
    }

    @Test
    void addGoldensFromCsvFileParsesDelimitedToolNamesWithCustomDelimiters() throws IOException {
        var file = tempDir.resolve("custom-tool-delimiter-goldens.csv");
        Files.writeString(file, """
                input,actual_output,tools_called,expected_tools
                Ask,Ans,search/lookup,finalize~notify
                """);
        var dataset = new EvaluationDataset();

        dataset.addGoldensFromCsvFile(file, "input", "actual_output", "expected_output", "context", "|",
                "retrieval_context", "|", "tools_called", "/", "expected_tools", "~", "comments", "name",
                "source_file", "additional_metadata");

        var golden = dataset.goldens().getFirst();
        assertAll(
                () -> assertEquals(List.of(new ToolCall("search"), new ToolCall("lookup")), golden.toolsCalled()),
                () -> assertEquals(List.of(new ToolCall("finalize"), new ToolCall("notify")), golden.expectedTools()));
    }

    @Test
    void csvRoundTripsConversationalGoldens() throws IOException {
        var file = tempDir.resolve("conversation.csv");
        var dataset = new EvaluationDataset();
        dataset.addGolden(ConversationalGolden.builder("Book a flight")
                .expectedOutcome("User gets options")
                .userDescription("Traveler")
                .context(List.of("travel", "flights"))
                .turns(List.of(Turn.builder("user", "Find flights")
                        .userId("u1")
                        .retrievalContext(List.of("r"))
                        .toolsCalled(List.of(new ToolCall("search", Map.of("destination", "Tokyo"), "found")))
                        .mcpToolsCalled(List.of(Map.of("name", "mcp-search")))
                        .mcpResourcesCalled(List.of(Map.of("uri", "file://policy")))
                        .mcpPromptsCalled(List.of(Map.of("name", "travel-prompt")))
                        .metadata(Map.of("k", "v"))
                        .build()))
                .additionalMetadata(Map.of("gk", "gv"))
                .customColumnKeyValues(Map.of("col", "val"))
                .name("trip")
                .comments("c")
                .build());

        dataset.saveAsCsvFile(file);

        var loaded = new EvaluationDataset();
        loaded.addGoldensFromCsvFile(file);
        var golden = loaded.conversationalGoldens().getFirst();
        var turn = golden.turns().getFirst();
        assertAll(
                () -> assertEquals(true, loaded.multiTurn()),
                () -> assertEquals("Book a flight", golden.scenario()),
                () -> assertEquals("User gets options", golden.expectedOutcome()),
                () -> assertEquals("Traveler", golden.userDescription()),
                () -> assertEquals(List.of("travel", "flights"), golden.context()),
                () -> assertEquals(Map.of("gk", "gv"), golden.additionalMetadata()),
                () -> assertEquals(Map.of("col", "val"), golden.customColumnKeyValues()),
                () -> assertEquals("trip", golden.name()),
                () -> assertEquals("c", golden.comments()),
                () -> assertEquals("user", turn.role()),
                () -> assertEquals("Find flights", turn.content()),
                () -> assertEquals("u1", turn.userId()),
                () -> assertEquals(List.of("r"), turn.retrievalContext()),
                () -> assertEquals(List.of(new ToolCall("search", Map.of("destination", "Tokyo"), "found")),
                        turn.toolsCalled()),
                () -> assertEquals("mcp-search", turn.mcpToolsCalled().getFirst().get("name")),
                () -> assertEquals("file://policy", turn.mcpResourcesCalled().getFirst().get("uri")),
                () -> assertEquals("travel-prompt", turn.mcpPromptsCalled().getFirst().get("name")),
                () -> assertEquals(Map.of("k", "v"), turn.metadata()));
    }

    @Test
    void saveAsCsvFileWritesNullTurnFieldsLikeDeepEval() throws IOException {
        var file = tempDir.resolve("conversation-null-turn-fields.csv");
        var dataset = new EvaluationDataset();
        dataset.addGolden(ConversationalGolden.builder("Book a flight")
                .turns(List.of(new Turn("user", "Find flights")))
                .build());

        dataset.saveAsCsvFile(file);

        var row = Files.readAllLines(file).get(1);
        assertAll(
                () -> assertTrue(row.contains("\"\"user_id\"\":null")),
                () -> assertTrue(row.contains("\"\"retrieval_context\"\":null")),
                () -> assertTrue(row.contains("\"\"tools_called\"\":null")),
                () -> assertTrue(row.contains("\"\"mcp_tools_called\"\":null")),
                () -> assertTrue(row.contains("\"\"mcp_resources_called\"\":null")),
                () -> assertTrue(row.contains("\"\"mcp_prompts_called\"\":null")),
                () -> assertTrue(row.contains("\"\"metadata\"\":null")));
    }

    @Test
    void addGoldensFromCsvFileImportsCustomConversationalColumns() throws IOException {
        var file = tempDir.resolve("custom-conversation.csv");
        Files.writeString(file, """
                situation,dialogue,outcome,user,ctx,note,label,meta
                Book a flight,"[{""role"":""user"",""content"":""Find flights""}]",User gets options,Traveler,travel|flights,comment,trip,"{""gk"":""gv""}"
                """);
        var dataset = new EvaluationDataset();

        dataset.addGoldensFromCsvFile(file, "input", "actual_output", "expected_output", "ctx",
                "retrieval_context", "tools_called", "expected_tools", "note", "label", "source_file",
                "meta", "custom_column_key_values", "situation", "dialogue", "outcome", "user");

        var golden = dataset.conversationalGoldens().getFirst();
        assertAll(
                () -> assertEquals(true, dataset.multiTurn()),
                () -> assertEquals("Book a flight", golden.scenario()),
                () -> assertEquals("User gets options", golden.expectedOutcome()),
                () -> assertEquals("Traveler", golden.userDescription()),
                () -> assertEquals(List.of("travel", "flights"), golden.context()),
                () -> assertEquals("comment", golden.comments()),
                () -> assertEquals("trip", golden.name()),
                () -> assertEquals(Map.of("gk", "gv"), golden.additionalMetadata()),
                () -> assertEquals("user", golden.turns().getFirst().role()),
                () -> assertEquals("Find flights", golden.turns().getFirst().content()));
    }

    @Test
    void addGoldensFromCsvFileAcceptsCamelCaseAliasesForDefaultConversationalColumns() throws IOException {
        var file = tempDir.resolve("camel-case-conversational-golden.csv");
        Files.writeString(file, """
                scenario,turns,expectedOutcome,userDescription,metadata,customColumnKeyValues
                Book a flight,"[{""role"":""user"",""content"":""Find flights""}]",User gets options,Traveler,"{""gk"":""gv""}","{""col"":""val""}"
                """);
        var dataset = new EvaluationDataset();

        dataset.addGoldensFromCsvFile(file);

        var golden = dataset.conversationalGoldens().getFirst();
        assertAll(
                () -> assertEquals("User gets options", golden.expectedOutcome()),
                () -> assertEquals("Traveler", golden.userDescription()),
                () -> assertEquals(Map.of("gk", "gv"), golden.additionalMetadata()),
                () -> assertEquals(Map.of("col", "val"), golden.customColumnKeyValues()));
    }

    @Test
    void addGoldensFromJsonlFileRejectsMixedVariations() throws IOException {
        var file = tempDir.resolve("mixed.jsonl");
        Files.writeString(file, """
                {"scenario": "Book a flight", "turns": [{"role": "user", "content": "hi"}]}
                {"input": "Ask", "actual_output": "Ans"}
                """);

        assertThrows(IllegalArgumentException.class, () -> new EvaluationDataset().addGoldensFromJsonlFile(file));
    }
}
