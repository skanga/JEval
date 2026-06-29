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

    public SimbaTraceRecord executeTrace(PromptConfiguration promptConfiguration, Object golden) {
        var trace = measureOne(promptConfiguration, golden);
        var feedback = trace.metricResults().stream()
                .map(result -> "- " + result.name() + " (" + result.score() + "): "
                        + (result.reason() == null ? "" : result.reason()))
                .toList();
        return new SimbaTraceRecord(trace.actual(), trace.score(), String.join("\n", feedback));
    }

    public ScorerDiagnosisResult getMinibatchFeedback(
            PromptConfiguration promptConfiguration,
            String module,
            List<?> minibatch) {
        if (minibatch.isEmpty()) {
            return new ScorerDiagnosisResult("", "", "", List.of());
        }
        var failures = new ArrayList<String>();
        var successes = new ArrayList<String>();
        var results = new ArrayList<String>();
        for (var golden : minibatch) {
            var trace = measureOne(promptConfiguration, golden);
            var block = evaluationResultsBlock(golden, trace.actual(), trace.metricResults());
            results.add(block);
            if (trace.success()) {
                successes.add(block);
            } else {
                failures.add(block);
            }
        }
        var analysis = failures.size() + " failure" + plural(failures.size())
                + ", " + successes.size() + " success" + plural(successes.size())
                + " for module `" + module + "`.";
        return new ScorerDiagnosisResult(
                String.join("\n\n---\n\n", failures),
                String.join("\n\n---\n\n", successes),
                analysis,
                results);
    }

    private double scoreOne(PromptConfiguration promptConfiguration, Object golden) {
        return measureOne(promptConfiguration, golden).score();
    }

    private ScoreTrace measureOne(PromptConfiguration promptConfiguration, Object golden) {
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
        var score = metricResults.stream().mapToDouble(MetricResult::score).average().orElse(0.0);
        var success = !metricResults.isEmpty() && metricResults.stream().allMatch(MetricResult::success);
        return new ScoreTrace(actual, metricResults, score, success);
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

    private static String evaluationResultsBlock(Object golden, String actual, List<MetricResult> metricResults) {
        var input = input(golden);
        var expected = expected(golden);
        var reasons = metricResults.stream()
                .map(result -> "- " + result.name() + " (Score: " + result.score() + "): "
                        + (result.reason() == null ? "" : result.reason()))
                .toList();
        return "[Input]: " + input + "\n"
                + "[Expected]: " + expected + "\n"
                + "[Actual Model Output]: " + actual + "\n"
                + "[Evaluation Reasons]:\n" + String.join("\n", reasons);
    }

    private static String input(Object golden) {
        if (golden instanceof Golden singleTurnGolden) {
            return singleTurnGolden.input();
        }
        if (golden instanceof ConversationalGolden conversationalGolden) {
            var turns = conversationalGolden.turns() == null ? List.<Turn>of() : conversationalGolden.turns();
            return String.join("\n", turns.stream()
                    .filter(turn -> "user".equals(turn.role()))
                    .map(Turn::content)
                    .toList());
        }
        return String.valueOf(golden);
    }

    private static String expected(Object golden) {
        if (golden instanceof Golden singleTurnGolden) {
            return singleTurnGolden.expectedOutput() == null ? "None provided" : singleTurnGolden.expectedOutput();
        }
        if (golden instanceof ConversationalGolden conversationalGolden) {
            return conversationalGolden.expectedOutcome() == null ? "None provided"
                    : conversationalGolden.expectedOutcome();
        }
        return "None provided";
    }

    private static String plural(int count) {
        return count == 1 ? "" : "s";
    }

    private record ScoreTrace(String actual, List<MetricResult> metricResults, double score, boolean success) {
    }
}
