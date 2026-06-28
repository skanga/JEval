package dev.jeval.metrics;

import dev.jeval.EvaluationModel;
import dev.jeval.LlmTestCase;
import dev.jeval.Metric;
import dev.jeval.MetricResult;
import dev.jeval.SingleTurnParam;
import dev.jeval.metrics.SummarizationSchemas.ScoreType;
import dev.jeval.metrics.SummarizationSchemas.SummarizationAlignmentVerdict;
import dev.jeval.metrics.SummarizationSchemas.SummarizationCoverageVerdict;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SummarizationMetric implements Metric {
    private final EvaluationModel model;
    private final boolean includeReason;
    private final boolean strictMode;
    private final double threshold;
    private final int n;
    private final Integer truthsExtractionLimit;
    private double score;
    private String reason;
    private boolean success;
    private List<String> assessmentQuestions;
    private List<String> truths = List.of();
    private List<String> claims = List.of();
    private List<SummarizationCoverageVerdict> coverageVerdicts = List.of();
    private List<SummarizationAlignmentVerdict> alignmentVerdicts = List.of();
    private Map<String, Double> scoreBreakdown = Map.of();
    private boolean multimodal;

    public SummarizationMetric() {
        this(0.5, 5, null, true, false, null);
    }

    public SummarizationMetric(EvaluationModel model) {
        this(model, 0.5, 5, null, true, false, null);
    }

    public SummarizationMetric(
            double threshold,
            int n,
            List<String> assessmentQuestions,
            boolean includeReason,
            boolean strictMode,
            Integer truthsExtractionLimit) {
        this(null, threshold, n, assessmentQuestions, includeReason, strictMode, truthsExtractionLimit);
    }

    public SummarizationMetric(
            EvaluationModel model,
            double threshold,
            int n,
            List<String> assessmentQuestions,
            boolean includeReason,
            boolean strictMode,
            Integer truthsExtractionLimit) {
        this.model = model;
        this.includeReason = includeReason;
        this.strictMode = strictMode;
        this.threshold = strictMode ? 1.0 : threshold;
        this.n = n;
        this.assessmentQuestions = assessmentQuestions == null || assessmentQuestions.isEmpty()
                ? null
                : List.copyOf(assessmentQuestions);
        this.truthsExtractionLimit = truthsExtractionLimit == null ? null : Math.max(truthsExtractionLimit, 0);
    }

    @Override
    public MetricResult measure(LlmTestCase testCase) {
        MetricUtils.checkLlmTestCaseParams(
                testCase, List.of(SingleTurnParam.INPUT, SingleTurnParam.ACTUAL_OUTPUT), name());

        multimodal = testCase.multimodal();
        truths = List.copyOf(generateTruths(testCase.input()));
        claims = List.copyOf(generateClaims(testCase.actualOutput()));
        coverageVerdicts = List.copyOf(generateCoverageVerdicts(testCase));
        alignmentVerdicts = claims.isEmpty() ? List.of() : List.copyOf(generateAlignmentVerdicts());
        var alignmentScore = calculateScore(ScoreType.ALIGNMENT);
        var coverageScore = calculateScore(ScoreType.COVERAGE);
        var breakdown = new LinkedHashMap<String, Double>();
        breakdown.put(ScoreType.ALIGNMENT.value(), alignmentScore);
        breakdown.put(ScoreType.COVERAGE.value(), coverageScore);
        scoreBreakdown = Map.copyOf(breakdown);
        score = Math.min(alignmentScore, coverageScore);
        reason = includeReason ? generateReason() : null;
        success = score >= threshold;
        return new MetricResult(name(), score, threshold, success, reason);
    }

    public String name() {
        return "Summarization";
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

    public List<String> truths() {
        return truths;
    }

    public List<String> claims() {
        return claims;
    }

    public List<String> assessmentQuestions() {
        return assessmentQuestions;
    }

    public List<SummarizationCoverageVerdict> coverageVerdicts() {
        return coverageVerdicts;
    }

    public List<SummarizationAlignmentVerdict> alignmentVerdicts() {
        return alignmentVerdicts;
    }

    public Map<String, Double> scoreBreakdown() {
        return scoreBreakdown;
    }

    protected List<String> generateTruths(String text) {
        requireModel();
        return FaithfulnessSchemas.parseTruths(
                model.generate(FaithfulnessPrompts.generateTruths(List.of(text), truthsExtractionLimit, multimodal)))
                .truths();
    }

    protected List<String> generateClaims(String text) {
        requireModel();
        return FaithfulnessSchemas.parseClaims(
                model.generate(FaithfulnessPrompts.generateClaims(text, multimodal))).claims();
    }

    protected List<SummarizationCoverageVerdict> generateCoverageVerdicts(LlmTestCase testCase) {
        requireModel();
        if (assessmentQuestions == null) {
            assessmentQuestions = List.copyOf(SummarizationSchemas.parseQuestions(
                    model.generate(SummarizationPrompts.generateQuestions(testCase.input(), n))).questions());
        }
        var originalAnswers = SummarizationSchemas.parseAnswers(
                model.generate(SummarizationPrompts.generateAnswers(testCase.input(), assessmentQuestions))).answers();
        var summaryAnswers = SummarizationSchemas.parseAnswers(
                model.generate(SummarizationPrompts.generateAnswers(testCase.actualOutput(), assessmentQuestions))).answers();
        if (originalAnswers.size() != summaryAnswers.size()) {
            throw new IllegalArgumentException("Number of verdicts generated does not equal.");
        }
        var verdicts = new java.util.ArrayList<SummarizationCoverageVerdict>();
        for (var i = 0; i < originalAnswers.size(); i++) {
            verdicts.add(new SummarizationCoverageVerdict(
                    summaryAnswers.get(i), originalAnswers.get(i), assessmentQuestions.get(i)));
        }
        return verdicts;
    }

    protected List<SummarizationAlignmentVerdict> generateAlignmentVerdicts() {
        requireModel();
        return SummarizationSchemas.parseVerdicts(
                model.generate(SummarizationPrompts.generateAlignmentVerdicts(
                        claims, String.join("\n\n", truths)))).verdicts();
    }

    protected String generateReason() {
        requireModel();
        var contradictions = alignmentVerdicts.stream()
                .filter(verdict -> "no".equals(verdict.verdict().strip().toLowerCase()))
                .map(SummarizationAlignmentVerdict::reason)
                .toList();
        var redundancies = alignmentVerdicts.stream()
                .filter(verdict -> "idk".equals(verdict.verdict().strip().toLowerCase()))
                .map(SummarizationAlignmentVerdict::reason)
                .toList();
        var questions = coverageVerdicts.stream()
                .filter(verdict -> "yes".equals(verdict.originalVerdict().strip().toLowerCase())
                        && "no".equals(verdict.summaryVerdict().strip().toLowerCase()))
                .map(SummarizationCoverageVerdict::question)
                .toList();
        return SummarizationSchemas.parseScoreReason(
                model.generate(SummarizationPrompts.generateReason(score, contradictions, redundancies, questions)))
                .reason();
    }

    private double calculateScore(ScoreType scoreType) {
        if (scoreType == ScoreType.ALIGNMENT) {
            if (alignmentVerdicts.isEmpty()) {
                return 0.0;
            }
            var faithful = 0;
            for (var verdict : alignmentVerdicts) {
                if ("yes".equals(verdict.verdict().strip().toLowerCase())) {
                    faithful++;
                }
            }
            return strictScore((double) faithful / alignmentVerdicts.size());
        }

        if (assessmentQuestions == null) {
            return 1.0;
        }
        var total = 0;
        var covered = 0;
        for (var verdict : coverageVerdicts) {
            if ("yes".equals(verdict.originalVerdict().strip().toLowerCase())) {
                total++;
                if ("yes".equals(verdict.summaryVerdict().strip().toLowerCase())) {
                    covered++;
                }
            }
        }
        return total == 0 ? 0.0 : strictScore((double) covered / total);
    }

    private double strictScore(double value) {
        return strictMode && value < threshold ? 0.0 : value;
    }

    private void requireModel() {
        if (model == null) {
            throw new UnsupportedOperationException("Summarization generation requires a model provider");
        }
    }
}
