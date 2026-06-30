package dev.jeval.synthesizer;

import dev.jeval.EvaluationModel;
import dev.jeval.EvaluationDataset;
import dev.jeval.ConversationalGolden;
import dev.jeval.Golden;
import dev.jeval.Utils;
import dev.jeval.synthesizer.SynthesizerSchemas.SyntheticData;
import dev.jeval.synthesizer.SynthesizerSchemas.ConversationalData;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.function.IntFunction;

public final class Synthesizer {
    private final EvaluationModel model;
    private final StylingConfig stylingConfig;
    private final ConversationalStylingConfig conversationalStylingConfig;
    private final EvolutionConfig evolutionConfig;
    private final FiltrationConfig filtrationConfig;
    private final SynthesizerOptions options;
    private List<Golden> syntheticGoldens = List.of();
    private List<ConversationalGolden> syntheticConversationalGoldens = List.of();

    public Synthesizer(EvaluationModel model) {
        this(model, null, null, new EvolutionConfig());
    }

    public Synthesizer(EvaluationModel model, StylingConfig stylingConfig) {
        this(model, stylingConfig, null, new EvolutionConfig());
    }

    public Synthesizer(EvaluationModel model, ConversationalStylingConfig conversationalStylingConfig) {
        this(model, null, conversationalStylingConfig, new EvolutionConfig());
    }

    public Synthesizer(EvaluationModel model, StylingConfig stylingConfig, EvolutionConfig evolutionConfig) {
        this(model, stylingConfig, null, evolutionConfig);
    }

    public Synthesizer(
            EvaluationModel model,
            StylingConfig stylingConfig,
            ConversationalStylingConfig conversationalStylingConfig,
            EvolutionConfig evolutionConfig) {
        this(model, stylingConfig, conversationalStylingConfig, evolutionConfig, new FiltrationConfig(), SynthesizerOptions.DEFAULT);
    }

    public Synthesizer(
            EvaluationModel model,
            StylingConfig stylingConfig,
            ConversationalStylingConfig conversationalStylingConfig,
            EvolutionConfig evolutionConfig,
            SynthesizerOptions options) {
        this(model, stylingConfig, conversationalStylingConfig, evolutionConfig, new FiltrationConfig(), options);
    }

    public Synthesizer(
            EvaluationModel model,
            StylingConfig stylingConfig,
            ConversationalStylingConfig conversationalStylingConfig,
            EvolutionConfig evolutionConfig,
            FiltrationConfig filtrationConfig,
            SynthesizerOptions options) {
        this.model = Objects.requireNonNull(model, "model");
        this.stylingConfig = stylingConfig;
        this.conversationalStylingConfig = conversationalStylingConfig;
        this.evolutionConfig = evolutionConfig == null ? new EvolutionConfig() : evolutionConfig;
        this.filtrationConfig = filtrationConfig == null ? new FiltrationConfig() : filtrationConfig;
        this.options = options == null ? SynthesizerOptions.DEFAULT : options;
    }

    public SynthesizerOptions options() {
        return options;
    }

    public Path saveAs(String fileType, Path directory, String fileName, boolean quiet) {
        if (fileName != null && fileName.contains(".")) {
            throw new IllegalArgumentException("file_name should not contain periods or file extensions. "
                    + "The file extension will be added based on the file_type parameter.");
        }
        if (syntheticGoldens.isEmpty() && syntheticConversationalGoldens.isEmpty()) {
            throw new IllegalStateException(
                    "No synthetic goldens found. Please generate goldens before saving goldens.");
        }
        var dataset = syntheticGoldens.isEmpty()
                ? new EvaluationDataset(syntheticConversationalGoldens)
                : new EvaluationDataset(syntheticGoldens);
        var path = dataset.saveAs(fileType, directory, fileName);
        if (!quiet) {
            System.out.println("Synthetic goldens saved at " + path + "!");
        }
        return path;
    }

    public List<Golden> generateGoldensFromContexts(List<List<String>> contexts) {
        return generateGoldensFromContexts(contexts, true, 2, null);
    }

