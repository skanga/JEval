package dev.jeval.metrics;

import dev.jeval.ConversationalMetric;
import dev.jeval.ConversationalTestCase;
import dev.jeval.EvaluationModel;
import dev.jeval.MetricResult;
import dev.jeval.MultiTurnParam;
import dev.jeval.RetrievedContextData;
import dev.jeval.Turn;
import dev.jeval.metrics.ContextualRelevancySchemas.ContextualRelevancyScoreReason;
import dev.jeval.metrics.ContextualRelevancySchemas.ContextualRelevancyVerdict;
import dev.jeval.metrics.ContextualRelevancySchemas.ContextualRelevancyVerdicts;
import java.util.ArrayList;
import java.util.List;

public class TurnContextualRelevancyMetric implements ConversationalMetric {
    private static final String NO_CONTEXT_REASON =
            "There were no retrieval contexts in your turns to evaluate, hence the score is 1";

    private final EvaluationModel model;
    private final boolean includeReason;
    private final boolean strictMode;
    private final int windowSize;
    private final double threshold;
    private List<InteractionContextualRelevancyScore> scores = List.of();
    private double score;
    private String reason;
    private boolean success;
    private boolean multimodal;

    public TurnContextualRelevancyMetric() {
        this(0.5, true, false, 10);
    }

    public TurnContextualRelevancyMetric(EvaluationModel model) {
        this(model, 0.5, true, false, 10);
    }

    public TurnContextualRelevancyMetric(double threshold, boolean includeReason, boolean strictMode, int windowSize) {
        this(null, threshold, includeReason, strictMode, windowSize);
    }

