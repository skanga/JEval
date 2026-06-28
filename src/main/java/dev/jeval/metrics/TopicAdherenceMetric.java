package dev.jeval.metrics;

import dev.jeval.ConversationalMetric;
import dev.jeval.ConversationalTestCase;
import dev.jeval.EvaluationModel;
import dev.jeval.MetricResult;
import dev.jeval.MultiTurnParam;
import dev.jeval.Turn;
import dev.jeval.metrics.TopicAdherenceSchemas.QAPair;
import dev.jeval.metrics.TopicAdherenceSchemas.QAPairs;
import dev.jeval.metrics.TopicAdherenceSchemas.RelevancyVerdict;
import dev.jeval.metrics.TopicAdherenceSchemas.TopicAdherenceReason;
import java.util.ArrayList;
import java.util.List;

public class TopicAdherenceMetric implements ConversationalMetric {
    private static final String NO_QA_REASON =
            "There were no question-answer pairs to evaluate. Please enable verbose logs to look at the evaluation steps taken";

    private final List<String> relevantTopics;
    private final EvaluationModel model;
    private final boolean includeReason;
    private final boolean strictMode;
    private final double threshold;
    private List<QAPairs> qaPairs;
    private List<RelevancyVerdict> verdicts;
    private List<String> truePositives;
    private List<String> trueNegatives;
    private List<String> falsePositives;
    private List<String> falseNegatives;
    private double score;
    private String reason;
    private boolean success;

    public TopicAdherenceMetric(List<String> relevantTopics) {
        this(relevantTopics, 0.5, true, false);
    }

    public TopicAdherenceMetric(List<String> relevantTopics, EvaluationModel model) {
        this(relevantTopics, model, 0.5, true, false);
    }

    public TopicAdherenceMetric(List<String> relevantTopics, double threshold, boolean includeReason, boolean strictMode) {
        this(relevantTopics, null, threshold, includeReason, strictMode);
    }

    public TopicAdherenceMetric(
            List<String> relevantTopics,
            EvaluationModel model,
            double threshold,
            boolean includeReason,
            boolean strictMode) {
        this.relevantTopics = List.copyOf(relevantTopics);
        this.model = model;
        this.includeReason = includeReason;
        this.strictMode = strictMode;
        this.threshold = strictMode ? 1.0 : threshold;
    }

    @Override
    public MetricResult measure(ConversationalTestCase testCase) {
        MetricUtils.checkConversationalTestCaseParams(
                testCase, List.of(MultiTurnParam.CONTENT, MultiTurnParam.ROLE), name());
        var multimodal = testCase.multimodal();
        qaPairs = getQaPairs(MetricUtils.getUnitInteractions(testCase.turns()), multimodal);
        verdicts = new ArrayList<>();
        truePositives = new ArrayList<>();
        trueNegatives = new ArrayList<>();
        falsePositives = new ArrayList<>();
        falseNegatives = new ArrayList<>();
        for (var pairs : qaPairs) {
            for (var pair : pairs.qaPairs()) {
                var verdict = getQaVerdict(pair, multimodal);
                verdicts.add(verdict);
                addVerdict(verdict);
            }
        }
        score = calculateScore();
        success = score >= threshold;
        reason = includeReason ? reasonOrDefault(multimodal) : null;
        return new MetricResult(name(), score, threshold, success, reason);
    }

    public String name() {
        return "Topic Adherence";
    }

    public List<QAPairs> qaPairs() {
        return qaPairs;
    }

    public List<RelevancyVerdict> verdicts() {
        return verdicts;
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

    protected List<QAPairs> getQaPairs(List<List<Turn>> unitInteractions, boolean multimodal) {
        requireModel();
        var results = new ArrayList<QAPairs>();
        for (var unitInteraction : unitInteractions) {
            results.add(TopicAdherenceSchemas.parseQaPairs(
                    model.generate(TopicAdherencePrompts.getQaPairs(unitInteraction, multimodal))));
        }
        return results;
    }

    protected RelevancyVerdict getQaVerdict(QAPair qaPair, boolean multimodal) {
        requireModel();
        return TopicAdherenceSchemas.parseVerdict(model.generate(TopicAdherencePrompts.getQaPairVerdict(
                relevantTopics, qaPair.question(), qaPair.response(), multimodal)));
    }

    protected TopicAdherenceReason generateReason(boolean multimodal) {
        requireModel();
        return TopicAdherenceSchemas.parseReason(model.generate(TopicAdherencePrompts.generateReason(
                success, score, threshold, truePositives, trueNegatives, falsePositives, falseNegatives, multimodal)));
    }

    private void addVerdict(RelevancyVerdict verdict) {
        if ("TP".equals(verdict.verdict())) {
            truePositives.add(verdict.reason());
        } else if ("TN".equals(verdict.verdict())) {
            trueNegatives.add(verdict.reason());
        } else if ("FP".equals(verdict.verdict())) {
            falsePositives.add(verdict.reason());
        } else if ("FN".equals(verdict.verdict())) {
            falseNegatives.add(verdict.reason());
        }
    }

    private double calculateScore() {
        var total = truePositives.size() + trueNegatives.size() + falsePositives.size() + falseNegatives.size();
        var rawScore = total == 0 ? 0.0 : (truePositives.size() + trueNegatives.size()) / (double) total;
        return strictMode && rawScore < threshold ? 0.0 : rawScore;
    }

    private String reasonOrDefault(boolean multimodal) {
        var total = truePositives.size() + trueNegatives.size() + falsePositives.size() + falseNegatives.size();
        return total == 0 ? NO_QA_REASON : generateReason(multimodal).reason();
    }

    private void requireModel() {
        if (model == null) {
            throw new UnsupportedOperationException("Topic Adherence generation requires a model provider");
        }
    }
}
