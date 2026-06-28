package dev.jeval.metrics;

import dev.jeval.ConversationalMetric;
import dev.jeval.ConversationalTestCase;
import dev.jeval.EvaluationModel;
import dev.jeval.MetricResult;
import dev.jeval.MultiTurnParam;
import dev.jeval.RetrievedContextData;
import dev.jeval.Turn;
import dev.jeval.metrics.ContextualRecallSchemas.ContextualRecallScoreReason;
import dev.jeval.metrics.ContextualRecallSchemas.ContextualRecallVerdict;
import java.util.ArrayList;
import java.util.List;

public class TurnContextualRecallMetric implements ConversationalMetric {
    private static final String NO_CONTEXT_REASON =
            "There were no retrieval contexts in your turns to evaluate, hence the score is 1";

    private final EvaluationModel model;
    private final boolean includeReason;
    private final boolean strictMode;
    private final int windowSize;
    private final double threshold;
    private List<InteractionContextualRecallScore> scores = List.of();
    private double score;
    private String reason;
    private boolean success;
    private boolean multimodal;

    public TurnContextualRecallMetric() {
        this(0.5, true, false, 10);
    }

    public TurnContextualRecallMetric(EvaluationModel model) {
        this(model, 0.5, true, false, 10);
    }

    public TurnContextualRecallMetric(double threshold, boolean includeReason, boolean strictMode, int windowSize) {
        this(null, threshold, includeReason, strictMode, windowSize);
    }

    public TurnContextualRecallMetric(
            EvaluationModel model,
            double threshold,
            boolean includeReason,
            boolean strictMode,
            int windowSize) {
        this.model = model;
        this.includeReason = includeReason;
        this.strictMode = strictMode;
        this.windowSize = Math.max(1, windowSize);
        this.threshold = strictMode ? 1.0 : threshold;
    }

    @Override
    public MetricResult measure(ConversationalTestCase testCase) {
        MetricUtils.checkConversationalTestCaseParams(
                testCase,
                List.of(
                        MultiTurnParam.CONTENT,
                        MultiTurnParam.ROLE,
                        MultiTurnParam.RETRIEVAL_CONTEXT,
                        MultiTurnParam.EXPECTED_OUTCOME),
                name());
        multimodal = testCase.multimodal();
        var generated = new ArrayList<InteractionContextualRecallScore>();
        for (var window : slidingWindows(MetricUtils.getUnitInteractions(testCase.turns()))) {
            generated.addAll(getContextualRecallScores(window, testCase.expectedOutcome()));
        }
        scores = List.copyOf(generated);
        score = calculateScore();
        success = score >= threshold;
        reason = includeReason ? reasonOrDefault() : null;
        return new MetricResult(name(), score, threshold, success, reason);
    }

    public String name() {
        return "Turn Contextual Recall";
    }

