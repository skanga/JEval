package dev.jeval.metrics;

import dev.jeval.ConversationalMetric;
import dev.jeval.ConversationalTestCase;
import dev.jeval.EvaluationModel;
import dev.jeval.MetricResult;
import dev.jeval.MultiTurnParam;
import dev.jeval.Turn;
import dev.jeval.metrics.KnowledgeRetentionSchemas.Knowledge;
import dev.jeval.metrics.KnowledgeRetentionSchemas.KnowledgeRetentionScoreReason;
import dev.jeval.metrics.KnowledgeRetentionSchemas.KnowledgeRetentionVerdict;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class KnowledgeRetentionMetric implements ConversationalMetric {
    private final EvaluationModel model;
    private final boolean includeReason;
    private final boolean strictMode;
    private final double threshold;
    private List<Knowledge> knowledges;
    private List<KnowledgeRetentionVerdict> verdicts;
    private double score;
    private String reason;
    private boolean success;

    public KnowledgeRetentionMetric() {
        this(0.5, true, false);
    }

    public KnowledgeRetentionMetric(EvaluationModel model) {
        this(model, 0.5, true, false);
    }

    public KnowledgeRetentionMetric(double threshold, boolean includeReason, boolean strictMode) {
        this(null, threshold, includeReason, strictMode);
    }

    public KnowledgeRetentionMetric(EvaluationModel model, double threshold, boolean includeReason, boolean strictMode) {
        this.model = model;
        this.includeReason = includeReason;
        this.strictMode = strictMode;
        this.threshold = strictMode ? 1.0 : threshold;
    }

    @Override
    public MetricResult measure(ConversationalTestCase testCase) {
        MetricUtils.checkConversationalTestCaseParams(
                testCase, List.of(MultiTurnParam.CONTENT, MultiTurnParam.ROLE), name());
        knowledges = generateKnowledges(testCase.turns());
        verdicts = generateVerdicts(testCase.turns(), testCase.multimodal());
        score = calculateScore();
        reason = includeReason ? generateReason(testCase.multimodal()).reason() : null;
        success = score >= threshold;
        return new MetricResult(name(), score, threshold, success, reason);
    }

    public String name() {
        return "Knowledge Retention";
    }

    public List<Knowledge> knowledges() {
        return knowledges;
    }

    public List<KnowledgeRetentionVerdict> verdicts() {
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

    protected List<Knowledge> generateKnowledges(List<Turn> turns) {
        requireModel();
        var values = new ArrayList<Knowledge>();
        for (var i = 0; i < turns.size(); i++) {
            if ("assistant".equals(turns.get(i).role())) {
                values.add(null);
            } else {
                values.add(KnowledgeRetentionSchemas.parseKnowledge(model.generate(
                        KnowledgeRetentionPrompts.extractData(turns.subList(0, i), turns.get(i).content()))));
            }
        }
        return values;
    }

    protected List<KnowledgeRetentionVerdict> generateVerdicts(List<Turn> turns, boolean multimodal) {
        requireModel();
        var values = new ArrayList<KnowledgeRetentionVerdict>();
        for (var i = 0; i < turns.size(); i++) {
            if (!"assistant".equals(turns.get(i).role())) {
                continue;
            }
            var accumulatedKnowledge = knowledges.subList(0, i).stream()
                    .filter(knowledge -> knowledge != null && knowledge.data() != null && !knowledge.data().isEmpty())
                    .map(Knowledge::data)
                    .toList();
            if (accumulatedKnowledge.isEmpty()) {
                continue;
            }
            values.add(KnowledgeRetentionSchemas.parseVerdict(model.generate(
                    KnowledgeRetentionPrompts.generateVerdict(
                            turns.get(i).content(), accumulatedKnowledge, multimodal))));
        }
        return values;
    }

    protected KnowledgeRetentionScoreReason generateReason(boolean multimodal) {
        requireModel();
        var attritions = verdicts.stream()
                .filter(verdict -> verdict.verdict() != null && "yes".equalsIgnoreCase(verdict.verdict().strip()))
                .map(KnowledgeRetentionVerdict::reason)
                .toList();
        return KnowledgeRetentionSchemas.parseReason(
                model.generate(KnowledgeRetentionPrompts.generateReason(attritions, score, multimodal)));
    }

    private double calculateScore() {
        if (verdicts.isEmpty()) {
            return 1.0;
        }
        var retainedCount = verdicts.stream()
                .filter(verdict -> verdict.verdict() != null && "no".equalsIgnoreCase(verdict.verdict().strip()))
                .count();
        var rawScore = retainedCount / (double) verdicts.size();
        return strictMode && rawScore < threshold ? 0.0 : rawScore;
    }

    private void requireModel() {
        if (model == null) {
            throw new UnsupportedOperationException("Knowledge Retention generation requires a model provider");
        }
    }
}
