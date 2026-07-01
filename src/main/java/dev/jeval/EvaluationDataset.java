package dev.jeval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.jeval.synthesizer.ContextConstructionConfig;
import dev.jeval.synthesizer.Synthesizer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class EvaluationDataset {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<Golden> goldens = new ArrayList<>();
    private final List<ConversationalGolden> conversationalGoldens = new ArrayList<>();
    private final List<LlmTestCase> testCases = new ArrayList<>();
    private final List<ConversationalTestCase> conversationalTestCases = new ArrayList<>();
    private boolean multiTurn;

    public EvaluationDataset() {
    }

    public EvaluationDataset(List<?> goldens) {
        if (goldens == null) {
            return;
        }
        for (var golden : goldens) {
            if (golden instanceof Golden singleTurnGolden) {
                addGolden(singleTurnGolden);
            } else if (golden instanceof ConversationalGolden conversationalGolden) {
                addGolden(conversationalGolden);
            } else {
                throw new IllegalArgumentException("'goldens' must contain Golden or ConversationalGolden values.");
            }
        }
    }

    public void addTestCase(LlmTestCase testCase) {
        if (multiTurn || !conversationalTestCases.isEmpty() || !conversationalGoldens.isEmpty()) {
            throw new IllegalArgumentException("You cannot add 'LlmTestCase' to a multi-turn dataset.");
        }
        testCases.add(testCase.withDatasetRank(testCases.size()));
    }

    public void addTestCase(ConversationalTestCase testCase) {
        if (!testCases.isEmpty() || !goldens.isEmpty()) {
            throw new IllegalArgumentException("You cannot add 'ConversationalTestCase' to a single-turn dataset.");
        }
        multiTurn = true;
        conversationalTestCases.add(testCase.withDatasetRank(conversationalTestCases.size()));
    }

    public void addGolden(Golden golden) {
        if (multiTurn || !conversationalTestCases.isEmpty() || !conversationalGoldens.isEmpty()) {
            throw new IllegalArgumentException("You cannot add 'Golden' to a multi-turn dataset.");
        }
        goldens.add(golden);
    }

    public void addGolden(ConversationalGolden golden) {
        if (!testCases.isEmpty() || !goldens.isEmpty()) {
            throw new IllegalArgumentException("You cannot add 'ConversationalGolden' to a single-turn dataset.");
        }
        multiTurn = true;
        conversationalGoldens.add(golden);
    }

    public boolean multiTurn() {
        return multiTurn;
    }

    public List<LlmTestCase> testCases() {
        return List.copyOf(testCases);
    }

    public List<ConversationalTestCase> conversationalTestCases() {
        return List.copyOf(conversationalTestCases);
    }

    public List<Golden> goldens() {
        return List.copyOf(goldens);
    }

    public List<ConversationalGolden> conversationalGoldens() {
        return List.copyOf(conversationalGoldens);
    }

    public List<LlmTestCase> testCasesFromGoldens() {
        var cases = new ArrayList<LlmTestCase>();
        for (var i = 0; i < goldens.size(); i++) {
            cases.add(goldens.get(i).toTestCase(i));
        }
        return List.copyOf(cases);
    }

    public List<ConversationalTestCase> conversationalTestCasesFromGoldens() {
        var cases = new ArrayList<ConversationalTestCase>();
        for (var i = 0; i < conversationalGoldens.size(); i++) {
            cases.add(conversationalGoldens.get(i).toTestCase(i));
        }
        return List.copyOf(cases);
    }

    public List<EvaluationResult> evaluate(List<? extends Metric> metrics) {
        var cases = testCases.isEmpty() ? testCasesFromGoldens() : testCases;
        if (cases.isEmpty()) {
            throw new IllegalStateException("Unable to evaluate dataset with no test cases.");
        }
        return Evaluator.evaluate(cases, metrics);
    }

    public CompletableFuture<List<EvaluationResult>> aEvaluate(List<? extends Metric> metrics) {
        return CompletableFuture.supplyAsync(() -> evaluate(metrics));
    }

    public List<ConversationalEvaluationResult> evaluateConversations(List<? extends ConversationalMetric> metrics) {
        var cases = conversationalTestCases.isEmpty()
                ? conversationalTestCasesFromGoldens()
                : conversationalTestCases;
        if (cases.isEmpty()) {
            throw new IllegalStateException("Unable to evaluate dataset with no conversational test cases.");
        }
        return Evaluator.evaluateConversations(cases, metrics);
    }

    public CompletableFuture<List<ConversationalEvaluationResult>> aEvaluateConversations(
            List<? extends ConversationalMetric> metrics) {
        return CompletableFuture.supplyAsync(() -> evaluateConversations(metrics));
    }

    public void generateGoldensFromDocs(List<Path> documentPaths, Synthesizer synthesizer) throws IOException {
        generateGoldensFromDocs(documentPaths, true, 2, ContextConstructionConfig.DEFAULT, synthesizer);
    }

    public CompletableFuture<Void> generateGoldensFromDocsAsync(List<Path> documentPaths, Synthesizer synthesizer) {
        return generateGoldensFromDocsAsync(documentPaths, true, 2, ContextConstructionConfig.DEFAULT, synthesizer);
    }

    public void generateGoldensFromDocs(
            List<Path> documentPaths,
            boolean includeExpectedOutput,
            int maxGoldensPerContext,
            ContextConstructionConfig contextConstructionConfig,
            Synthesizer synthesizer) throws IOException {
        var generated = requireSynthesizer(synthesizer).generateGoldensFromDocs(
                documentPaths,
                includeExpectedOutput,
                maxGoldensPerContext,
                contextConstructionConfig);
        generated.forEach(this::addGolden);
    }

    public CompletableFuture<Void> generateGoldensFromDocsAsync(
            List<Path> documentPaths,
            boolean includeExpectedOutput,
            int maxGoldensPerContext,
            ContextConstructionConfig contextConstructionConfig,
            Synthesizer synthesizer) {
        return requireSynthesizer(synthesizer).generateGoldensFromDocsAsync(
                documentPaths,
                includeExpectedOutput,
                maxGoldensPerContext,
                contextConstructionConfig)
                .thenAccept(generated -> generated.forEach(this::addGolden));
    }

    public void generateGoldensFromContexts(List<List<String>> contexts, Synthesizer synthesizer) {
        generateGoldensFromContexts(contexts, true, 2, synthesizer);
    }

    public CompletableFuture<Void> generateGoldensFromContextsAsync(
            List<List<String>> contexts,
            Synthesizer synthesizer) {
        return generateGoldensFromContextsAsync(contexts, true, 2, synthesizer);
    }

    public void generateGoldensFromContexts(
            List<List<String>> contexts,
            boolean includeExpectedOutput,
            int maxGoldensPerContext,
            Synthesizer synthesizer) {
        generateGoldensFromContexts(contexts, includeExpectedOutput, maxGoldensPerContext, null, synthesizer);
    }

    public void generateGoldensFromContexts(
            List<List<String>> contexts,
            boolean includeExpectedOutput,
            int maxGoldensPerContext,
            List<?> sourceFiles,
            Synthesizer synthesizer) {
        var generated = requireSynthesizer(synthesizer).generateGoldensFromContexts(
                contexts,
                includeExpectedOutput,
                maxGoldensPerContext,
                sourceFiles);
        generated.forEach(this::addGolden);
    }

    public void generateGoldensFromContexts(
            List<List<String>> contexts,
            boolean includeExpectedOutput,
            int maxGoldensPerContext,
            List<?> sourceFiles,
            List<List<String>> contextChunkSourceFiles,
            Integer targetFilesPerContext,
            Synthesizer synthesizer) {
        var generated = requireSynthesizer(synthesizer).generateGoldensFromContexts(
                contexts,
                includeExpectedOutput,
                maxGoldensPerContext,
                sourceFiles,
                contextChunkSourceFiles,
                targetFilesPerContext);
        generated.forEach(this::addGolden);
    }

    public CompletableFuture<Void> generateGoldensFromContextsAsync(
            List<List<String>> contexts,
            boolean includeExpectedOutput,
            int maxGoldensPerContext,
            Synthesizer synthesizer) {
        return generateGoldensFromContextsAsync(contexts, includeExpectedOutput, maxGoldensPerContext, null,
                synthesizer);
    }

    public CompletableFuture<Void> generateGoldensFromContextsAsync(
            List<List<String>> contexts,
            boolean includeExpectedOutput,
            int maxGoldensPerContext,
            List<?> sourceFiles,
            Synthesizer synthesizer) {
        return requireSynthesizer(synthesizer).generateGoldensFromContextsAsync(
                contexts,
                includeExpectedOutput,
                maxGoldensPerContext,
                sourceFiles)
                .thenAccept(generated -> generated.forEach(this::addGolden));
    }

    public CompletableFuture<Void> generateGoldensFromContextsAsync(
            List<List<String>> contexts,
            boolean includeExpectedOutput,
            int maxGoldensPerContext,
            List<?> sourceFiles,
            List<List<String>> contextChunkSourceFiles,
            Integer targetFilesPerContext,
            Synthesizer synthesizer) {
        return requireSynthesizer(synthesizer).generateGoldensFromContextsAsync(
                contexts,
                includeExpectedOutput,
                maxGoldensPerContext,
                sourceFiles,
                contextChunkSourceFiles,
                targetFilesPerContext)
                .thenAccept(generated -> generated.forEach(this::addGolden));
    }

    public void generateTextToSqlGoldensFromContext(
            List<String> context,
            boolean includeExpectedOutput,
            int maxGoldensPerContext,
            Synthesizer synthesizer) {
        var generated = requireSynthesizer(synthesizer).generateTextToSqlGoldensFromContext(
                context,
                includeExpectedOutput,
                maxGoldensPerContext);
        generated.forEach(this::addGolden);
    }

    public CompletableFuture<Void> generateTextToSqlGoldensFromContextAsync(
            List<String> context,
            boolean includeExpectedOutput,
            int maxGoldensPerContext,
            Synthesizer synthesizer) {
        return requireSynthesizer(synthesizer).generateTextToSqlGoldensFromContextAsync(
                context,
                includeExpectedOutput,
                maxGoldensPerContext)
                .thenAccept(generated -> generated.forEach(this::addGolden));
    }

    public void generateGoldensFromScratch(int numGoldens, Synthesizer synthesizer) {
        requireSynthesizer(synthesizer).generateGoldensFromScratch(numGoldens).forEach(this::addGolden);
    }

    public CompletableFuture<Void> generateGoldensFromScratchAsync(int numGoldens, Synthesizer synthesizer) {
        return requireSynthesizer(synthesizer).generateGoldensFromScratchAsync(numGoldens)
                .thenAccept(generated -> generated.forEach(this::addGolden));
    }

    public void generateGoldensFromGoldens(
            int maxGoldensPerGolden,
            boolean includeExpectedOutput,
            Synthesizer synthesizer) {
        var generated = requireSynthesizer(synthesizer).generateGoldensFromGoldens(
                goldens(),
                maxGoldensPerGolden,
                includeExpectedOutput);
        generated.forEach(this::addGolden);
    }

    public CompletableFuture<Void> generateGoldensFromGoldensAsync(
            int maxGoldensPerGolden,
            boolean includeExpectedOutput,
            Synthesizer synthesizer) {
        return requireSynthesizer(synthesizer).generateGoldensFromGoldensAsync(
                goldens(),
                maxGoldensPerGolden,
                includeExpectedOutput)
                .thenAccept(generated -> generated.forEach(this::addGolden));
    }

    public void generateConversationalGoldensFromDocs(List<Path> documentPaths, Synthesizer synthesizer)
            throws IOException {
        generateConversationalGoldensFromDocs(documentPaths, true, 2, ContextConstructionConfig.DEFAULT, synthesizer);
    }

    public CompletableFuture<Void> generateConversationalGoldensFromDocsAsync(
            List<Path> documentPaths,
            Synthesizer synthesizer) {
        return generateConversationalGoldensFromDocsAsync(
                documentPaths, true, 2, ContextConstructionConfig.DEFAULT, synthesizer);
    }

    public void generateConversationalGoldensFromDocs(
            List<Path> documentPaths,
            boolean includeExpectedOutcome,
            int maxGoldensPerContext,
            ContextConstructionConfig contextConstructionConfig,
            Synthesizer synthesizer) throws IOException {
        var generated = requireSynthesizer(synthesizer).generateConversationalGoldensFromDocs(
                documentPaths,
                includeExpectedOutcome,
                maxGoldensPerContext,
                contextConstructionConfig);
        generated.forEach(this::addGolden);
    }

    public CompletableFuture<Void> generateConversationalGoldensFromDocsAsync(
            List<Path> documentPaths,
            boolean includeExpectedOutcome,
            int maxGoldensPerContext,
            ContextConstructionConfig contextConstructionConfig,
            Synthesizer synthesizer) {
        return requireSynthesizer(synthesizer).generateConversationalGoldensFromDocsAsync(
                documentPaths,
                includeExpectedOutcome,
                maxGoldensPerContext,
                contextConstructionConfig)
                .thenAccept(generated -> generated.forEach(this::addGolden));
    }

    public void generateConversationalGoldensFromContexts(
            List<List<String>> contexts,
            Synthesizer synthesizer) {
        generateConversationalGoldensFromContexts(contexts, true, 2, synthesizer);
    }

    public CompletableFuture<Void> generateConversationalGoldensFromContextsAsync(
            List<List<String>> contexts,
            Synthesizer synthesizer) {
        return generateConversationalGoldensFromContextsAsync(contexts, true, 2, synthesizer);
    }

    public void generateConversationalGoldensFromContexts(
            List<List<String>> contexts,
            boolean includeExpectedOutcome,
            int maxGoldensPerContext,
            Synthesizer synthesizer) {
        generateConversationalGoldensFromContexts(contexts, includeExpectedOutcome, maxGoldensPerContext, null,
                synthesizer);
    }

    public void generateConversationalGoldensFromContexts(
            List<List<String>> contexts,
            boolean includeExpectedOutcome,
            int maxGoldensPerContext,
            List<?> sourceFiles,
            Synthesizer synthesizer) {
        var generated = requireSynthesizer(synthesizer).generateConversationalGoldensFromContexts(
                contexts,
                includeExpectedOutcome,
                maxGoldensPerContext,
                sourceFiles);
        generated.forEach(this::addGolden);
    }

    public void generateConversationalGoldensFromContexts(
            List<List<String>> contexts,
            boolean includeExpectedOutcome,
            int maxGoldensPerContext,
            List<?> sourceFiles,
            List<List<String>> contextChunkSourceFiles,
            Integer targetFilesPerContext,
            Synthesizer synthesizer) {
        var generated = requireSynthesizer(synthesizer).generateConversationalGoldensFromContexts(
                contexts,
                includeExpectedOutcome,
                maxGoldensPerContext,
                sourceFiles,
                contextChunkSourceFiles,
                targetFilesPerContext);
        generated.forEach(this::addGolden);
    }

    public CompletableFuture<Void> generateConversationalGoldensFromContextsAsync(
            List<List<String>> contexts,
            boolean includeExpectedOutcome,
            int maxGoldensPerContext,
            Synthesizer synthesizer) {
        return generateConversationalGoldensFromContextsAsync(contexts, includeExpectedOutcome, maxGoldensPerContext,
                null, synthesizer);
    }

    public CompletableFuture<Void> generateConversationalGoldensFromContextsAsync(
            List<List<String>> contexts,
            boolean includeExpectedOutcome,
            int maxGoldensPerContext,
            List<?> sourceFiles,
            Synthesizer synthesizer) {
        return requireSynthesizer(synthesizer).generateConversationalGoldensFromContextsAsync(
                contexts,
                includeExpectedOutcome,
                maxGoldensPerContext,
                sourceFiles)
                .thenAccept(generated -> generated.forEach(this::addGolden));
    }

    public void generateConversationalGoldensFromScratch(int numGoldens, Synthesizer synthesizer) {
        requireSynthesizer(synthesizer).generateConversationalGoldensFromScratch(numGoldens).forEach(this::addGolden);
    }

    public CompletableFuture<Void> generateConversationalGoldensFromScratchAsync(
            int numGoldens,
            Synthesizer synthesizer) {
        return requireSynthesizer(synthesizer).generateConversationalGoldensFromScratchAsync(numGoldens)
                .thenAccept(generated -> generated.forEach(this::addGolden));
    }

    public void generateConversationalGoldensFromGoldens(
            int maxGoldensPerGolden,
            boolean includeExpectedOutcome,
            Synthesizer synthesizer) {
        var generated = requireSynthesizer(synthesizer).generateConversationalGoldensFromGoldens(
                conversationalGoldens(),
                maxGoldensPerGolden,
                includeExpectedOutcome);
        generated.forEach(this::addGolden);
    }

    public CompletableFuture<Void> generateConversationalGoldensFromGoldensAsync(
            int maxGoldensPerGolden,
            boolean includeExpectedOutcome,
            Synthesizer synthesizer) {
        return requireSynthesizer(synthesizer).generateConversationalGoldensFromGoldensAsync(
                conversationalGoldens(),
                maxGoldensPerGolden,
                includeExpectedOutcome)
                .thenAccept(generated -> generated.forEach(this::addGolden));
    }

    public void addTestCasesFromJsonFile(
            Path file,
            String inputKeyName,
            String actualOutputKeyName,
            String expectedOutputKeyName,
            String contextKeyName,
            String retrievalContextKeyName) {
        addTestCasesFromJsonFile(file, inputKeyName, actualOutputKeyName, expectedOutputKeyName,
                contextKeyName, retrievalContextKeyName, null, null);
    }

    public void addTestCasesFromJsonFile(
            Path file,
            String inputKeyName,
            String actualOutputKeyName,
            String expectedOutputKeyName,
            String contextKeyName,
            String retrievalContextKeyName,
            String toolsCalledKeyName,
            String expectedToolsKeyName) {
        addTestCasesFromJsonFile(file, inputKeyName, actualOutputKeyName, expectedOutputKeyName,
                contextKeyName, retrievalContextKeyName, toolsCalledKeyName, expectedToolsKeyName, null);
    }

    public void addTestCasesFromJsonFile(
            Path file,
            String inputKeyName,
            String actualOutputKeyName,
            String expectedOutputKeyName,
            String contextKeyName,
            String retrievalContextKeyName,
            String toolsCalledKeyName,
            String expectedToolsKeyName,
            String additionalMetadataKeyName) {
        var rows = readJsonArray(file);
        for (var row : rows) {
            if (!row.has(inputKeyName) || !row.has(actualOutputKeyName)) {
                throw new IllegalArgumentException("Required fields are missing in one or more JSON objects");
            }
            addTestCase(LlmTestCase.builder(requiredText(row, inputKeyName))
                    .actualOutput(requiredText(row, actualOutputKeyName))
                    .expectedOutput(textOrNull(row, expectedOutputKeyName))
                    .context(textListOrNull(row, contextKeyName))
                    .retrievalContext(textListOrNull(row, retrievalContextKeyName))
                    .toolsCalled(toolListOrEmptyIfMissing(row, toolsCalledKeyName, false))
                    .expectedTools(toolListOrEmptyIfMissing(row, expectedToolsKeyName, false))
                    .additionalMetadata(metadataMapOrNull(row, additionalMetadataKeyName))
                    .comments(textOrNull(row, "comments"))
                    .tokenCost(doubleOrNull(row, "token_cost"))
                    .completionTime(doubleOrNull(row, "completion_time"))
                    .customColumnKeyValues(stringMapOrNull(row, "custom_column_key_values"))
                    .mcpServers(objectListOrNull(row, "mcp_servers"))
                    .mcpToolsCalled(objectListOrNull(row, "mcp_tools_called"))
                    .mcpResourcesCalled(objectListOrNull(row, "mcp_resources_called"))
                    .mcpPromptsCalled(objectListOrNull(row, "mcp_prompts_called"))
                    .trace(objectMapOrNull(row, "trace"))
                    .name(textOrNull(row, "name"))
                    .tags(textListOrNull(row, "tags"))
                    .build());
        }
    }

    public void addTestCasesFromCsvFile(
            Path file,
            String inputColName,
            String actualOutputColName,
            String expectedOutputColName,
            String contextColName,
            String retrievalContextColName) {
        addTestCasesFromCsvFile(file, inputColName, actualOutputColName, expectedOutputColName,
                contextColName, retrievalContextColName, null, null);
    }

    public void addTestCasesFromCsvFile(
            Path file,
            String inputColName,
            String actualOutputColName,
            String expectedOutputColName,
            String contextColName,
            String retrievalContextColName,
            String toolsCalledColName,
            String expectedToolsColName) {
        addTestCasesFromCsvFile(file, inputColName, actualOutputColName, expectedOutputColName,
                contextColName, retrievalContextColName, toolsCalledColName, expectedToolsColName, null);
    }

    public void addTestCasesFromCsvFile(
            Path file,
            String inputColName,
            String actualOutputColName,
            String expectedOutputColName,
            String contextColName,
            String retrievalContextColName,
            String toolsCalledColName,
            String expectedToolsColName,
            String additionalMetadataColName) {
        addTestCasesFromCsvFile(file, inputColName, actualOutputColName, expectedOutputColName,
                contextColName, ";", retrievalContextColName, ";", toolsCalledColName, expectedToolsColName,
                additionalMetadataColName);
    }

    public void addTestCasesFromCsvFile(
            Path file,
            String inputColName,
            String actualOutputColName,
            String expectedOutputColName,
            String contextColName,
            String contextDelimiter,
            String retrievalContextColName,
            String retrievalContextDelimiter,
            String toolsCalledColName,
            String expectedToolsColName,
            String additionalMetadataColName) {
        try {
            var lines = Files.readAllLines(file);
            if (lines.isEmpty()) {
                return;
            }
            var headers = parseCsvLine(lines.getFirst());
            for (var i = 1; i < lines.size(); i++) {
                if (lines.get(i).isBlank()) {
                    continue;
                }
                var row = csvRow(headers, parseCsvLine(lines.get(i)));
                if (blank(row.get(inputColName)) || blank(row.get(actualOutputColName))) {
                    throw new IllegalArgumentException("Required fields are missing in one or more CSV rows");
                }
                addTestCase(LlmTestCase.builder(row.get(inputColName))
                        .actualOutput(row.get(actualOutputColName))
                        .expectedOutput(nullIfBlank(row.get(expectedOutputColName)))
                        .context(csvListOrNull(row.get(contextColName), contextDelimiter))
                        .retrievalContext(csvListOrNull(row.get(retrievalContextColName), retrievalContextDelimiter))
                        .toolsCalled(toolListFromCsv(row.get(toolsCalledColName), "tools_called", ";"))
                        .expectedTools(toolListFromCsv(row.get(expectedToolsColName), "expected_tools", ";"))
                        .additionalMetadata(metadataMapFromCsv(row, additionalMetadataColName))
                        .comments(nullIfBlank(row.get("comments")))
                        .tokenCost(doubleFromCsv(row.get("token_cost"), "token_cost"))
                        .completionTime(doubleFromCsv(row.get("completion_time"), "completion_time"))
                        .customColumnKeyValues(stringMapFromCsv(row.get("custom_column_key_values")))
                        .trace(objectMapFromCsv(row.get("trace"), "trace"))
                        .mcpServers(objectListFromCsv(row.get("mcp_servers"), "mcp_servers"))
                        .mcpToolsCalled(objectListFromCsv(row.get("mcp_tools_called"), "mcp_tools_called"))
                        .mcpResourcesCalled(objectListFromCsv(row.get("mcp_resources_called"), "mcp_resources_called"))
                        .mcpPromptsCalled(objectListFromCsv(row.get("mcp_prompts_called"), "mcp_prompts_called"))
                        .name(nullIfBlank(row.get("name")))
                        .tags(csvListOrNull(row.get("tags"), ";"))
                        .build());
            }
        } catch (IOException error) {
            throw new IllegalArgumentException("The file " + file + " could not be read.", error);
        }
    }

    public void addGoldensFromJsonFile(Path file) {
        addGoldensFromJsonFile(file, "input", "actual_output", "expected_output", "context", "retrieval_context",
                "tools_called", "expected_tools", "additional_metadata", "custom_column_key_values",
                "name", "comments", "source_file");
    }

    public void addGoldensFromJsonFile(
            Path file,
            String inputKeyName,
            String actualOutputKeyName,
            String expectedOutputKeyName,
            String contextKeyName,
            String retrievalContextKeyName,
            String toolsCalledKeyName,
            String expectedToolsKeyName,
            String additionalMetadataKeyName,
            String customColumnKeyValuesKeyName,
            String nameKeyName,
            String commentsKeyName,
            String sourceFileKeyName) {
        addGoldensFromJsonFile(file, inputKeyName, actualOutputKeyName, expectedOutputKeyName,
                contextKeyName, retrievalContextKeyName, toolsCalledKeyName, expectedToolsKeyName,
                additionalMetadataKeyName, customColumnKeyValuesKeyName, nameKeyName, commentsKeyName,
                sourceFileKeyName, "scenario", "turns", "expected_outcome", "user_description");
    }

    public void addGoldensFromJsonFile(
            Path file,
            String inputKeyName,
            String actualOutputKeyName,
            String expectedOutputKeyName,
            String contextKeyName,
            String retrievalContextKeyName,
            String toolsCalledKeyName,
            String expectedToolsKeyName,
            String additionalMetadataKeyName,
            String customColumnKeyValuesKeyName,
            String nameKeyName,
            String commentsKeyName,
            String sourceFileKeyName,
            String scenarioKeyName,
            String turnsKeyName,
            String expectedOutcomeKeyName,
            String userDescriptionKeyName) {
        var rows = readJsonArray(file);
        for (var row : rows) {
            addGoldenFromJson(row, inputKeyName, actualOutputKeyName, expectedOutputKeyName, contextKeyName,
                    retrievalContextKeyName, toolsCalledKeyName, expectedToolsKeyName, additionalMetadataKeyName,
                    customColumnKeyValuesKeyName, nameKeyName, commentsKeyName, sourceFileKeyName,
                    scenarioKeyName, turnsKeyName, expectedOutcomeKeyName, userDescriptionKeyName);
        }
    }

    public void addGoldensFromJsonlFile(Path file) {
        addGoldensFromJsonlFile(file, "input", "actual_output", "expected_output", "context", "retrieval_context",
                "tools_called", "expected_tools", "additional_metadata", "custom_column_key_values",
                "name", "comments", "source_file");
    }

    public void addGoldensFromJsonlFile(
            Path file,
            String inputKeyName,
            String actualOutputKeyName,
            String expectedOutputKeyName,
            String contextKeyName,
            String retrievalContextKeyName,
            String toolsCalledKeyName,
            String expectedToolsKeyName,
            String additionalMetadataKeyName,
            String customColumnKeyValuesKeyName,
            String nameKeyName,
            String commentsKeyName,
            String sourceFileKeyName) {
        addGoldensFromJsonlFile(file, inputKeyName, actualOutputKeyName, expectedOutputKeyName,
                contextKeyName, "|", retrievalContextKeyName, "|", toolsCalledKeyName, expectedToolsKeyName,
                additionalMetadataKeyName, customColumnKeyValuesKeyName, nameKeyName, commentsKeyName,
                sourceFileKeyName);
    }

    public void addGoldensFromJsonlFile(
            Path file,
            String inputKeyName,
            String actualOutputKeyName,
            String expectedOutputKeyName,
            String contextKeyName,
            String contextDelimiter,
            String retrievalContextKeyName,
            String retrievalContextDelimiter,
            String toolsCalledKeyName,
            String expectedToolsKeyName,
            String additionalMetadataKeyName,
            String customColumnKeyValuesKeyName,
            String nameKeyName,
            String commentsKeyName,
            String sourceFileKeyName) {
        addGoldensFromJsonlFile(file, inputKeyName, actualOutputKeyName, expectedOutputKeyName,
                contextKeyName, retrievalContextKeyName, toolsCalledKeyName, expectedToolsKeyName,
                additionalMetadataKeyName, customColumnKeyValuesKeyName, nameKeyName, commentsKeyName,
                sourceFileKeyName, "scenario", "turns", "expected_outcome", "user_description",
                contextDelimiter, retrievalContextDelimiter);
    }

    public void addGoldensFromJsonlFile(
            Path file,
            String inputKeyName,
            String actualOutputKeyName,
            String expectedOutputKeyName,
            String contextKeyName,
            String retrievalContextKeyName,
            String toolsCalledKeyName,
            String expectedToolsKeyName,
            String additionalMetadataKeyName,
            String customColumnKeyValuesKeyName,
            String nameKeyName,
            String commentsKeyName,
            String sourceFileKeyName,
            String scenarioKeyName,
            String turnsKeyName,
            String expectedOutcomeKeyName,
            String userDescriptionKeyName) {
        addGoldensFromJsonlFile(file, inputKeyName, actualOutputKeyName, expectedOutputKeyName,
                contextKeyName, retrievalContextKeyName, toolsCalledKeyName, expectedToolsKeyName,
                additionalMetadataKeyName, customColumnKeyValuesKeyName, nameKeyName, commentsKeyName,
                sourceFileKeyName, scenarioKeyName, turnsKeyName, expectedOutcomeKeyName, userDescriptionKeyName,
                "|", "|");
    }

    public void addGoldensFromJsonlFile(
            Path file,
            String inputKeyName,
            String actualOutputKeyName,
            String expectedOutputKeyName,
            String contextKeyName,
            String retrievalContextKeyName,
            String toolsCalledKeyName,
            String expectedToolsKeyName,
            String additionalMetadataKeyName,
            String customColumnKeyValuesKeyName,
            String nameKeyName,
            String commentsKeyName,
            String sourceFileKeyName,
            String scenarioKeyName,
            String turnsKeyName,
            String expectedOutcomeKeyName,
            String userDescriptionKeyName,
            String contextDelimiter,
            String retrievalContextDelimiter) {
        try {
            var lines = Files.readAllLines(file);
            for (var i = 0; i < lines.size(); i++) {
                var line = lines.get(i);
                if (!line.isBlank()) {
                    try {
                        addGoldenFromJson(MAPPER.readTree(line), inputKeyName, actualOutputKeyName, expectedOutputKeyName,
                                contextKeyName, retrievalContextKeyName, toolsCalledKeyName, expectedToolsKeyName,
                                additionalMetadataKeyName, customColumnKeyValuesKeyName, nameKeyName, commentsKeyName,
                                sourceFileKeyName, scenarioKeyName, turnsKeyName, expectedOutcomeKeyName,
                                userDescriptionKeyName, contextDelimiter, retrievalContextDelimiter);
                    } catch (JsonProcessingException error) {
                        throw new IllegalArgumentException(
                                "The file " + file + " contains invalid JSON on line " + (i + 1) + ".", error);
                    }
                }
            }
        } catch (IOException error) {
            throw new IllegalArgumentException("The file " + file + " could not be read.", error);
        }
    }

    public void addGoldensFromCsvFile(Path file) {
        addGoldensFromCsvFile(file, "input", "actual_output", "expected_output", "context", "retrieval_context");
    }

    public void addGoldensFromCsvFile(
            Path file,
            String inputColName,
            String actualOutputColName,
            String expectedOutputColName,
            String contextColName,
            String retrievalContextColName) {
        addGoldensFromCsvFile(file, inputColName, actualOutputColName, expectedOutputColName,
                contextColName, retrievalContextColName, "tools_called", "expected_tools",
                "comments", "name", "source_file", "additional_metadata");
    }

    public void addGoldensFromCsvFile(
            Path file,
            String inputColName,
            String actualOutputColName,
            String expectedOutputColName,
            String contextColName,
            String retrievalContextColName,
            String toolsCalledColName,
            String expectedToolsColName,
            String commentsColName,
            String nameColName,
            String sourceFileColName,
            String additionalMetadataColName) {
        addGoldensFromCsvFile(file, inputColName, actualOutputColName, expectedOutputColName,
                contextColName, retrievalContextColName, toolsCalledColName, expectedToolsColName,
                commentsColName, nameColName, sourceFileColName, additionalMetadataColName,
                "custom_column_key_values");
    }

    public void addGoldensFromCsvFile(
            Path file,
            String inputColName,
            String actualOutputColName,
            String expectedOutputColName,
            String contextColName,
            String contextDelimiter,
            String retrievalContextColName,
            String retrievalContextDelimiter,
            String toolsCalledColName,
            String expectedToolsColName,
            String commentsColName,
            String nameColName,
            String sourceFileColName,
            String additionalMetadataColName) {
        addGoldensFromCsvFile(file, inputColName, actualOutputColName, expectedOutputColName,
                contextColName, retrievalContextColName, toolsCalledColName, expectedToolsColName,
                commentsColName, nameColName, sourceFileColName, additionalMetadataColName,
                "custom_column_key_values",
                "scenario", "turns", "expected_outcome", "user_description",
                contextDelimiter, retrievalContextDelimiter, "|", "|");
    }

    public void addGoldensFromCsvFile(
            Path file,
            String inputColName,
            String actualOutputColName,
            String expectedOutputColName,
            String contextColName,
            String contextDelimiter,
            String retrievalContextColName,
            String retrievalContextDelimiter,
            String toolsCalledColName,
            String toolsCalledDelimiter,
            String expectedToolsColName,
            String expectedToolsDelimiter,
            String commentsColName,
            String nameColName,
            String sourceFileColName,
            String additionalMetadataColName) {
        addGoldensFromCsvFile(file, inputColName, actualOutputColName, expectedOutputColName,
                contextColName, retrievalContextColName, toolsCalledColName, expectedToolsColName,
                commentsColName, nameColName, sourceFileColName, additionalMetadataColName,
                "custom_column_key_values",
                "scenario", "turns", "expected_outcome", "user_description",
                contextDelimiter, retrievalContextDelimiter, toolsCalledDelimiter, expectedToolsDelimiter);
    }

    public void addGoldensFromCsvFile(
            Path file,
            String inputColName,
            String actualOutputColName,
            String expectedOutputColName,
            String contextColName,
            String retrievalContextColName,
            String toolsCalledColName,
            String expectedToolsColName,
            String commentsColName,
            String nameColName,
            String sourceFileColName,
            String additionalMetadataColName,
            String customColumnKeyValuesColName) {
        addGoldensFromCsvFile(file, inputColName, actualOutputColName, expectedOutputColName,
                contextColName, retrievalContextColName, toolsCalledColName, expectedToolsColName,
                commentsColName, nameColName, sourceFileColName, additionalMetadataColName,
                customColumnKeyValuesColName,
                "scenario", "turns", "expected_outcome", "user_description");
    }

    public void addGoldensFromCsvFile(
            Path file,
            String inputColName,
            String actualOutputColName,
            String expectedOutputColName,
            String contextColName,
            String retrievalContextColName,
            String toolsCalledColName,
            String expectedToolsColName,
            String commentsColName,
            String nameColName,
            String sourceFileColName,
            String additionalMetadataColName,
            String customColumnKeyValuesColName,
            String scenarioColName,
            String turnsColName,
            String expectedOutcomeColName,
            String userDescriptionColName) {
        addGoldensFromCsvFile(file, inputColName, actualOutputColName, expectedOutputColName,
                contextColName, retrievalContextColName, toolsCalledColName, expectedToolsColName,
                commentsColName, nameColName, sourceFileColName, additionalMetadataColName,
                customColumnKeyValuesColName, scenarioColName, turnsColName, expectedOutcomeColName,
                userDescriptionColName, "|", "|", ";", ";");
    }

    public void addGoldensFromCsvFile(
            Path file,
            String inputColName,
            String actualOutputColName,
            String expectedOutputColName,
            String contextColName,
            String retrievalContextColName,
            String toolsCalledColName,
            String expectedToolsColName,
            String commentsColName,
            String nameColName,
            String sourceFileColName,
            String additionalMetadataColName,
            String customColumnKeyValuesColName,
            String scenarioColName,
            String turnsColName,
            String expectedOutcomeColName,
            String userDescriptionColName,
            String contextDelimiter,
            String retrievalContextDelimiter,
            String toolsCalledDelimiter,
            String expectedToolsDelimiter) {
        try {
            var lines = Files.readAllLines(file);
            if (lines.isEmpty()) {
                return;
            }
            var headers = parseCsvLine(lines.getFirst());
            for (var i = 1; i < lines.size(); i++) {
                if (lines.get(i).isBlank()) {
                    continue;
                }
                var row = csvRow(headers, parseCsvLine(lines.get(i)));
                if (!blank(row.get(scenarioColName))) {
                    addGolden(ConversationalGolden.builder(row.get(scenarioColName))
                            .turns(turnListFromCsv(row.get(turnsColName)))
                            .expectedOutcome(nullIfBlank(row.get(expectedOutcomeColName)))
                            .userDescription(nullIfBlank(row.get(userDescriptionColName)))
                            .context(csvListOrNull(row.get(contextColName), contextDelimiter))
                            .name(nullIfBlank(row.get(nameColName)))
                            .comments(nullIfBlank(row.get(commentsColName)))
                            .additionalMetadata(objectMapFromCsv(row.get(additionalMetadataColName), "additional_metadata"))
                            .customColumnKeyValues(stringMapFromCsv(row.get(customColumnKeyValuesColName)))
                            .build());
                    continue;
                }
                if (blank(row.get(inputColName))) {
                    throw new IllegalArgumentException("Required fields are missing in one or more CSV rows");
                }
                addGolden(Golden.builder(row.get(inputColName))
                        .actualOutput(nullIfBlank(row.get(actualOutputColName)))
                        .expectedOutput(nullIfBlank(row.get(expectedOutputColName)))
                        .retrievalContext(csvListOrNull(row.get(retrievalContextColName), retrievalContextDelimiter))
                        .context(csvListOrNull(row.get(contextColName), contextDelimiter))
                        .name(nullIfBlank(row.get(nameColName)))
                        .comments(nullIfBlank(row.get(commentsColName)))
                        .sourceFile(nullIfBlank(row.get(sourceFileColName)))
                        .toolsCalled(toolListFromCsv(row.get(toolsCalledColName), "tools_called", toolsCalledDelimiter))
                        .expectedTools(toolListFromCsv(row.get(expectedToolsColName), "expected_tools", expectedToolsDelimiter))
                        .additionalMetadata(objectMapFromCsv(row.get(additionalMetadataColName), "additional_metadata"))
                        .customColumnKeyValues(stringMapFromCsv(row.get(customColumnKeyValuesColName)))
                        .build());
            }
        } catch (IOException error) {
            throw new IllegalArgumentException("The file " + file + " could not be read.", error);
        }
    }

    public void saveAsJsonFile(Path file) {
        saveAsJsonFile(file, true);
    }

    public Path saveAs(String fileType, Path directory) {
        return saveAs(fileType, directory, null, false);
    }

    public Path saveAs(String fileType, Path directory, boolean includeTestCases) {
        return saveAs(fileType, directory, null, includeTestCases);
    }

    public Path saveAs(String fileType, Path directory, String fileName) {
        return saveAs(fileType, directory, fileName, false);
    }

    public Path saveAs(String fileType, Path directory, String fileName, boolean includeTestCases) {
        var normalizedFileType = fileType == null ? "" : fileType.toLowerCase();
        var baseName = fileName == null
                ? LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                : fileName;
        var file = directory.resolve(baseName + "." + normalizedFileType);
        switch (normalizedFileType) {
            case "json" -> saveAsJsonFile(file, includeTestCases);
            case "jsonl" -> saveAsJsonlFile(file, includeTestCases);
            case "csv" -> saveAsCsvFile(file, includeTestCases);
            default -> throw new IllegalArgumentException("Invalid file type. Available file types to save as: json, csv, jsonl");
        }
        return file;
    }

    public void saveAsJsonFile(Path file, boolean includeTestCases) {
        var rows = datasetRows(includeTestCases, true);
        try {
            ensureParentDirectory(file);
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), rows);
        } catch (IOException error) {
            throw new IllegalArgumentException("The file " + file + " could not be written.", error);
        }
    }

    public void saveAsCsvFile(Path file) {
        saveAsCsvFile(file, true);
    }

    public void saveAsCsvFile(Path file, boolean includeTestCases) {
        if (multiTurn) {
            saveConversationalCsvFile(file, includeTestCases);
            return;
        }
        var header = List.of("input", "actual_output", "expected_output", "retrieval_context", "context", "name",
                "comments", "source_file", "tools_called", "expected_tools", "additional_metadata",
                "custom_column_key_values");
        var lines = new ArrayList<String>();
        lines.add(toCsvLine(header));
        var rows = new ArrayList<Golden>();
        rows.addAll(goldens);
        if (includeTestCases) {
            testCases.stream().map(Golden::from).forEach(rows::add);
        }
        if (rows.isEmpty()) {
            throw new IllegalStateException("No goldens found. Please add data before attempting to save.");
        }
        for (var golden : rows) {
            lines.add(toCsvLine(List.of(
                    csvValue(golden.input()),
                    csvValue(golden.actualOutput()),
                    csvValue(golden.expectedOutput()),
                    csvList(golden.retrievalContext()),
                    csvList(golden.context()),
                    csvValue(golden.name()),
                    csvValue(golden.comments()),
                    csvValue(golden.sourceFile()),
                    csvTools(golden.toolsCalled()),
                    csvTools(golden.expectedTools()),
                    csvJson(golden.additionalMetadata()),
                    csvJson(golden.customColumnKeyValues()))));
        }
        try {
            ensureParentDirectory(file);
            Files.writeString(file, String.join(System.lineSeparator(), lines) + System.lineSeparator());
        } catch (IOException error) {
            throw new IllegalArgumentException("The file " + file + " could not be written.", error);
        }
    }

    private void saveConversationalCsvFile(Path file, boolean includeTestCases) {
        var header = List.of("scenario", "turns", "expected_outcome", "user_description", "context", "name",
                "comments", "additional_metadata", "custom_column_key_values");
        var lines = new ArrayList<String>();
        lines.add(toCsvLine(header));
        var rows = new ArrayList<ConversationalGolden>();
        rows.addAll(conversationalGoldens);
        if (includeTestCases) {
            conversationalTestCases.stream().map(ConversationalGolden::from).forEach(rows::add);
        }
        if (rows.isEmpty()) {
            throw new IllegalStateException("No goldens found. Please add data before attempting to save.");
        }
        for (var golden : rows) {
            lines.add(toCsvLine(List.of(
                    csvValue(golden.scenario()),
                    csvTurns(golden.turns()),
                    csvValue(golden.expectedOutcome()),
                    csvValue(golden.userDescription()),
                    csvList(golden.context()),
                    csvValue(golden.name()),
                    csvValue(golden.comments()),
                    csvJson(golden.additionalMetadata()),
                    csvJson(golden.customColumnKeyValues()))));
        }
        try {
            ensureParentDirectory(file);
            Files.writeString(file, String.join(System.lineSeparator(), lines) + System.lineSeparator());
        } catch (IOException error) {
            throw new IllegalArgumentException("The file " + file + " could not be written.", error);
        }
    }

    public void saveAsJsonlFile(Path file) {
        saveAsJsonlFile(file, true);
    }

    public void saveAsJsonlFile(Path file, boolean includeTestCases) {
        var rows = jsonlRows(includeTestCases);
        var content = new StringBuilder();
        try {
            ensureParentDirectory(file);
            for (var row : rows) {
                content.append(MAPPER.writeValueAsString(row)).append(System.lineSeparator());
            }
            Files.writeString(file, content.toString());
        } catch (IOException error) {
            throw new IllegalArgumentException("The file " + file + " could not be written.", error);
        }
    }

    private ArrayNode jsonlRows(boolean includeTestCases) {
        var rows = MAPPER.createArrayNode();
        if (multiTurn) {
            conversationalGoldens.forEach(golden -> rows.add(toJsonl(golden)));
            if (includeTestCases) {
                conversationalTestCases.stream()
                        .map(ConversationalGolden::from)
                        .forEach(golden -> rows.add(toJsonl(golden)));
            }
        } else {
            goldens.forEach(golden -> rows.add(toJsonl(golden)));
            if (includeTestCases) {
                testCases.stream()
                        .map(Golden::from)
                        .forEach(golden -> rows.add(toJsonl(golden)));
            }
        }
        if (rows.isEmpty()) {
            throw new IllegalStateException("No goldens found. Please add data before attempting to save.");
        }
        return rows;
    }

    private ArrayNode datasetRows() {
        return datasetRows(true);
    }

    private ArrayNode datasetRows(boolean includeTestCases) {
        return datasetRows(includeTestCases, false);
    }

    private ArrayNode datasetRows(boolean includeTestCases, boolean includeNulls) {
        var rows = MAPPER.createArrayNode();
        if (multiTurn) {
            conversationalGoldens.forEach(golden -> rows.add(toJson(golden, includeNulls)));
            if (includeTestCases) {
                conversationalTestCases.stream()
                        .map(ConversationalGolden::from)
                        .forEach(golden -> rows.add(toJson(golden, includeNulls)));
            }
        } else {
            goldens.forEach(golden -> rows.add(toJson(golden, includeNulls)));
            if (includeTestCases) {
                testCases.stream()
                        .map(Golden::from)
                        .forEach(golden -> rows.add(toJson(golden, includeNulls)));
            }
        }
        if (rows.isEmpty()) {
            throw new IllegalStateException("No goldens found. Please add data before attempting to save.");
        }
        return rows;
    }

    private static void ensureParentDirectory(Path file) throws IOException {
        var parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private static JsonNode readJsonArray(Path file) {
        try {
            return readJsonArray(file, Files.readString(file));
        } catch (JsonProcessingException error) {
            try {
                return readJsonArray(file, Files.readString(file).replaceAll(",\\s*([\\]}])", "$1"));
            } catch (JsonProcessingException retryError) {
                throw new IllegalArgumentException("The file " + file + " is not a valid JSON file.", retryError);
            } catch (IOException retryError) {
                throw new IllegalArgumentException("The file " + file + " could not be read.", retryError);
            }
        } catch (IOException error) {
            throw new IllegalArgumentException("The file " + file + " could not be read.", error);
        }
    }

    private static JsonNode readJsonArray(Path file, String content) throws JsonProcessingException {
        var node = MAPPER.readTree(content);
        if (!node.isArray()) {
            throw new IllegalArgumentException("The file " + file + " must contain a JSON array.");
        }
        return node;
    }

    private void addGoldenFromJson(JsonNode row) {
        addGoldenFromJson(row, "input", "actual_output", "expected_output", "context", "retrieval_context",
                "tools_called", "expected_tools", "additional_metadata", "custom_column_key_values",
                "name", "comments", "source_file", "scenario", "turns", "expected_outcome", "user_description",
                null, null);
    }

    private void addGoldenFromJson(
            JsonNode row,
            String inputKeyName,
            String actualOutputKeyName,
            String expectedOutputKeyName,
            String contextKeyName,
            String retrievalContextKeyName,
            String toolsCalledKeyName,
            String expectedToolsKeyName,
            String additionalMetadataKeyName,
            String customColumnKeyValuesKeyName,
            String nameKeyName,
            String commentsKeyName,
            String sourceFileKeyName,
            String scenarioKeyName,
            String turnsKeyName,
            String expectedOutcomeKeyName,
            String userDescriptionKeyName) {
        addGoldenFromJson(row, inputKeyName, actualOutputKeyName, expectedOutputKeyName, contextKeyName,
                retrievalContextKeyName, toolsCalledKeyName, expectedToolsKeyName, additionalMetadataKeyName,
                customColumnKeyValuesKeyName, nameKeyName, commentsKeyName, sourceFileKeyName, scenarioKeyName,
                turnsKeyName, expectedOutcomeKeyName, userDescriptionKeyName, null, null);
    }

    private void addGoldenFromJson(
            JsonNode row,
            String inputKeyName,
            String actualOutputKeyName,
            String expectedOutputKeyName,
            String contextKeyName,
            String retrievalContextKeyName,
            String toolsCalledKeyName,
            String expectedToolsKeyName,
            String additionalMetadataKeyName,
            String customColumnKeyValuesKeyName,
            String nameKeyName,
            String commentsKeyName,
            String sourceFileKeyName,
            String scenarioKeyName,
            String turnsKeyName,
            String expectedOutcomeKeyName,
            String userDescriptionKeyName,
            String contextDelimiter,
            String retrievalContextDelimiter) {
        if (truthy(scenarioKeyName == null ? null : row.get(scenarioKeyName))) {
            var scenario = requiredText(row, scenarioKeyName);
            addGolden(ConversationalGolden.builder(scenario)
                    .expectedOutcome(textOrNull(row, expectedOutcomeKeyName))
                    .userDescription(textOrNull(row, userDescriptionKeyName))
                    .context(textListOrSplitOrNull(row, contextKeyName, contextDelimiter))
                    .turns(turnListOrEmptyIfMissing(row, turnsKeyName))
                    .additionalMetadata(objectMapOrNull(row, additionalMetadataKeyName))
                    .customColumnKeyValues(stringMapOrNull(row, customColumnKeyValuesKeyName))
                    .name(textOrNull(row, nameKeyName))
                    .comments(textOrNull(row, commentsKeyName))
                    .build());
            return;
        }
        addGolden(Golden.builder(requiredText(row, inputKeyName))
                .actualOutput(textOrNull(row, actualOutputKeyName))
                .expectedOutput(textOrNull(row, expectedOutputKeyName))
                .context(textListOrSplitOrNull(row, contextKeyName, contextDelimiter))
                .retrievalContext(textListOrSplitOrNull(row, retrievalContextKeyName, retrievalContextDelimiter))
                .toolsCalled(toolListOrNull(row, toolsCalledKeyName, contextDelimiter != null, contextDelimiter != null))
                .expectedTools(toolListOrNull(row, expectedToolsKeyName, contextDelimiter != null, contextDelimiter != null))
                .additionalMetadata(objectMapOrNull(row, additionalMetadataKeyName))
                .customColumnKeyValues(stringMapOrNull(row, customColumnKeyValuesKeyName))
                .name(textOrNull(row, nameKeyName))
                .comments(textOrNull(row, commentsKeyName))
                .sourceFile(textOrNull(row, sourceFileKeyName))
                .build());
    }

    private static String textOrNull(JsonNode row, String key) {
        if (key == null || !row.has(key) || row.get(key).isNull()) {
            return null;
        }
        if (!row.get(key).isTextual()) {
            throw new IllegalArgumentException("'" + key + "' must be a string");
        }
        return row.get(key).asText();
    }

    private static Double doubleOrNull(JsonNode row, String key) {
        if (key == null || !row.has(key) || row.get(key).isNull()) {
            return null;
        }
        if (!row.get(key).isNumber()) {
            throw new IllegalArgumentException("'" + key + "' must be a number");
        }
        return finiteDouble(row.get(key).asDouble(), key);
    }

    private static double finiteDouble(double value, String key) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("'" + key + "' must be a finite number");
        }
        return value;
    }

    private static String requiredText(JsonNode row, String key) {
        if (!row.has(key) || row.get(key).isNull() || !row.get(key).isTextual()) {
            throw new IllegalArgumentException("Required fields are missing in one or more JSON objects");
        }
        return row.get(key).asText();
    }

    private static boolean truthy(JsonNode node) {
        if (node == null || node.isNull()) {
            return false;
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isNumber()) {
            return node.doubleValue() != 0.0d;
        }
        if (node.isTextual()) {
            return !node.asText().isEmpty();
        }
        if (node.isArray() || node.isObject()) {
            return !node.isEmpty();
        }
        return true;
    }

    private static List<String> textListOrNull(JsonNode row, String key) {
        if (key == null || !row.has(key) || row.get(key).isNull()) {
            return null;
        }
        var node = row.get(key);
        if (!node.isArray()) {
            throw new IllegalArgumentException("'" + key + "' must be a list of strings");
        }
        var values = new ArrayList<String>();
        node.forEach(value -> {
            if (!value.isTextual()) {
                throw new IllegalArgumentException("'" + key + "' must be a list of strings");
            }
            values.add(value.asText());
        });
        return values;
    }

    private static List<String> textListOrSplitOrNull(JsonNode row, String key, String delimiter) {
        if (key == null || !row.has(key) || row.get(key).isNull()) {
            return null;
        }
        var node = row.get(key);
        if (delimiter == null) {
            return textListOrNull(row, key);
        }
        if (node.isTextual()) {
            var value = node.asText();
            return value.isEmpty() ? List.of() : List.of(value.split(java.util.regex.Pattern.quote(delimiter), -1));
        }
        return textListOrNull(row, key);
    }

    private static List<Turn> turnListOrNull(JsonNode row, String key) {
        if (key == null || !row.has(key) || row.get(key).isNull()) {
            return null;
        }
        var node = row.get(key);
        if (node.isTextual()) {
            var value = node.asText();
            if (value.isBlank()) {
                return List.of();
            }
            try {
                node = MAPPER.readTree(value);
            } catch (JsonProcessingException error) {
                throw new IllegalArgumentException("'" + key + "' must contain JSON turns", error);
            }
        }
        if (!node.isArray()) {
            throw new IllegalArgumentException("'" + key + "' must be a list of turns");
        }
        var values = new ArrayList<Turn>();
        node.forEach(turn -> values.add(toTurn(turn, key)));
        return values;
    }

    private static List<Turn> turnListOrEmptyIfMissing(JsonNode row, String key) {
        return key == null || !row.has(key) || !truthy(row.get(key)) ? List.of() : turnListOrNull(row, key);
    }

    private static List<Turn> turnListFromCsv(String value) {
        if (blank(value)) {
            return null;
        }
        try {
            var node = MAPPER.readTree(value);
            if (!node.isArray()) {
                throw new IllegalArgumentException("'turns' must be a list of turns");
            }
            var values = new ArrayList<Turn>();
            node.forEach(turn -> values.add(toTurn(turn, "turns")));
            return values;
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("'turns' must contain JSON turns", error);
        }
    }

    private static Turn toTurn(JsonNode turn, String key) {
        if (!turn.isObject()
                || !turn.has("role")
                || !turn.get("role").isTextual()
                || !turn.has("content")
                || !turn.get("content").isTextual()) {
            throw new IllegalArgumentException("'" + key + "' entries must include role and content");
        }
        return Turn.builder(turn.get("role").asText(), turn.get("content").asText())
                .userId(textOrNull(turn, "user_id"))
                .retrievalContext(textListOrNull(turn, "retrieval_context"))
                .toolsCalled(toolListOrNull(turn, "tools_called", true))
                .mcpToolsCalled(objectListOrNull(turn, "mcp_tools_called"))
                .mcpResourcesCalled(objectListOrNull(turn, "mcp_resources_called"))
                .mcpPromptsCalled(objectListOrNull(turn, "mcp_prompts_called"))
                .metadata(objectMapOrNull(turn, turn.has("metadata") ? "metadata" : "additional_metadata"))
                .build();
    }

    private static ObjectNode toJson(Golden golden) {
        return toJson(golden, false);
    }

    private static ObjectNode toJson(Golden golden, boolean includeNulls) {
        var row = MAPPER.createObjectNode();
        put(row, "input", golden.input(), includeNulls);
        put(row, "actual_output", golden.actualOutput(), includeNulls);
        put(row, "expected_output", golden.expectedOutput(), includeNulls);
        put(row, "context", golden.context(), includeNulls);
        put(row, "retrieval_context", golden.retrievalContext(), includeNulls);
        put(row, "name", golden.name(), includeNulls);
        put(row, "comments", golden.comments(), includeNulls);
        put(row, "source_file", golden.sourceFile(), includeNulls);
        putTools(row, "tools_called", golden.toolsCalled(), includeNulls);
        putTools(row, "expected_tools", golden.expectedTools(), includeNulls);
        putMap(row, "additional_metadata", golden.additionalMetadata(), includeNulls);
        putMap(row, "custom_column_key_values", golden.customColumnKeyValues(), includeNulls);
        return row;
    }

    private static ObjectNode toJson(ConversationalGolden golden) {
        return toJson(golden, false);
    }

    private static ObjectNode toJson(ConversationalGolden golden, boolean includeNulls) {
        var row = MAPPER.createObjectNode();
        put(row, "scenario", golden.scenario(), includeNulls);
        putTurns(row, emptyToNull(golden.turns()), includeNulls);
        put(row, "expected_outcome", golden.expectedOutcome(), includeNulls);
        put(row, "user_description", golden.userDescription(), includeNulls);
        put(row, "context", golden.context(), includeNulls);
        put(row, "name", golden.name(), includeNulls);
        put(row, "comments", golden.comments(), includeNulls);
        putMap(row, "additional_metadata", golden.additionalMetadata(), includeNulls);
        putMap(row, "custom_column_key_values", golden.customColumnKeyValues(), includeNulls);
        return row;
    }

    private static ObjectNode toJsonl(Golden golden) {
        var row = MAPPER.createObjectNode();
        put(row, "input", golden.input(), true);
        put(row, "actual_output", golden.actualOutput(), true);
        put(row, "expected_output", golden.expectedOutput(), true);
        put(row, "retrieval_context", jsonlList(golden.retrievalContext()), true);
        put(row, "context", jsonlList(golden.context()), true);
        putTools(row, "tools_called", emptyToNull(golden.toolsCalled()), true);
        putTools(row, "expected_tools", emptyToNull(golden.expectedTools()), true);
        putMap(row, "additional_metadata", golden.additionalMetadata(), true);
        putMap(row, "custom_column_key_values", golden.customColumnKeyValues(), true);
        return row;
    }

    private static ObjectNode toJsonl(ConversationalGolden golden) {
        var row = MAPPER.createObjectNode();
        put(row, "scenario", golden.scenario(), true);
        putTurns(row, emptyToNull(golden.turns()), true);
        put(row, "expected_outcome", golden.expectedOutcome(), true);
        put(row, "user_description", golden.userDescription(), true);
        put(row, "context", golden.context(), true);
        put(row, "name", golden.name(), true);
        put(row, "comments", golden.comments(), true);
        putMap(row, "additional_metadata", golden.additionalMetadata(), true);
        putMap(row, "custom_column_key_values", golden.customColumnKeyValues(), true);
        return row;
    }

    private static void put(ObjectNode row, String key, String value) {
        put(row, key, value, false);
    }

    private static void put(ObjectNode row, String key, String value, boolean includeNulls) {
        if (value != null) {
            row.put(key, value);
        } else if (includeNulls) {
            row.putNull(key);
        }
    }

    private static void put(ObjectNode row, String key, List<?> values) {
        put(row, key, values, false);
    }

    private static void put(ObjectNode row, String key, List<?> values, boolean includeNulls) {
        if (values == null) {
            if (includeNulls) {
                row.putNull(key);
            }
            return;
        }
        ArrayNode array = row.putArray(key);
        values.forEach(value -> array.add(RetrievedContextData.markerValue(value)));
    }

    private static String jsonlList(List<?> values) {
        return values == null ? null : String.join("|", RetrievedContextData.markerValues(values));
    }

    private static <T> List<T> emptyToNull(List<T> values) {
        return values == null || values.isEmpty() ? null : values;
    }

    private static List<ToolCall> toolListOrNull(JsonNode row, String key) {
        return toolListOrNull(row, key, true, false);
    }

    private static List<ToolCall> toolListOrEmptyIfMissing(JsonNode row, String key, boolean parseText) {
        if (key == null || !row.has(key)) {
            return List.of();
        }
        if (row.get(key).isNull()) {
            throw new IllegalArgumentException("'" + key + "' must be a list of tool calls");
        }
        return toolListOrNull(row, key, parseText, false);
    }

    private static List<ToolCall> toolListOrNull(JsonNode row, String key, boolean parseText) {
        return toolListOrNull(row, key, parseText, false);
    }

    private static List<ToolCall> toolListOrNull(JsonNode row, String key, boolean parseText, boolean emptyAsNull) {
        if (key == null || !row.has(key) || row.get(key).isNull()) {
            return null;
        }
        var node = row.get(key);
        if (emptyAsNull && !truthy(node)) {
            return null;
        }
        if (node.isTextual()) {
            if (!parseText) {
                throw new IllegalArgumentException("'" + key + "' must be a list of tool calls");
            }
            var value = node.asText();
            if (value.isBlank()) {
                return null;
            }
            try {
                node = MAPPER.readTree(value);
            } catch (JsonProcessingException error) {
                try {
                    node = MAPPER.readTree(value.replaceAll(",\\s*([\\]}])", "$1"));
                } catch (JsonProcessingException retryError) {
                    throw new IllegalArgumentException("'" + key + "' must contain JSON tool calls", retryError);
                }
            }
        }
        if (!node.isArray()) {
            throw new IllegalArgumentException("'" + key + "' must be a list of tool calls");
        }
        if (emptyAsNull && node.isEmpty()) {
            return null;
        }
        var values = new ArrayList<ToolCall>();
        node.forEach(tool -> values.add(toToolCall(tool, key)));
        return values;
    }

    private static List<ToolCall> toolListFromCsv(String value, String key) {
        return toolListFromCsv(value, key, null);
    }

    private static List<ToolCall> toolListFromCsv(String value, String key, String nameDelimiter) {
        if (blank(value)) {
            return List.of();
        }
        try {
            var node = MAPPER.readTree(value);
            if (!node.isArray()) {
                throw new IllegalArgumentException("'" + key + "' must be a list of tool calls");
            }
            var tools = new ArrayList<ToolCall>();
            node.forEach(tool -> tools.add(toToolCall(tool, key)));
            return tools;
        } catch (JsonProcessingException error) {
            if (nameDelimiter != null) {
                return java.util.Arrays.stream(value.split(java.util.regex.Pattern.quote(nameDelimiter), -1))
                        .filter(name -> !name.isBlank())
                        .map(ToolCall::new)
                        .toList();
            }
            throw new IllegalArgumentException("'" + key + "' must contain JSON tool calls", error);
        }
    }

    private static ToolCall toToolCall(JsonNode tool, String key) {
        if (!tool.isObject() || !tool.has("name") || tool.get("name").isNull() || !tool.get("name").isTextual()) {
            throw new IllegalArgumentException("'" + key + "' entries must include a name");
        }
        return new ToolCall(
                tool.get("name").asText(),
                toolTextOrNull(tool, "description", key),
                toolTextOrNull(tool, "reasoning", key),
                inputParametersOrNull(tool),
                tool.has("output") && !tool.get("output").isNull()
                        ? MAPPER.convertValue(tool.get("output"), Object.class)
                        : null);
    }

    private static String toolTextOrNull(JsonNode tool, String field, String key) {
        if (!tool.has(field) || tool.get(field).isNull()) {
            return null;
        }
        if (!tool.get(field).isTextual()) {
            throw new IllegalArgumentException("'" + key + "' entries must include string " + field);
        }
        return tool.get(field).asText();
    }

    private static Map<String, Object> inputParametersOrNull(JsonNode tool) {
        var node = tool.has("input_parameters") ? tool.get("input_parameters") : tool.get("inputParameters");
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isObject()) {
            throw new IllegalArgumentException("'input_parameters' must be an object");
        }
        return MAPPER.convertValue(node, new TypeReference<>() {});
    }

    private static Map<String, Object> objectMapOrNull(JsonNode row, String key) {
        if (key == null || !row.has(key) || row.get(key).isNull()) {
            return null;
        }
        var node = row.get(key);
        if (!node.isObject()) {
            throw new IllegalArgumentException("'" + key + "' must be an object");
        }
        return MAPPER.convertValue(node, new TypeReference<>() {});
    }

    private static Map<String, Object> metadataMapOrNull(JsonNode row, String key) {
        if (key == null) {
            return null;
        }
        var alias = metadataAlias(key);
        return row.has(key) || alias == null ? objectMapOrNull(row, key) : objectMapOrNull(row, alias);
    }

    private static Map<String, Object> objectMapFromCsv(String value, String key) {
        if (blank(value)) {
            return null;
        }
        try {
            var node = MAPPER.readTree(value);
            if (!node.isObject()) {
                throw new IllegalArgumentException("'" + key + "' must be an object");
            }
            return MAPPER.convertValue(node, new TypeReference<>() {});
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("'" + key + "' must contain a JSON object", error);
        }
    }

    private static Map<String, Object> metadataMapFromCsv(Map<String, String> row, String key) {
        if (key == null) {
            return null;
        }
        var value = row.get(key);
        var alias = metadataAlias(key);
        if (blank(value) && alias != null) {
            value = row.get(alias);
        }
        return objectMapFromCsv(value, key);
    }

    private static String metadataAlias(String key) {
        if ("metadata".equals(key)) {
            return "additional_metadata";
        }
        if ("additional_metadata".equals(key)) {
            return "metadata";
        }
        return null;
    }

    private static List<Map<String, Object>> objectListFromCsv(String value, String key) {
        if (blank(value)) {
            return null;
        }
        try {
            var node = MAPPER.readTree(value);
            if (!node.isArray()) {
                throw new IllegalArgumentException("'" + key + "' must be a list of objects");
            }
            node.forEach(entry -> {
                if (!entry.isObject()) {
                    throw new IllegalArgumentException("'" + key + "' must be a list of objects");
                }
            });
            return MAPPER.convertValue(node, new TypeReference<>() {});
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("'" + key + "' must contain JSON objects", error);
        }
    }

    private static List<Map<String, Object>> objectListOrNull(JsonNode row, String key) {
        if (key == null || !row.has(key) || row.get(key).isNull()) {
            return null;
        }
        var node = row.get(key);
        if (node.isTextual()) {
            var value = node.asText();
            if (value.isBlank()) {
                return null;
            }
            try {
                node = MAPPER.readTree(value);
            } catch (JsonProcessingException error) {
                throw new IllegalArgumentException("'" + key + "' must contain JSON objects", error);
            }
        }
        if (!node.isArray()) {
            throw new IllegalArgumentException("'" + key + "' must be a list of objects");
        }
        return MAPPER.convertValue(node, new TypeReference<>() {});
    }

    private static Map<String, String> stringMapOrNull(JsonNode row, String key) {
        if (key == null || !row.has(key) || row.get(key).isNull()) {
            return null;
        }
        var node = row.get(key);
        if (!node.isObject()) {
            throw new IllegalArgumentException("'" + key + "' must be an object");
        }
        node.fields().forEachRemaining(entry -> {
            if (!entry.getValue().isTextual()) {
                throw new IllegalArgumentException("'" + key + "' must contain string keys and values");
            }
        });
        return MAPPER.convertValue(node, new TypeReference<>() {});
    }

    private static Map<String, String> stringMapFromCsv(String value) {
        if (blank(value)) {
            return null;
        }
        try {
            var node = MAPPER.readTree(value);
            if (!node.isObject()) {
                throw new IllegalArgumentException("'custom_column_key_values' must contain a JSON object");
            }
            node.fields().forEachRemaining(entry -> {
                if (!entry.getValue().isTextual()) {
                    throw new IllegalArgumentException("'custom_column_key_values' must contain string keys and values");
                }
            });
            return MAPPER.convertValue(node, new TypeReference<>() {});
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("'custom_column_key_values' must contain a JSON object", error);
        }
    }

    private static Double doubleFromCsv(String value, String key) {
        if (blank(value)) {
            return null;
        }
        try {
            return finiteDouble(Double.valueOf(value), key);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("'" + key + "' must be a number", error);
        }
    }

    private static void putTools(ObjectNode row, String key, List<ToolCall> tools) {
        putTools(row, key, tools, false);
    }

    private static void putTools(ObjectNode row, String key, List<ToolCall> tools, boolean includeNulls) {
        if (tools == null || tools.isEmpty()) {
            if (includeNulls) {
                row.putNull(key);
            }
            return;
        }
        ArrayNode array = row.putArray(key);
        for (var tool : tools) {
            var node = array.addObject();
            put(node, "name", tool.name());
            put(node, "description", tool.description());
            put(node, "reasoning", tool.reasoning());
            if (tool.inputParameters() != null) {
                node.set("inputParameters", MAPPER.valueToTree(tool.inputParameters()));
            }
            if (tool.output() != null) {
                node.set("output", MAPPER.valueToTree(tool.output()));
            }
        }
    }

    private static void putTurns(ObjectNode row, List<Turn> turns) {
        putTurns(row, turns, false);
    }

    private static void putTurns(ObjectNode row, List<Turn> turns, boolean includeNulls) {
        if (turns == null) {
            if (includeNulls) {
                row.putNull("turns");
            }
            return;
        }
        var array = row.putArray("turns");
        for (var turn : turns) {
            var node = array.addObject();
            put(node, "role", turn.role(), includeNulls);
            put(node, "content", turn.content(), includeNulls);
            put(node, "user_id", turn.userId(), includeNulls);
            put(node, "retrieval_context", turn.retrievalContext(), includeNulls);
            putTools(node, "tools_called", turn.toolsCalled(), includeNulls);
            putObjectList(node, "mcp_tools_called", turn.mcpToolsCalled(), includeNulls);
            putObjectList(node, "mcp_resources_called", turn.mcpResourcesCalled(), includeNulls);
            putObjectList(node, "mcp_prompts_called", turn.mcpPromptsCalled(), includeNulls);
            putMap(node, "metadata", turn.metadata(), includeNulls);
        }
    }

    private static void putMap(ObjectNode row, String key, Map<?, ?> values) {
        putMap(row, key, values, false);
    }

    private static void putMap(ObjectNode row, String key, Map<?, ?> values, boolean includeNulls) {
        if (values != null) {
            row.set(key, MAPPER.valueToTree(values));
        } else if (includeNulls) {
            row.putNull(key);
        }
    }

    private static void putObjectList(ObjectNode row, String key, List<Map<String, Object>> values) {
        putObjectList(row, key, values, false);
    }

    private static void putObjectList(ObjectNode row, String key, List<Map<String, Object>> values,
            boolean includeNulls) {
        if (values != null && !values.isEmpty()) {
            row.set(key, MAPPER.valueToTree(values));
        } else if (includeNulls) {
            row.putNull(key);
        }
    }

    private static Map<String, String> csvRow(List<String> headers, List<String> values) {
        var row = new LinkedHashMap<String, String>();
        for (var i = 0; i < headers.size(); i++) {
            row.put(headers.get(i), i < values.size() ? values.get(i) : "");
        }
        return row;
    }

    private static List<String> parseCsvLine(String line) {
        var values = new ArrayList<String>();
        var value = new StringBuilder();
        var quoted = false;
        for (var i = 0; i < line.length(); i++) {
            var ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    value.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                values.add(value.toString());
                value.setLength(0);
            } else {
                value.append(ch);
            }
        }
        values.add(value.toString());
        return values;
    }

    private static String toCsvLine(List<String> values) {
        return values.stream().map(EvaluationDataset::csvCell).reduce((left, right) -> left + "," + right).orElse("");
    }

    private static String csvCell(String value) {
        if (value == null) {
            return "";
        }
        if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static String csvValue(String value) {
        return value == null ? "" : value;
    }

    private static String csvList(List<?> values) {
        return values == null ? "" : String.join("|", RetrievedContextData.markerValues(values));
    }

    private static List<String> csvListOrNull(String value) {
        return csvListOrNull(value, "|");
    }

    private static List<String> csvListOrNull(String value, String delimiter) {
        return blank(value) ? List.of() : List.of(value.split(java.util.regex.Pattern.quote(delimiter), -1));
    }

    private static String csvJson(Object value) {
        if (value == null) {
            return "";
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Could not serialize CSV value.", error);
        }
    }

    private static String csvTools(List<ToolCall> tools) {
        if (tools == null) {
            return "";
        }
        var row = MAPPER.createObjectNode();
        putTools(row, "tools", tools);
        return csvJson(row.get("tools"));
    }

    private static String csvTurns(List<Turn> turns) {
        if (turns == null) {
            return "";
        }
        var row = MAPPER.createObjectNode();
        putTurns(row, turns, true);
        return csvJson(row.get("turns"));
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String nullIfBlank(String value) {
        return blank(value) ? null : value;
    }

    private static Synthesizer requireSynthesizer(Synthesizer synthesizer) {
        return Objects.requireNonNull(synthesizer, "synthesizer");
    }
}
