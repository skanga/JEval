package dev.jeval.optimizer;

import dev.jeval.ConversationalGolden;
import dev.jeval.ConversationalMetric;
import dev.jeval.Golden;
import dev.jeval.LlmTestCase;
import dev.jeval.Metric;
import dev.jeval.MetricResult;
import dev.jeval.Turn;
import dev.jeval.prompt.Prompt;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class OptimizerScorer {
    public static final String DEFAULT_MODULE_ID = "__module__";

    private final ModelCallback modelCallback;
    private final List<?> metrics;

    public OptimizerScorer(ModelCallback modelCallback, List<?> metrics) {
        this.modelCallback = (ModelCallback) OptimizerUtils.validateCallback("Scorer", modelCallback);
        this.metrics = OptimizerUtils.validateMetrics("Scorer", metrics);
    }

    public String generate(Map<String, Prompt> promptsByModule, Object golden) {
        return OptimizerUtils.invokeModelCallback(modelCallback, selectPrompt(promptsByModule), golden);
    }

    public List<Double> scorePareto(PromptConfiguration promptConfiguration, List<?> paretoGoldens) {
        return paretoGoldens.stream()
                .map(golden -> scoreOne(promptConfiguration, golden))
                .toList();
    }

    public double scoreMinibatch(PromptConfiguration promptConfiguration, List<?> minibatch) {
        if (minibatch.isEmpty()) {
            return 0.0;
        }
        return minibatch.stream()
                .mapToDouble(golden -> scoreOne(promptConfiguration, golden))
                .average()
                .orElse(0.0);
    }

    private double scoreOne(PromptConfiguration promptConfiguration, Object golden) {
        var actual = generate(promptConfiguration.prompts(), golden);
        var metricResults = new ArrayList<MetricResult>();
        for (var metric : metrics) {
            if (golden instanceof Golden singleTurnGolden && metric instanceof Metric singleTurnMetric) {
                metricResults.add(singleTurnMetric.measure(testCase(singleTurnGolden, actual)));
            } else if (golden instanceof ConversationalGolden conversationalGolden
                    && metric instanceof ConversationalMetric conversationalMetric) {
                metricResults.add(conversationalMetric.measure(testCase(conversationalGolden, actual)));
            }
        }
        return metricResults.stream().mapToDouble(MetricResult::score).average().orElse(0.0);
    }

    private LlmTestCase testCase(Golden golden, String actual) {
        return LlmTestCase.builder(golden.input())
                .actualOutput(actual)
                .expectedOutput(golden.expectedOutput())
                .context(golden.context())
                .retrievalContext(golden.retrievalContext())
                .additionalMetadata(golden.additionalMetadata())
                .comments(golden.comments())
                .toolsCalled(golden.toolsCalled())
                .expectedTools(golden.expectedTools())
                .name(golden.name())
                .customColumnKeyValues(golden.customColumnKeyValues())
                .datasetRank(golden.datasetRank())
                .datasetAlias(golden.datasetAlias())
                .datasetId(golden.datasetId())
                .multimodal(golden.multimodal())
                .build();
    }

    private dev.jeval.ConversationalTestCase testCase(ConversationalGolden golden, String actual) {
        var turns = new ArrayList<Turn>(golden.turns() == null ? List.of() : golden.turns());
        if (!turns.isEmpty() && "assistant".equals(turns.getLast().role())) {
            turns.set(turns.size() - 1, new Turn("assistant", actual));
        } else {
            turns.add(new Turn("assistant", actual));
        }
        return dev.jeval.ConversationalTestCase.builder(turns)
                .scenario(golden.scenario())
                .expectedOutcome(golden.expectedOutcome())
                .userDescription(golden.userDescription())
                .context(golden.context())
                .additionalMetadata(golden.additionalMetadata())
                .comments(golden.comments())
                .name(golden.name())
                .datasetRank(golden.datasetRank())
                .datasetAlias(golden.datasetAlias())
                .datasetId(golden.datasetId())
                .multimodal(golden.multimodal())
                .build();
    }

    private Prompt selectPrompt(Map<String, Prompt> promptsByModule) {
        if (promptsByModule.isEmpty()) {
            throw new IllegalArgumentException("promptsByModule must contain at least one Prompt");
        }
        var defaultPrompt = promptsByModule.get(DEFAULT_MODULE_ID);
        if (defaultPrompt != null) {
            return defaultPrompt;
        }
        return promptsByModule.values().iterator().next();
    }
}
