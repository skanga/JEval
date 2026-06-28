package dev.jeval.metrics;

import dev.jeval.ConversationalMetric;
import dev.jeval.ConversationalTestCase;
import dev.jeval.EvaluationModel;
import dev.jeval.MetricResult;
import dev.jeval.MultiTurnParam;
import dev.jeval.RetrievedContextData;
import dev.jeval.Turn;
import dev.jeval.metrics.ContextualPrecisionSchemas.ContextualPrecisionScoreReason;
import dev.jeval.metrics.ContextualPrecisionSchemas.ContextualPrecisionVerdict;
import java.util.ArrayList;
import java.util.List;

public class TurnContextualPrecisionMetric implements ConversationalMetric {
    private static final String NO_CONTEXT_REASON =
            "There were no retrieval contexts in your turns to evaluate, hence the score is 1";

    private final EvaluationModel model;
    private final boolean includeReason;
    private final boolean strictMode;
    private final int windowSize;
    private final double threshold;
    private List<InteractionContextualPrecisionScore> scores = List.of();
    private double score;
    private String reason;
    private boolean success;
    private boolean multimodal;

    public TurnContextualPrecisionMetric() {
        this(0.5, true, false, 10);
    }

    public TurnContextualPrecisionMetric(EvaluationModel model) {
        this(model, 0.5, true, false, 10);
    }

    public TurnContextualPrecisionMetric(double threshold, boolean includeReason, boolean strictMode, int windowSize) {
        this(null, threshold, includeReason, strictMode, windowSize);
    }

    public TurnContextualPrecisionMetric(
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
        var generated = new ArrayList<InteractionContextualPrecisionScore>();
        for (var window : slidingWindows(MetricUtils.getUnitInteractions(testCase.turns()))) {
            generated.addAll(getContextualPrecisionScores(window, testCase.expectedOutcome()));
        }
        scores = List.copyOf(generated);
        score = calculateScore();
        success = score >= threshold;
        reason = includeReason ? reasonOrDefault() : null;
        return new MetricResult(name(), score, threshold, success, reason);
    }

    public String name() {
        return "Turn Contextual Precision";
    }

    public List<InteractionContextualPrecisionScore> scores() {
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

    protected List<InteractionContextualPrecisionScore> getContextualPrecisionScores(
            List<Turn> turnsWindow,
            String expectedOutcome) {
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
            return List.of(new InteractionContextualPrecisionScore(1.0, noContextInteractionReason(), List.of()));
        }

        var verdicts = generateVerdicts(input.toString(), expectedOutcome, retrievalContext);
        var interactionScore = calculateInteractionScore(verdicts);
        if (strictMode && interactionScore < threshold) {
            interactionScore = 0.0;
        }
        var interactionReason = includeReason
                ? generateInteractionReason(input.toString(), interactionScore, verdicts).reason()
                : null;
        return List.of(new InteractionContextualPrecisionScore(interactionScore, interactionReason, verdicts));
    }

    protected ContextualPrecisionScoreReason generateReason() {
        requireModel();
        return ContextualPrecisionSchemas.parseScoreReason(model.generate("""
                You are an AI evaluator producing a single final explanation for the TurnContextualPrecisionMetric result.

                Context:
                This metric evaluates conversational contextual precision by determining whether relevant nodes in retrieval context are ranked higher than irrelevant nodes for each interaction. Each interaction yields a reason indicating why relevant nodes were well-ranked or poorly-ranked. You are given all those reasons.

                **
                IMPORTANT: Please make sure to only return in JSON format, with the 'reason' key providing the reason.
                Example JSON:
                {
                  "reason": "The score is <contextual_precision_score> because <your_reason>."
                }

                Inputs:
                - final_score: the averaged score across all interactions.
                - success: whether the metric passed or failed
                - reasons: a list of textual reasons generated from individual interactions.

                Instructions:
                1. Read all reasons and synthesize them into one unified explanation.
                2. Describe patterns of ranking issues, irrelevant nodes appearing before relevant ones, or well-structured retrieval contexts if present.
                3. Do not repeat every reason; merge them into a concise, coherent narrative.
                4. If the metric failed, state the dominant failure modes. If it passed, state why the retrieval context ranking was effective.
                5. Output a single paragraph with no lists, no bullets, no markup.

                Final Score:
                %s

                Success:
                %s

                Interaction Reasons:
                %s

                Now give me a final reason that explains why the metric passed or failed. Output ONLY the reason and nothing else.

                JSON:
                """.formatted(score, success, scores.stream().map(InteractionContextualPrecisionScore::reason).toList())));
    }

    private List<ContextualPrecisionVerdict> generateVerdicts(
            String input,
            String expectedOutcome,
            List<String> retrievalContext) {
        requireModel();
        return ContextualPrecisionSchemas.parseVerdicts(model.generate(
                ContextualPrecisionPrompts.generateVerdicts(input, expectedOutcome, retrievalContext, multimodal)))
                .verdicts();
    }

    private ContextualPrecisionScoreReason generateInteractionReason(
            String input,
            double interactionScore,
            List<ContextualPrecisionVerdict> verdicts) {
        requireModel();
        return ContextualPrecisionSchemas.parseScoreReason(model.generate(
                ContextualPrecisionPrompts.generateReason(input, interactionScore, verdicts, multimodal)));
    }

    private double calculateScore() {
        if (scores.isEmpty()) {
            return 1.0;
        }
        var rawScore = scores.stream().mapToDouble(InteractionContextualPrecisionScore::score).average().orElse(1.0);
        return strictMode && rawScore < threshold ? 0.0 : rawScore;
    }

    private String reasonOrDefault() {
        if (scores.isEmpty() || scores.stream().allMatch(score -> score.verdicts().isEmpty())) {
            return NO_CONTEXT_REASON;
        }
        return generateReason().reason();
    }

    private static double calculateInteractionScore(List<ContextualPrecisionVerdict> verdicts) {
        if (verdicts.isEmpty()) {
            return 0.0;
        }
        var weightedPrecisionAtK = 0.0;
        var relevantNodes = 0;
        for (var k = 0; k < verdicts.size(); k++) {
            if ("yes".equalsIgnoreCase(verdicts.get(k).verdict().strip())) {
                relevantNodes++;
                weightedPrecisionAtK += relevantNodes / (double) (k + 1);
            }
        }
        return relevantNodes == 0 ? 0.0 : weightedPrecisionAtK / relevantNodes;
    }

    private static String noContextInteractionReason() {
        return "There were no retrieval contexts in the given turns to evaluate the contextual precision.";
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
            throw new UnsupportedOperationException("Turn Contextual Precision generation requires a model provider");
        }
    }

    public record InteractionContextualPrecisionScore(
            double score,
            String reason,
            List<ContextualPrecisionVerdict> verdicts) {
        public InteractionContextualPrecisionScore {
            verdicts = verdicts == null ? List.of() : List.copyOf(verdicts);
        }
    }
}
