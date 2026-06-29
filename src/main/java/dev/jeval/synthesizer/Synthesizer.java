package dev.jeval.synthesizer;

import dev.jeval.EvaluationModel;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.function.IntFunction;

public final class Synthesizer {
    private final EvaluationModel model;
    private final StylingConfig stylingConfig;
    private final ConversationalStylingConfig conversationalStylingConfig;
    private final EvolutionConfig evolutionConfig;
    private final SynthesizerOptions options;

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
        this(model, stylingConfig, conversationalStylingConfig, evolutionConfig, SynthesizerOptions.DEFAULT);
    }

    public Synthesizer(
            EvaluationModel model,
            StylingConfig stylingConfig,
            ConversationalStylingConfig conversationalStylingConfig,
            EvolutionConfig evolutionConfig,
            SynthesizerOptions options) {
        this.model = Objects.requireNonNull(model, "model");
        this.stylingConfig = stylingConfig;
        this.conversationalStylingConfig = conversationalStylingConfig;
        this.evolutionConfig = evolutionConfig == null ? new EvolutionConfig() : evolutionConfig;
        this.options = options == null ? SynthesizerOptions.DEFAULT : options;
    }

    public SynthesizerOptions options() {
        return options;
    }

    public List<Golden> generateGoldensFromContexts(List<List<String>> contexts) {
        return generateGoldensFromContexts(contexts, true, 2, null);
    }

    public List<Golden> generateGoldensFromDocs(List<Path> documentPaths) throws IOException {
        return generateGoldensFromDocs(documentPaths, true, 2, ContextConstructionConfig.DEFAULT);
    }

    public List<Golden> generateGoldensFromDocs(
            List<Path> documentPaths,
            boolean includeExpectedOutput,
            int maxGoldensPerContext,
            ContextConstructionConfig contextConstructionConfig) throws IOException {
        var contexts = new ArrayList<List<String>>();
        var sourceFiles = new ArrayList<String>();
        addDocumentContexts(documentPaths, config(contextConstructionConfig), contexts, sourceFiles);
        return generateGoldensFromContexts(contexts, includeExpectedOutput, maxGoldensPerContext, sourceFiles);
    }

    public List<Golden> generateGoldensFromContexts(
            List<List<String>> contexts,
            boolean includeExpectedOutput,
            int maxGoldensPerContext,
            List<String> sourceFiles) {
        var goldens = new ArrayList<Golden>();
        for (var batch : generateContextBatches(contexts.size(),
                index -> generateGoldensForContext(index, contexts, includeExpectedOutput, maxGoldensPerContext, sourceFiles))) {
            goldens.addAll(batch);
        }
        return List.copyOf(goldens);
    }

    private List<Golden> generateGoldensForContext(
            int contextIndex,
            List<List<String>> contexts,
            boolean includeExpectedOutput,
            int maxGoldensPerContext,
            List<String> sourceFiles) {
        var goldens = new ArrayList<Golden>();
        var context = List.copyOf(contexts.get(contextIndex));
        var sourceFile = sourceFiles != null && contextIndex < sourceFiles.size() ? sourceFiles.get(contextIndex) : null;
        var data = SynthesizerSchemas.parseSyntheticData(
                model.generate(SynthesizerPrompts.generateSyntheticInputs(
                        context, maxGoldensPerContext, includeExpectedOutput)));
        for (var item : data.stream().limit(maxGoldensPerContext).toList()) {
            goldens.add(golden(item, context, sourceFile, includeExpectedOutput,
                    contextIndex * maxGoldensPerContext + goldens.size()));
        }
        return List.copyOf(goldens);
    }

    public List<Golden> generateGoldensFromScratch(int numGoldens) {
        if (stylingConfig == null) {
            throw new IllegalStateException("StylingConfig is required for scratch generation");
        }
        var data = SynthesizerSchemas.parseSyntheticData(model.generate(
                SynthesizerPrompts.generateSyntheticInputsFromScratch(
                        stylingConfig.scenario(), stylingConfig.task(), stylingConfig.inputFormat(), numGoldens)));
        var goldens = new ArrayList<Golden>();
        for (var item : data) {
            goldens.add(golden(item, null, null, false, goldens.size()));
        }
        return List.copyOf(goldens);
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
            var data = SynthesizerSchemas.parseSyntheticData(model.generate(
                    SynthesizerPrompts.generateSyntheticInputsFromGoldens(
                            inputs, inputs.size() * maxGoldensPerGolden, includeExpectedOutput)));
            for (var item : data) {
                generated.add(golden(item, null, null, includeExpectedOutput, generated.size()));
            }
        }
        return List.copyOf(generated);
    }

    public List<ConversationalGolden> generateConversationalGoldensFromContexts(
            List<List<String>> contexts,
            boolean includeExpectedOutcome,
            int maxGoldensPerContext,
            List<String> sourceFiles) {
        var goldens = new ArrayList<ConversationalGolden>();
        for (var batch : generateContextBatches(contexts.size(),
                index -> generateConversationalGoldensForContext(index, contexts, includeExpectedOutcome,
                        maxGoldensPerContext, sourceFiles))) {
            goldens.addAll(batch);
        }
        return List.copyOf(goldens);
    }

    public List<ConversationalGolden> generateConversationalGoldensFromDocs(List<Path> documentPaths)
            throws IOException {
        return generateConversationalGoldensFromDocs(documentPaths, true, 2, ContextConstructionConfig.DEFAULT);
    }

    public List<ConversationalGolden> generateConversationalGoldensFromDocs(
            List<Path> documentPaths,
            boolean includeExpectedOutcome,
            int maxGoldensPerContext,
            ContextConstructionConfig contextConstructionConfig) throws IOException {
        var contexts = new ArrayList<List<String>>();
        var sourceFiles = new ArrayList<String>();
        addDocumentContexts(documentPaths, config(contextConstructionConfig), contexts, sourceFiles);
        return generateConversationalGoldensFromContexts(contexts, includeExpectedOutcome, maxGoldensPerContext, sourceFiles);
    }

    private List<ConversationalGolden> generateConversationalGoldensForContext(
            int contextIndex,
            List<List<String>> contexts,
            boolean includeExpectedOutcome,
            int maxGoldensPerContext,
            List<String> sourceFiles) {
        var goldens = new ArrayList<ConversationalGolden>();
        var context = List.copyOf(contexts.get(contextIndex));
        var sourceFile = sourceFiles != null && contextIndex < sourceFiles.size() ? sourceFiles.get(contextIndex) : null;
        var data = SynthesizerSchemas.parseConversationalData(model.generate(
                SynthesizerPrompts.generateSyntheticConversationalScenarios(
                        context, maxGoldensPerContext, conversationalStylingConfig, includeExpectedOutcome)));
        for (var item : data.stream().limit(maxGoldensPerContext).toList()) {
            goldens.add(conversationalGolden(item, context, sourceFile, includeExpectedOutcome));
        }
        return List.copyOf(goldens);
    }

    public List<ConversationalGolden> generateConversationalGoldensFromScratch(int numGoldens) {
        if (conversationalStylingConfig == null
                || conversationalStylingConfig.scenarioContext() == null
                || conversationalStylingConfig.conversationalTask() == null
                || conversationalStylingConfig.participantRoles() == null) {
            throw new IllegalStateException(
                    "ConversationalStylingConfig with scenarioContext, conversationalTask, and participantRoles is required for conversational scratch generation");
        }
        return SynthesizerSchemas.parseConversationalData(model.generate(
                        SynthesizerPrompts.generateSyntheticConversationalScenariosFromScratch(
                                conversationalStylingConfig, numGoldens)))
                .stream()
                .map(data -> conversationalGolden(data, null, null, false))
                .toList();
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
            for (var item : data) {
                generated.add(conversationalGolden(item, null, null, includeExpectedOutcome));
            }
        }
        return List.copyOf(generated);
    }

    private ConversationalGolden conversationalGolden(
            ConversationalData data,
            List<String> context,
            String sourceFile,
            boolean includeExpectedOutcome) {
        var expectedOutcome = data.expectedOutcome();
        if (includeExpectedOutcome && expectedOutcome == null) {
            expectedOutcome = model.generate(SynthesizerPrompts.generateConversationalExpectedOutcome(
                    data.scenario(),
                    context,
                    conversationalStylingConfig == null ? null : conversationalStylingConfig.expectedOutcomeFormat()));
        }
        return ConversationalGolden.builder(data.scenario())
                .turns(data.turns())
                .expectedOutcome(expectedOutcome)
                .userDescription(data.userDescription())
                .context(context)
                .additionalMetadata(metadata(List.of(), data, sourceFile))
                .build();
    }

    private Golden golden(
            SyntheticData data,
            List<String> context,
            String sourceFile,
            boolean includeExpectedOutput,
            int goldenIndex) {
        var evolutions = new ArrayList<String>();
        var input = data.input();
        for (var i = 0; i < evolutionConfig.numEvolutions(); i++) {
            var evolution = evolution(goldenIndex + i);
            input = SynthesizerSchemas.parseRewrittenInput(model.generate(SynthesizerPrompts.evolveInput(input, evolution)));
            evolutions.add(evolution.value());
        }
        var expectedOutput = includeExpectedOutput ? expectedOutput(data, context, input) : null;
        return Golden.builder(input)
                .expectedOutput(expectedOutput)
                .context(context)
                .sourceFile(sourceFile)
                .additionalMetadata(metadata(evolutions, data, sourceFile))
                .build();
    }

    private String expectedOutput(SyntheticData data, List<String> context, String input) {
        if (data.expectedOutput() != null || context == null) {
            return data.expectedOutput();
        }
        return model.generate(SynthesizerPrompts.generateExpectedOutput(
                context, input, stylingConfig == null ? null : stylingConfig.expectedOutputFormat()));
    }

    private static ContextConstructionConfig config(ContextConstructionConfig config) {
        return config == null ? ContextConstructionConfig.DEFAULT : config;
    }

    private static void addDocumentContexts(
            List<Path> documentPaths,
            ContextConstructionConfig config,
            List<List<String>> contexts,
            List<String> sourceFiles) throws IOException {
        for (var path : documentPaths) {
            for (var file : documentFiles(path)) {
                addDocumentContexts(config, contexts, sourceFiles, file);
            }
        }
    }

    private static void addDocumentContexts(
            ContextConstructionConfig config,
            List<List<String>> contexts,
            List<String> sourceFiles,
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

    private static LinkedHashMap<String, Object> metadata(
            List<String> evolutions,
            SyntheticData data,
            String sourceFile) {
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("evolutions", List.copyOf(evolutions));
        if (sourceFile != null) {
            metadata.put("context_source_files", List.of(sourceFile));
        }
        if (data.usedSourceFiles() != null) {
            metadata.put("used_source_files", data.usedSourceFiles());
        }
        return metadata;
    }

    private static LinkedHashMap<String, Object> metadata(
            List<String> evolutions,
            ConversationalData data,
            String sourceFile) {
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("evolutions", List.copyOf(evolutions));
        if (sourceFile != null) {
            metadata.put("source_files", sourceFile);
            metadata.put("context_source_files", List.of(sourceFile));
        }
        if (data.usedSourceFiles() != null) {
            metadata.put("used_source_files", data.usedSourceFiles());
        }
        return metadata;
    }
}
