package dev.jeval.metrics;

import dev.jeval.EvaluationModel;
import dev.jeval.LlmTestCase;
import dev.jeval.Metric;
import dev.jeval.MetricResult;
import dev.jeval.MissingTestCaseParamsException;
import dev.jeval.RetrievedContextData;
import dev.jeval.SingleTurnParam;
import dev.jeval.metrics.ContextualPrecisionSchemas.ContextualPrecisionVerdict;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ContextualPrecisionMetric implements Metric {
    private final EvaluationModel model;
    private final boolean includeReason;
    private final boolean strictMode;
    private final double threshold;
    private double score;
    private String reason;
    private boolean success;
    private List<ContextualPrecisionVerdict> verdicts = List.of();

    public ContextualPrecisionMetric() {
        this(0.5, true, false);
    }

    public ContextualPrecisionMetric(EvaluationModel model) {
        this(model, 0.5, true, false);
    }

    public ContextualPrecisionMetric(double threshold, boolean includeReason, boolean strictMode) {
        this(null, threshold, includeReason, strictMode);
    }

    public ContextualPrecisionMetric(EvaluationModel model, double threshold, boolean includeReason, boolean strictMode) {
        if (!Double.isFinite(threshold)) {
            throw new IllegalArgumentException("Contextual Precision threshold must be finite");
        }
        this.model = model;
        this.includeReason = includeReason;
        this.strictMode = strictMode;
        this.threshold = strictMode ? 1.0 : threshold;
    }

    @Override
    public MetricResult measure(LlmTestCase testCase) {
        MetricUtils.checkLlmTestCaseParams(
                testCase,
                List.of(SingleTurnParam.INPUT, SingleTurnParam.EXPECTED_OUTPUT),
                name());
        if (testCase.retrievalContext() == null) {
            throw new MissingTestCaseParamsException("'retrieval_context' cannot be None for the '" + name() + "' metric");
        }

        verdicts = List.copyOf(generateVerdicts(
                testCase.input(), testCase.expectedOutput(), groupRetrievalContexts(testCase.retrievalContext()),
                testCase.multimodal()));
        score = calculateScore();
        reason = includeReason ? generateReason(testCase.input(), testCase.multimodal()) : null;
        success = score >= threshold;
        return new MetricResult(name(), score, threshold, success, reason);
    }

    public String name() {
        return "Contextual Precision";
    }

    public double score() {
        return score;
    }

    public String reason() {
        return reason;
    }

    public boolean success() {
        return success;
    }

    public List<ContextualPrecisionVerdict> verdicts() {
        return verdicts;
    }

    protected List<ContextualPrecisionVerdict> generateVerdicts(
            String input, String expectedOutput, List<String> retrievalContext, boolean multimodal) {
        requireModel();
        return ContextualPrecisionSchemas.parseVerdicts(
                model.generate(ContextualPrecisionPrompts.generateVerdicts(
                        input, expectedOutput, retrievalContext, multimodal))).verdicts();
    }

    protected String generateReason(String input, boolean multimodal) {
        requireModel();
        return ContextualPrecisionSchemas.parseScoreReason(
                model.generate(ContextualPrecisionPrompts.generateReason(input, score, verdicts, multimodal)))
                .reason();
    }

    private double calculateScore() {
        if (verdicts.isEmpty()) {
            return 0.0;
        }

        var weightedPrecisionAtK = 0.0;
        var relevantNodes = 0;
        for (var k = 0; k < verdicts.size(); k++) {
            if ("yes".equals(verdicts.get(k).verdict().strip().toLowerCase())) {
                relevantNodes++;
                weightedPrecisionAtK += (double) relevantNodes / (k + 1);
            }
        }
        if (relevantNodes == 0) {
            return 0.0;
        }
        var calculatedScore = weightedPrecisionAtK / relevantNodes;
        return strictMode && calculatedScore < threshold ? 0.0 : calculatedScore;
    }

    private static List<String> groupRetrievalContexts(List<?> retrievalContext) {
        var grouped = new HashMap<String, List<String>>();
        var ordered = new ArrayList<Object>();
        for (var context : retrievalContext) {
            if (context instanceof RetrievedContextData data) {
                if (!grouped.containsKey(data.source())) {
                    grouped.put(data.source(), new ArrayList<>());
                    ordered.add(new SourceGroup(data.source()));
                }
                grouped.get(data.source()).add(data.context());
            } else {
                ordered.add(RetrievedContextData.textValue(context));
            }
        }
        var result = new ArrayList<String>();
        for (var item : ordered) {
            if (item instanceof SourceGroup group) {
                result.add("Source: " + group.source() + "\n" + String.join("\n---\n", grouped.get(group.source())));
            } else {
                result.add((String) item);
            }
        }
        return List.copyOf(result);
    }

    private void requireModel() {
        if (model == null) {
            throw new UnsupportedOperationException("Contextual precision generation requires a model provider");
        }
    }

    private record SourceGroup(String source) {}
}