    public CompletableFuture<List<Golden>> generateGoldensFromContextsAsync(List<List<String>> contexts) {
        return generateGoldensFromContextsAsync(contexts, true, 2, null);
    }

    public CompletableFuture<List<Golden>> generateGoldensFromContextsAsync(
            List<List<String>> contexts,
            boolean includeExpectedOutput,
            int maxGoldensPerContext,
            List<?> sourceFiles) {
        return CompletableFuture.supplyAsync(
                () -> generateGoldensFromContexts(contexts, includeExpectedOutput, maxGoldensPerContext, sourceFiles));
    }

    public List<Golden> generateGoldensFromDocs(List<Path> documentPaths) throws IOException {
        return generateGoldensFromDocs(documentPaths, true, 2, ContextConstructionConfig.DEFAULT);
    }

    public CompletableFuture<List<Golden>> generateGoldensFromDocsAsync(List<Path> documentPaths) {
        return generateGoldensFromDocsAsync(documentPaths, true, 2, ContextConstructionConfig.DEFAULT);
    }

    public CompletableFuture<List<Golden>> generateGoldensFromDocsAsync(
            List<Path> documentPaths,
            boolean includeExpectedOutput,
            int maxGoldensPerContext,
            ContextConstructionConfig contextConstructionConfig) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return generateGoldensFromDocs(documentPaths, includeExpectedOutput, maxGoldensPerContext,
                        contextConstructionConfig);
            } catch (IOException error) {
                throw new CompletionException(error);
            }
        });
    }

    public List<Golden> generateGoldensFromDocs(
            List<Path> documentPaths,
            boolean includeExpectedOutput,
            int maxGoldensPerContext,
            ContextConstructionConfig contextConstructionConfig) throws IOException {
        var contexts = new ArrayList<List<String>>();
        var sourceFiles = new ArrayList<String>();
        var contextScores = new ArrayList<Double>();
        addDocumentContexts(documentPaths, config(contextConstructionConfig), contexts, sourceFiles, contextScores);
        return generateGoldensFromContexts(
                contexts, includeExpectedOutput, maxGoldensPerContext, sourceFiles, contextScores);
    }

    public List<Golden> generateGoldensFromContexts(
            List<List<String>> contexts,
            boolean includeExpectedOutput,
            int maxGoldensPerContext,
            List<?> sourceFiles) {
        var goldens = new ArrayList<Golden>();
        for (var batch : generateContextBatches(contexts.size(),
                index -> generateGoldensForContext(
                        index, contexts, includeExpectedOutput, maxGoldensPerContext, sourceFiles, null))) {
            goldens.addAll(batch);
        }
        return retainGoldens(goldens);
    }

    private List<Golden> generateGoldensFromContexts(
            List<List<String>> contexts,
            boolean includeExpectedOutput,
            int maxGoldensPerContext,
            List<?> sourceFiles,
            List<Double> contextScores) {
        var goldens = new ArrayList<Golden>();
        for (var batch : generateContextBatches(contexts.size(),
                index -> generateGoldensForContext(
                        index, contexts, includeExpectedOutput, maxGoldensPerContext, sourceFiles, contextScores))) {
            goldens.addAll(batch);
        }
        return retainGoldens(goldens);
    }

    private List<Golden> generateGoldensForContext(
            int contextIndex,
            List<List<String>> contexts,
            boolean includeExpectedOutput,
            int maxGoldensPerContext,
            List<?> sourceFiles,
            List<Double> contextScores) {
        var goldens = new ArrayList<Golden>();
        var context = List.copyOf(contexts.get(contextIndex));
        var contextSourceFiles = contextSourceFiles(sourceFiles, contextIndex);
        var sourceFile = contextSourceFiles.isEmpty() ? null : contextSourceFiles.getFirst();
        var contextQuality = contextScores != null && contextIndex < contextScores.size()
                ? contextScores.get(contextIndex)
                : null;
        var data = SynthesizerSchemas.parseSyntheticData(
                model.generate(SynthesizerPrompts.generateSyntheticInputs(
                        context, maxGoldensPerContext, includeExpectedOutput, contextSourceFiles)));
        var qualifiedData = rewriteInputs(context, data);
        for (var item : qualifiedData.stream().limit(maxGoldensPerContext).toList()) {
            goldens.add(golden(item.data(), context, sourceFile, includeExpectedOutput,
                    item.score(),
                    contextQuality,
                    contextSourceFiles,
                    contextIndex * maxGoldensPerContext + goldens.size()));
        }
        return List.copyOf(goldens);
    }

    public List<Golden> generateGoldensFromScratch(int numGoldens) {
        validateScratchStylingConfig(stylingConfig);
        return generateGoldensFromScratch(numGoldens, stylingConfig);
    }

    public CompletableFuture<List<Golden>> generateGoldensFromScratchAsync(int numGoldens) {
        return CompletableFuture.supplyAsync(() -> generateGoldensFromScratch(numGoldens));
    }

    private List<Golden> generateGoldensFromScratch(int numGoldens, StylingConfig activeStylingConfig) {
        var data = SynthesizerSchemas.parseSyntheticData(model.generate(
                SynthesizerPrompts.generateSyntheticInputsFromScratch(
                        activeStylingConfig.scenario(), activeStylingConfig.task(),
                        activeStylingConfig.inputFormat(), numGoldens)));
        var qualifiedData = rewriteInputs(List.of(), data);
        var goldens = new ArrayList<Golden>();
        for (var item : qualifiedData) {
            goldens.add(golden(
                    item.data(), null, null, false, item.score(), null, List.of(), goldens.size(),
                    activeStylingConfig));
        }
        return retainGoldens(goldens);
    }

    public List<Golden> generateGoldensFromGoldens(
            List<Golden> goldens,
            int maxGoldensPerGolden,
            boolean includeExpectedOutput) {
        var contexts = new ArrayList<List<String>>();
        var sourceFiles = new ArrayList<String>();
        var inputs = new ArrayList<String>();
        for (var golden : goldens) {
            if (golden.context() != null && !golden.context().isEmpty()) {
                contexts.add(golden.context());
                sourceFiles.add(golden.sourceFile());
            } else {
                inputs.add(golden.input());
            }
        }
        var generated = new ArrayList<Golden>();
        if (!contexts.isEmpty()) {
            generated.addAll(generateGoldensFromContexts(contexts, includeExpectedOutput, maxGoldensPerGolden, sourceFiles));
        } else if (!inputs.isEmpty()) {
            var activeStylingConfig = stylingConfig == null
                    ? extractStylingConfig(inputs)
                    : stylingConfig;
            validateScratchStylingConfig(activeStylingConfig);
            generated.addAll(generateGoldensFromScratch(inputs.size() * maxGoldensPerGolden, activeStylingConfig));
        }
        return retainGoldens(generated);
    }

    public List<ConversationalGolden> generateConversationalGoldensFromContexts(
            List<List<String>> contexts,
            boolean includeExpectedOutcome,
            int maxGoldensPerContext,
            List<?> sourceFiles) {
        var goldens = new ArrayList<ConversationalGolden>();
        for (var batch : generateContextBatches(contexts.size(),
                index -> generateConversationalGoldensForContext(index, contexts, includeExpectedOutcome,
                        maxGoldensPerContext, sourceFiles, null))) {
            goldens.addAll(batch);
        }
        return retainConversationalGoldens(goldens);
    }

    public CompletableFuture<List<ConversationalGolden>> generateConversationalGoldensFromContextsAsync(
            List<List<String>> contexts,
            boolean includeExpectedOutcome,
            int maxGoldensPerContext,
            List<?> sourceFiles) {
        return CompletableFuture.supplyAsync(() -> generateConversationalGoldensFromContexts(
                contexts,
                includeExpectedOutcome,
                maxGoldensPerContext,
                sourceFiles));
    }

    public List<ConversationalGolden> generateConversationalGoldensFromDocs(List<Path> documentPaths)
            throws IOException {
        return generateConversationalGoldensFromDocs(documentPaths, true, 2, ContextConstructionConfig.DEFAULT);
    }

    public CompletableFuture<List<ConversationalGolden>> generateConversationalGoldensFromDocsAsync(
            List<Path> documentPaths) {
        return generateConversationalGoldensFromDocsAsync(
                documentPaths, true, 2, ContextConstructionConfig.DEFAULT);
    }

    public CompletableFuture<List<ConversationalGolden>> generateConversationalGoldensFromDocsAsync(
            List<Path> documentPaths,
            boolean includeExpectedOutcome,
            int maxGoldensPerContext,
            ContextConstructionConfig contextConstructionConfig) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return generateConversationalGoldensFromDocs(
                        documentPaths,
                        includeExpectedOutcome,
                        maxGoldensPerContext,
                        contextConstructionConfig);
            } catch (IOException error) {
                throw new CompletionException(error);
            }
        });
    }

    public List<ConversationalGolden> generateConversationalGoldensFromDocs(
            List<Path> documentPaths,
            boolean includeExpectedOutcome,
            int maxGoldensPerContext,
            ContextConstructionConfig contextConstructionConfig) throws IOException {
        var contexts = new ArrayList<List<String>>();
        var sourceFiles = new ArrayList<String>();
        var contextScores = new ArrayList<Double>();
        addDocumentContexts(documentPaths, config(contextConstructionConfig), contexts, sourceFiles, contextScores);
        return generateConversationalGoldensFromContexts(
                contexts, includeExpectedOutcome, maxGoldensPerContext, sourceFiles, contextScores);
    }

    private List<ConversationalGolden> generateConversationalGoldensFromContexts(
            List<List<String>> contexts,
            boolean includeExpectedOutcome,
            int maxGoldensPerContext,
            List<?> sourceFiles,
            List<Double> contextScores) {
        var goldens = new ArrayList<ConversationalGolden>();
        for (var batch : generateContextBatches(contexts.size(),
                index -> generateConversationalGoldensForContext(index, contexts, includeExpectedOutcome,
                        maxGoldensPerContext, sourceFiles, contextScores))) {
            goldens.addAll(batch);
        }
        return retainConversationalGoldens(goldens);
    }

    private List<ConversationalGolden> generateConversationalGoldensForContext(
            int contextIndex,
            List<List<String>> contexts,
            boolean includeExpectedOutcome,
            int maxGoldensPerContext,
            List<?> sourceFiles,
            List<Double> contextScores) {
        var goldens = new ArrayList<ConversationalGolden>();
        var context = List.copyOf(contexts.get(contextIndex));
        var contextSourceFiles = contextSourceFiles(sourceFiles, contextIndex);
        var sourceFile = contextSourceFiles.isEmpty() ? null : contextSourceFiles.getFirst();
        var contextQuality = contextScores != null && contextIndex < contextScores.size()
                ? contextScores.get(contextIndex)
                : null;
        var data = SynthesizerSchemas.parseConversationalData(model.generate(
                SynthesizerPrompts.generateSyntheticConversationalScenarios(
                        context, maxGoldensPerContext, conversationalStylingConfig, includeExpectedOutcome,
                        contextSourceFiles)));
        var qualifiedData = rewriteScenarios(context, data);
        for (var item : qualifiedData.stream().limit(maxGoldensPerContext).toList()) {
            goldens.add(conversationalGolden(
                    item.data(), context, sourceFile, includeExpectedOutcome, item.score(), contextQuality,
                    contextSourceFiles, contextIndex * maxGoldensPerContext + goldens.size()));
        }
        return List.copyOf(goldens);
    }

    public List<ConversationalGolden> generateConversationalGoldensFromScratch(int numGoldens) {
        validateConversationalScratchStylingConfig();
        var data = SynthesizerSchemas.parseConversationalData(model.generate(
                        SynthesizerPrompts.generateSyntheticConversationalScenariosFromScratch(
                                conversationalStylingConfig, numGoldens)));
        var qualifiedData = rewriteScenarios(List.of(), data);
        var goldens = new ArrayList<ConversationalGolden>();
        for (var item : qualifiedData) {
            goldens.add(conversationalGolden(item.data(), null, null, false, item.score(), null, List.of(),
                    goldens.size()));
        }
        return retainConversationalGoldens(goldens);
    }

    public CompletableFuture<List<ConversationalGolden>> generateConversationalGoldensFromScratchAsync(int numGoldens) {
        return CompletableFuture.supplyAsync(() -> generateConversationalGoldensFromScratch(numGoldens));
    }

    public List<ConversationalGolden> generateConversationalGoldensFromGoldens(
            List<ConversationalGolden> goldens,
            int maxGoldensPerGolden,
            boolean includeExpectedOutcome) {
        var contexts = new ArrayList<List<String>>();
        var scenarios = new ArrayList<String>();
        for (var golden : goldens) {
            if (golden.context() != null && !golden.context().isEmpty()) {
                contexts.add(golden.context());
            } else {
                scenarios.add(golden.scenario());
            }
        }
        var generated = new ArrayList<ConversationalGolden>();
        if (!contexts.isEmpty()) {
            generated.addAll(generateConversationalGoldensFromContexts(
                    contexts, includeExpectedOutcome, maxGoldensPerGolden, null));
        } else if (!scenarios.isEmpty()) {
            var data = SynthesizerSchemas.parseConversationalData(model.generate(
                    SynthesizerPrompts.generateSyntheticConversationalScenariosFromGoldens(
                            scenarios, scenarios.size() * maxGoldensPerGolden, includeExpectedOutcome)));
            var qualifiedData = rewriteScenarios(scenarios, data);
            for (var item : qualifiedData) {
                generated.add(conversationalGolden(
                        item.data(), null, null, includeExpectedOutcome, item.score(), null, List.of(),
                        generated.size()));
            }
        }
        return retainConversationalGoldens(generated);
    }

    private List<Golden> retainGoldens(List<Golden> goldens) {
        syntheticGoldens = List.copyOf(goldens);
        syntheticConversationalGoldens = List.of();
        return syntheticGoldens;
    }

    private List<ConversationalGolden> retainConversationalGoldens(List<ConversationalGolden> goldens) {
        syntheticConversationalGoldens = List.copyOf(goldens);
        syntheticGoldens = List.of();
        return syntheticConversationalGoldens;
    }

    private ConversationalGolden conversationalGolden(
            ConversationalData data,
            List<String> context,
            String sourceFile,
            boolean includeExpectedOutcome,
            Double syntheticScenarioQuality,
            Double contextQuality,
            List<String> contextSourceFiles,
            int goldenIndex) {
        var evolutions = new ArrayList<String>();
        var scenario = data.scenario();
        for (var i = 0; i < evolutionConfig.numEvolutions(); i++) {
            var evolution = evolution(goldenIndex + i);
            scenario = SynthesizerSchemas.parseRewrittenScenario(model.generate(
                    SynthesizerPrompts.evolveScenario(scenario, context, evolution)));
            evolutions.add(evolution.value());
        }
        if (shouldStyle(conversationalStylingConfig)) {
            scenario = SynthesizerSchemas.parseScenario(model.generate(
                    SynthesizerPrompts.rewriteEvolvedScenario(scenario, conversationalStylingConfig)));
        }
        String expectedOutcome = null;
        if (includeExpectedOutcome) {
            expectedOutcome = model.generate(SynthesizerPrompts.generateConversationalExpectedOutcome(
                    scenario,
                    context,
                    conversationalStylingConfig == null ? null : conversationalStylingConfig.expectedOutcomeFormat()));
        }
        return ConversationalGolden.builder(scenario)
                .turns(data.turns())
                .expectedOutcome(expectedOutcome)
                .userDescription(data.userDescription())
                .context(context)
                .additionalMetadata(metadata(
                        evolutions, data, sourceFile, syntheticScenarioQuality, contextQuality, contextSourceFiles))
                .build();
    }

    private Golden golden(
            SyntheticData data,
            List<String> context,
            String sourceFile,
            boolean includeExpectedOutput,
            Double syntheticInputQuality,
            Double contextQuality,
            List<String> contextSourceFiles,
            int goldenIndex) {
        return golden(data, context, sourceFile, includeExpectedOutput, syntheticInputQuality,
                contextQuality, contextSourceFiles, goldenIndex, stylingConfig);
    }

    private Golden golden(
            SyntheticData data,
            List<String> context,
            String sourceFile,
            boolean includeExpectedOutput,
            Double syntheticInputQuality,
            Double contextQuality,
            List<String> contextSourceFiles,
            int goldenIndex,
            StylingConfig activeStylingConfig) {
        var evolutions = new ArrayList<String>();
        var input = data.input();
        for (var i = 0; i < evolutionConfig.numEvolutions(); i++) {
            var evolution = evolution(goldenIndex + i);
            input = SynthesizerSchemas.parseRewrittenInput(model.generate(SynthesizerPrompts.evolveInput(input, evolution)));
            evolutions.add(evolution.value());
        }
        if (shouldStyle(activeStylingConfig)) {
            input = SynthesizerSchemas.parseInput(model.generate(
                    SynthesizerPrompts.rewriteEvolvedInput(input, activeStylingConfig)));
        }
        var expectedOutput = includeExpectedOutput ? expectedOutput(data, context, input, activeStylingConfig) : null;
        return Golden.builder(input)
                .expectedOutput(expectedOutput)
                .context(context)
                .sourceFile(sourceFile)
                .additionalMetadata(metadata(
                        evolutions, data, sourceFile, syntheticInputQuality, contextQuality, contextSourceFiles))
                .build();
    }

    private String expectedOutput(
            SyntheticData data,
            List<String> context,
            String input,
            StylingConfig activeStylingConfig) {
        if (context == null) {
            return data.expectedOutput();
        }
        return model.generate(SynthesizerPrompts.generateExpectedOutput(
                context, input, activeStylingConfig == null ? null : activeStylingConfig.expectedOutputFormat()));
    }

    private List<QualifiedSyntheticData> rewriteInputs(List<String> context, List<SyntheticData> data) {
        if (filtrationConfig.maxQualityRetries() == 0) {
            return data.stream()
                    .map(item -> new QualifiedSyntheticData(item, null))
                    .toList();
        }
        var filtered = new ArrayList<QualifiedSyntheticData>();
        var critic = filtrationConfig.criticModel() == null ? model : filtrationConfig.criticModel();
        for (var item : data) {
            var input = item.input();
            var rewritten = false;
            Double quality = null;
            for (var i = 0; i < filtrationConfig.maxQualityRetries(); i++) {
                var feedback = SynthesizerSchemas.parseInputFeedback(
                        critic.generate(SynthesizerPrompts.evaluateSyntheticInput(input)));
                quality = feedback.score();
                if (feedback.score() >= filtrationConfig.syntheticInputQualityThreshold()) {
                    break;
                }
                input = SynthesizerSchemas.parseRewrittenInput(model.generate(
                        SynthesizerPrompts.rewriteSyntheticInput(context, input, feedback.feedback())));
                rewritten = true;
            }
            filtered.add(new QualifiedSyntheticData(
                    new SyntheticData(input, rewritten ? null : item.expectedOutput(), item.usedSourceFiles()),
                    quality));
        }
        return List.copyOf(filtered);
    }

    private List<QualifiedConversationalData> rewriteScenarios(List<String> context, List<ConversationalData> data) {
        if (filtrationConfig.maxQualityRetries() == 0) {
            return data.stream()
                    .map(item -> new QualifiedConversationalData(item, null))
                    .toList();
        }
        var filtered = new ArrayList<QualifiedConversationalData>();
        var critic = filtrationConfig.criticModel() == null ? model : filtrationConfig.criticModel();
        for (var item : data) {
            var scenario = item.scenario();
            var rewritten = false;
            Double quality = null;
            for (var i = 0; i < filtrationConfig.maxQualityRetries(); i++) {
                var feedback = SynthesizerSchemas.parseScenarioFeedback(
                        critic.generate(SynthesizerPrompts.evaluateSyntheticScenario(scenario)));
                quality = feedback.score();
                if (feedback.score() >= filtrationConfig.syntheticInputQualityThreshold()) {
                    break;
                }
                scenario = SynthesizerSchemas.parseRewrittenScenario(model.generate(
                        SynthesizerPrompts.rewriteSyntheticScenario(context, scenario, feedback.feedback())));
                rewritten = true;
            }
            filtered.add(new QualifiedConversationalData(
                    new ConversationalData(
                            scenario,
                            rewritten ? null : item.expectedOutcome(),
                            item.userDescription(),
                            item.turns(),
                            item.usedSourceFiles()),
                    quality));
        }
        return List.copyOf(filtered);
    }

    private record QualifiedSyntheticData(SyntheticData data, Double score) {
    }

    private record QualifiedConversationalData(ConversationalData data, Double score) {
    }

    private static ContextConstructionConfig config(ContextConstructionConfig config) {
        return config == null ? ContextConstructionConfig.DEFAULT : config;
    }

    private static List<String> contextSourceFiles(List<?> sourceFiles, int contextIndex) {
        if (sourceFiles == null || contextIndex >= sourceFiles.size()) {
            return List.of();
        }
        var source = sourceFiles.get(contextIndex);
        if (source instanceof String text) {
            return text.isBlank() ? List.of() : List.of(text);
        }
        if (source instanceof List<?> values) {
            return values.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(value -> !value.isBlank())
                    .toList();
        }
        return List.of();
    }

    private static void addDocumentContexts(
            List<Path> documentPaths,
            ContextConstructionConfig config,
            List<List<String>> contexts,
            List<String> sourceFiles,
            List<Double> contextScores) throws IOException {
        for (var path : documentPaths) {
            for (var file : documentFiles(path)) {
                addDocumentContexts(config, contexts, sourceFiles, contextScores, file);
            }
        }
    }

    private static void addDocumentContexts(
            ContextConstructionConfig config,
            List<List<String>> contexts,
            List<String> sourceFiles,
            List<Double> contextScores,
            Path file) throws IOException {
        validateChunkOverlap(config.chunkSize(), config.chunkOverlap());
        var chunks = Utils.chunkText(Files.readString(file), config.chunkSize(), config.chunkOverlap());
        validateMinContexts(chunks.size(), config.minContextsPerDocument());
        var count = 0;
        for (var chunk : chunks) {
            if (count++ >= config.maxContextsPerDocument()) {
                break;
            }
            contexts.add(List.of(chunk));
            sourceFiles.add(file.getFileName().toString());
            contextScores.add(0.0);
        }
    }

    private static List<Path> documentFiles(Path path) throws IOException {
        if (Files.isRegularFile(path)) {
            return List.of(path);
        }
        try (var files = Files.walk(path)) {
            return files.filter(Files::isRegularFile).sorted().toList();
        }
    }

    private static void validateChunkOverlap(int chunkSize, int chunkOverlap) {
        if (chunkOverlap > chunkSize - 1) {
            throw new IllegalArgumentException(
                    "`chunk_overlap` must not exceed " + (chunkSize - 1) + " (chunk_size - 1).");
        }
    }

    private static void validateMinContexts(int numChunks, int minContexts) {
        if (numChunks >= minContexts) {
            return;
        }
        var message = new StringBuilder()
                .append("Impossible to generate ")
                .append(minContexts)
                .append(" contexts from a document with ")
                .append(numChunks)
                .append(" chunks.\nYou have the following options:");
        if (numChunks > 0) {
            message.append("\n1. Adjust the `min_contexts_per_document` to no more than ")
                    .append(numChunks)
                    .append(".");
        }
        throw new IllegalArgumentException(message.toString());
    }

    private <T> List<List<T>> generateContextBatches(int contextCount, IntFunction<List<T>> generator) {
        if (!options.asyncMode() || contextCount < 2 || options.maxConcurrent() == 1) {
            var batches = new ArrayList<List<T>>();
            for (var i = 0; i < contextCount; i++) {
                batches.add(generator.apply(i));
            }
            return List.copyOf(batches);
        }
        var semaphore = new Semaphore(options.maxConcurrent());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<java.util.concurrent.Future<List<T>>>();
            for (var i = 0; i < contextCount; i++) {
                var index = i;
                futures.add(executor.submit(() -> {
                    semaphore.acquire();
                    try {
                        return generator.apply(index);
                    } finally {
                        semaphore.release();
                    }
                }));
            }
            var batches = new ArrayList<List<T>>();
            for (var future : futures) {
                batches.add(future.get());
            }
            return List.copyOf(batches);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while generating synthetic goldens.", error);
        } catch (ExecutionException error) {
            throw taskFailure(error);
        }
    }

    private static RuntimeException taskFailure(ExecutionException error) {
        var cause = error.getCause();
        if (cause instanceof RuntimeException runtime) {
            return runtime;
        }
        if (cause instanceof Error fatal) {
            throw fatal;
        }
        return new IllegalStateException("Synthetic golden generation failed.", cause);
    }

    private Evolution evolution(int index) {
        var evolutions = evolutionConfig.evolutions();
        return evolutions.get(index % evolutions.size());
    }

    private StylingConfig extractStylingConfig(List<String> inputs) {
        return SynthesizerSchemas.parseStylingConfig(model.generate(
                SynthesizerPrompts.extractPromptStructureFromInputs(inputs.stream().limit(10).toList())));
    }

    private void validateScratchStylingConfig(StylingConfig config) {
        if (config == null
                || config.scenario() == null
                || config.task() == null
                || config.inputFormat() == null) {
            throw new IllegalStateException(
                    "`scenario`, `task`, and `input_format` in `styling_config` must not be None when generation goldens from scratch.");
        }
    }

    private void validateConversationalScratchStylingConfig() {
        if (conversationalStylingConfig == null
                || conversationalStylingConfig.scenarioContext() == null
                || conversationalStylingConfig.conversationalTask() == null
                || conversationalStylingConfig.participantRoles() == null) {
            throw new IllegalStateException(
                    "`scenario_context`, `conversational_task`, and `participant_roles` in `conversational_styling_config` must not be None when generating conversational goldens from scratch.");
        }
    }

    private static boolean shouldStyle(StylingConfig config) {
        return config != null
                && (hasText(config.inputFormat()) || hasText(config.scenario()) || hasText(config.task()));
    }

    private static boolean shouldStyle(ConversationalStylingConfig config) {
        return config != null
                && (hasText(config.participantRoles())
                || hasText(config.scenarioContext())
                || hasText(config.conversationalTask()));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static LinkedHashMap<String, Object> metadata(
            List<String> evolutions,
            SyntheticData data,
            String sourceFile,
            Double syntheticInputQuality,
            Double contextQuality,
            List<String> contextSourceFiles) {
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("evolutions", List.copyOf(evolutions));
        if (syntheticInputQuality != null) {
            metadata.put("synthetic_input_quality", syntheticInputQuality);
        }
        metadata.put("context_source_files", List.copyOf(contextSourceFiles));
        metadata.put("context_quality", contextQuality);
        metadata.put("used_source_files", data.usedSourceFiles() == null ? List.of() : data.usedSourceFiles());
        return metadata;
    }

    private static LinkedHashMap<String, Object> metadata(
            List<String> evolutions,
            ConversationalData data,
            String sourceFile,
            Double syntheticScenarioQuality,
            Double contextQuality,
            List<String> contextSourceFiles) {
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("evolutions", List.copyOf(evolutions));
        if (syntheticScenarioQuality != null) {
            metadata.put("synthetic_scenario_quality", syntheticScenarioQuality);
        }
        metadata.put("source_files", sourceFile);
        metadata.put("context_source_files", List.copyOf(contextSourceFiles));
        metadata.put("context_quality", contextQuality);
        metadata.put("used_source_files", data.usedSourceFiles() == null ? List.of() : data.usedSourceFiles());
        return metadata;
    }
}