    public List<InteractionContextualRecallScore> scores() {
        return scores;
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

    protected List<InteractionContextualRecallScore> getContextualRecallScores(
            List<Turn> turnsWindow,
            String expectedOutcome) {
        var retrievalContext = new ArrayList<String>();
        for (var turn : turnsWindow) {
            if ("assistant".equals(turn.role()) && turn.retrievalContext() != null) {
                retrievalContext.addAll(RetrievedContextData.textValues(turn.retrievalContext()));
            }
        }
        if (retrievalContext.isEmpty()) {
            return List.of(new InteractionContextualRecallScore(1.0, noContextInteractionReason(), List.of()));
        }

        var verdicts = generateVerdicts(expectedOutcome, retrievalContext);
        var interactionScore = calculateInteractionScore(verdicts);
        if (strictMode && interactionScore < threshold) {
            interactionScore = 0.0;
        }
        var interactionReason = includeReason
                ? generateInteractionReason(expectedOutcome, interactionScore, verdicts).reason()
                : null;
        return List.of(new InteractionContextualRecallScore(interactionScore, interactionReason, verdicts));
    }

    protected ContextualRecallScoreReason generateReason() {
        requireModel();
        return ContextualRecallSchemas.parseScoreReason(model.generate("""
                You are an AI evaluator producing a single final explanation for the TurnContextualRecallMetric result.

                Context:
                This metric evaluates conversational contextual recall by determining whether sentences in the assistant output can be attributed to the retrieval context for each interaction. Each interaction yields a reason indicating which sentences were supported or unsupported. You are given all those reasons.

                **
                IMPORTANT: Please make sure to only return in JSON format, with the 'reason' key providing the reason.
                Example JSON:
                {
                  "reason": "The score is <contextual_recall_score> because <your_reason>."
                }

                Inputs:
                - final_score: the averaged score across all interactions.
                - success: whether the metric passed or failed
                - reasons: a list of textual reasons generated from individual interactions.

                Instructions:
                1. Read all reasons and synthesize them into one unified explanation.
                2. Describe patterns of unsupported sentences, missing context coverage, or well-attributed outputs if present.
                3. Do not repeat every reason; merge them into a concise, coherent narrative.
                4. If the metric failed, state the dominant failure modes. If it passed, state why the assistant output was well-supported by retrieval context.
                5. Output a single paragraph with no lists, no bullets, no markup.

                Final Score:
                %s

                Success:
                %s

                Interaction Reasons:
                %s

                Now give me a final reason that explains why the metric passed or failed. Output ONLY the reason and nothing else.

                JSON:
                """.formatted(score, success, scores.stream().map(InteractionContextualRecallScore::reason).toList())));
    }

    private List<ContextualRecallVerdict> generateVerdicts(String expectedOutcome, List<String> retrievalContext) {
        requireModel();
        return ContextualRecallSchemas.parseVerdicts(
                model.generate(ContextualRecallPrompts.generateVerdicts(expectedOutcome, retrievalContext, multimodal)))
                .verdicts();
    }

    private ContextualRecallScoreReason generateInteractionReason(
            String expectedOutcome,
            double interactionScore,
            List<ContextualRecallVerdict> verdicts) {
        requireModel();
        var supportiveReasons = verdicts.stream()
                .filter(verdict -> "yes".equalsIgnoreCase(verdict.verdict()))
                .map(ContextualRecallVerdict::reason)
                .toList();
        var unsupportiveReasons = verdicts.stream()
                .filter(verdict -> !"yes".equalsIgnoreCase(verdict.verdict()))
                .map(ContextualRecallVerdict::reason)
                .toList();
        return ContextualRecallSchemas.parseScoreReason(model.generate(ContextualRecallPrompts.generateReason(
                expectedOutcome, interactionScore, supportiveReasons, unsupportiveReasons, multimodal)));
    }

    private double calculateScore() {
        if (scores.isEmpty()) {
            return 1.0;
        }
        var rawScore = scores.stream().mapToDouble(InteractionContextualRecallScore::score).average().orElse(1.0);
        return strictMode && rawScore < threshold ? 0.0 : rawScore;
    }

    private String reasonOrDefault() {
        if (scores.isEmpty() || scores.stream().allMatch(score -> score.verdicts().isEmpty())) {
            return NO_CONTEXT_REASON;
        }
        return generateReason().reason();
    }

    private static double calculateInteractionScore(List<ContextualRecallVerdict> verdicts) {
        if (verdicts.isEmpty()) {
            return 1.0;
        }
        var recalled = verdicts.stream()
                .filter(verdict -> "yes".equalsIgnoreCase(verdict.verdict()))
                .count();
        return recalled / (double) verdicts.size();
    }

    private static String noContextInteractionReason() {
        return "There were no retrieval contexts in the given turns to evaluate the contextual recall.";
    }

    private List<List<Turn>> slidingWindows(List<List<Turn>> unitInteractions) {
        var windows = new ArrayList<List<Turn>>();
        for (var i = 0; i < unitInteractions.size(); i++) {
            var flattened = new ArrayList<Turn>();
            for (var unit : unitInteractions.subList(Math.max(0, i - windowSize + 1), i + 1)) {
                flattened.addAll(unit);
            }
            windows.add(List.copyOf(flattened));
        }
        return windows;
    }

    private void requireModel() {
        if (model == null) {
            throw new UnsupportedOperationException("Turn Contextual Recall generation requires a model provider");
        }
    }

    public record InteractionContextualRecallScore(
            double score,
            String reason,
            List<ContextualRecallVerdict> verdicts) {
        public InteractionContextualRecallScore {
            verdicts = verdicts == null ? List.of() : List.copyOf(verdicts);
        }
    }
}