    public TurnContextualRelevancyMetric(
            EvaluationModel model,
            double threshold,
            boolean includeReason,
            boolean strictMode,
            int windowSize) {
        if (!Double.isFinite(threshold)) {
            throw new IllegalArgumentException("TurnContextualRelevancy threshold must be finite");
        }
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
                List.of(MultiTurnParam.CONTENT, MultiTurnParam.ROLE, MultiTurnParam.RETRIEVAL_CONTEXT),
                name());
        multimodal = testCase.multimodal();
        var generated = new ArrayList<InteractionContextualRelevancyScore>();
        for (var window : slidingWindows(MetricUtils.getUnitInteractions(testCase.turns()))) {
            generated.addAll(getContextualRelevancyScores(window));
        }
        scores = List.copyOf(generated);
        score = calculateScore();
        success = score >= threshold;
        reason = includeReason ? reasonOrDefault() : null;
        return new MetricResult(name(), score, threshold, success, reason);
    }

    public String name() {
        return "Turn Contextual Relevancy";
    }

    public List<InteractionContextualRelevancyScore> scores() {
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

    protected List<InteractionContextualRelevancyScore> getContextualRelevancyScores(List<Turn> turnsWindow) {
        var input = new StringBuilder();
        var retrievalContext = new ArrayList<String>();
        for (var turn : turnsWindow) {
            if ("user".equals(turn.role())) {
                input.append("\n").append(turn.content()).append(" ");
            } else if (turn.retrievalContext() != null) {
                retrievalContext.addAll(RetrievedContextData.textValues(turn.retrievalContext()));
            }
        }
        if (retrievalContext.isEmpty()) {
            return List.of(new InteractionContextualRelevancyScore(1.0, noContextInteractionReason(), List.of()));
        }

        var verdicts = new ArrayList<ContextualRelevancyVerdict>();
        for (var context : retrievalContext) {
            verdicts.addAll(generateVerdicts(input.toString(), context, multimodal).verdicts());
        }
        var interactionScore = calculateInteractionScore(verdicts);
        if (strictMode && interactionScore < threshold) {
            interactionScore = 0.0;
        }
        var interactionReason = includeReason ? generateInteractionReason(input.toString(), interactionScore, verdicts).reason() : null;
        return List.of(new InteractionContextualRelevancyScore(interactionScore, interactionReason, verdicts));
    }

    protected ContextualRelevancyScoreReason generateReason() {
        requireModel();
        return ContextualRelevancySchemas.parseScoreReason(model.generate("""
                You are an AI evaluator producing a single final explanation for the TurnContextualRelevancyMetric result.

                Context:
                This metric evaluates conversational contextual relevancy by determining whether statements in the retrieval context are relevant to the user message for each interaction. Each interaction yields a reason indicating which statements were relevant or irrelevant. You are given all those reasons.

                **
                IMPORTANT: Please make sure to only return in JSON format, with the 'reason' key providing the reason.
                Example JSON:
                {
                  "reason": "The score is <contextual_relevancy_score> because <your_reason>."
                }

                Inputs:
                - final_score: the averaged score across all interactions.
                - success: whether the metric passed or failed
                - reasons: a list of textual reasons generated from individual interactions.

                Instructions:
                1. Read all reasons and synthesize them into one unified explanation.
                2. Describe patterns of irrelevant statements, off-topic context, or well-targeted retrieval if present.
                3. Do not repeat every reason; merge them into a concise, coherent narrative.
                4. If the metric failed, state the dominant failure modes. If it passed, state why the retrieval context was relevant to user messages.
                5. Output a single paragraph with no lists, no bullets, no markup.

                Final Score:
                %s

                Success:
                %s

                Interaction Reasons:
                %s

                Now give me a final reason that explains why the metric passed or failed. Output ONLY the reason and nothing else.

                JSON:
                """.formatted(score, success, scores.stream().map(InteractionContextualRelevancyScore::reason).toList())));
    }

    private ContextualRelevancyVerdicts generateVerdicts(String input, String context, boolean multimodal) {
        requireModel();
        return ContextualRelevancySchemas.parseVerdicts(
                model.generate(ContextualRelevancyPrompts.generateVerdicts(input, context, multimodal)));
    }

    private ContextualRelevancyScoreReason generateInteractionReason(
            String input,
            double interactionScore,
            List<ContextualRelevancyVerdict> verdicts) {
        requireModel();
        var irrelevantStatements = new ArrayList<String>();
        var relevantStatements = new ArrayList<String>();
        for (var verdict : verdicts) {
            if ("yes".equalsIgnoreCase(verdict.verdict())) {
                relevantStatements.add(verdict.statement());
            } else {
                irrelevantStatements.add(verdict.statement() + ": " + verdict.reason());
            }
        }
        return ContextualRelevancySchemas.parseScoreReason(model.generate(
                ContextualRelevancyPrompts.generateReason(
                        input, interactionScore, irrelevantStatements, relevantStatements, multimodal)));
    }

    private double calculateScore() {
        if (scores.isEmpty()) {
            return 1.0;
        }
        var rawScore = scores.stream().mapToDouble(InteractionContextualRelevancyScore::score).average().orElse(1.0);
        return strictMode && rawScore < threshold ? 0.0 : rawScore;
    }

    private String reasonOrDefault() {
        if (scores.isEmpty() || scores.stream().allMatch(score -> score.verdicts().isEmpty())) {
            return NO_CONTEXT_REASON;
        }
        return generateReason().reason();
    }

    private static double calculateInteractionScore(List<ContextualRelevancyVerdict> verdicts) {
        if (verdicts.isEmpty()) {
            return 1.0;
        }
        var relevant = verdicts.stream()
                .filter(verdict -> "yes".equalsIgnoreCase(verdict.verdict()))
                .count();
        return relevant / (double) verdicts.size();
    }

    private static String noContextInteractionReason() {
        return "There were no retrieval contexts in the given turns to evaluate the contextual relevancy.";
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
            throw new UnsupportedOperationException("Turn Contextual Relevancy generation requires a model provider");
        }
    }

    public record InteractionContextualRelevancyScore(
            double score,
            String reason,
            List<ContextualRelevancyVerdict> verdicts) {
        public InteractionContextualRelevancyScore {
            verdicts = verdicts == null ? List.of() : List.copyOf(verdicts);
        }
    }
}
