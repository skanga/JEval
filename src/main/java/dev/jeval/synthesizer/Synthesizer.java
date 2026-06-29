package dev.jeval.synthesizer;

import dev.jeval.EvaluationModel;
import dev.jeval.ConversationalGolden;
import dev.jeval.Golden;
import dev.jeval.synthesizer.SynthesizerSchemas.SyntheticData;
import dev.jeval.synthesizer.SynthesizerSchemas.ConversationalData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

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

    public List<Golden> generateGoldensFromContexts(
            List<List<String>> contexts,
            boolean includeExpectedOutput,
            int maxGoldensPerContext,
            List<String> sourceFiles) {
        var goldens = new ArrayList<Golden>();
        for (var i = 0; i < contexts.size(); i++) {
            var context = List.copyOf(contexts.get(i));
            var sourceFile = sourceFiles != null && i < sourceFiles.size() ? sourceFiles.get(i) : null;
            var data = SynthesizerSchemas.parseSyntheticData(
                    model.generate(SynthesizerPrompts.generateSyntheticInputs(
                            context, maxGoldensPerContext, includeExpectedOutput)));
            for (var item : data.stream().limit(maxGoldensPerContext).toList()) {
                goldens.add(golden(item, context, sourceFile, includeExpectedOutput, goldens.size()));
            }
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
        for (var i = 0; i < contexts.size(); i++) {
            var context = List.copyOf(contexts.get(i));
            var sourceFile = sourceFiles != null && i < sourceFiles.size() ? sourceFiles.get(i) : null;
            var data = SynthesizerSchemas.parseConversationalData(model.generate(
                    SynthesizerPrompts.generateSyntheticConversationalScenarios(
                            context, maxGoldensPerContext, conversationalStylingConfig, includeExpectedOutcome)));
            for (var item : data.stream().limit(maxGoldensPerContext).toList()) {
                goldens.add(conversationalGolden(item, context, sourceFile, includeExpectedOutcome));
            }
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
