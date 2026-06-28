package dev.jeval.metrics;

import dev.jeval.ConversationalMetric;
import dev.jeval.ConversationalTestCase;
import dev.jeval.EvaluationModel;
import dev.jeval.MetricResult;
import dev.jeval.MultiTurnParam;
import dev.jeval.RetrievedContextData;
import dev.jeval.Turn;
import dev.jeval.metrics.FaithfulnessSchemas.FaithfulnessScoreReason;
import dev.jeval.metrics.FaithfulnessSchemas.FaithfulnessVerdict;
import java.util.ArrayList;
import java.util.List;

public class TurnFaithfulnessMetric implements ConversationalMetric {
    private static final String NO_CONTEXT_REASON =
            "There were no retrieval contexts in your turns to evaluate, hence the score is 1";

    private final EvaluationModel model;
    private final boolean includeReason;
    private final boolean strictMode;
    private final boolean penalizeAmbiguousClaims;
    private final Integer truthsExtractionLimit;
    private final int windowSize;
    private final double threshold;
    private List<InteractionFaithfulnessScore> scores = List.of();
    private double score;
    private String reason;
    private boolean success;
    private boolean multimodal;

    public TurnFaithfulnessMetric() {
        this(0.5, true, false, false, null, 10);
    }

    public TurnFaithfulnessMetric(EvaluationModel model) {
        this(model, 0.5, true, false, false, null, 10);
    }

    public TurnFaithfulnessMetric(
            double threshold,
            boolean includeReason,
            boolean strictMode,
            boolean penalizeAmbiguousClaims,
            Integer truthsExtractionLimit,
            int windowSize) {
        this(null, threshold, includeReason, strictMode, penalizeAmbiguousClaims, truthsExtractionLimit, windowSize);
    }

    public TurnFaithfulnessMetric(
            EvaluationModel model,
            double threshold,
            boolean includeReason,
            boolean strictMode,
            boolean penalizeAmbiguousClaims,
            Integer truthsExtractionLimit,
            int windowSize) {
        this.model = model;
        this.includeReason = includeReason;
        this.strictMode = strictMode;
        this.penalizeAmbiguousClaims = penalizeAmbiguousClaims;
        this.truthsExtractionLimit = truthsExtractionLimit == null ? null : Math.max(truthsExtractionLimit, 0);
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
        var generated = new ArrayList<InteractionFaithfulnessScore>();
        for (var window : slidingWindows(MetricUtils.getUnitInteractions(testCase.turns()))) {
            generated.addAll(getFaithfulnessScores(window));
        }
        scores = List.copyOf(generated);
        score = calculateScore();
        success = score >= threshold;
        reason = includeReason ? reasonOrDefault() : null;
        return new MetricResult(name(), score, threshold, success, reason);
    }

    public String name() {
        return "Turn Faithfulness";
    }

    public List<InteractionFaithfulnessScore> scores() {
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

    protected List<InteractionFaithfulnessScore> getFaithfulnessScores(List<Turn> turnsWindow) {
        var assistantContent = new StringBuilder();
        var retrievalContext = new ArrayList<String>();
        for (var turn : turnsWindow) {
            if ("assistant".equals(turn.role())) {
                assistantContent.append("\n").append(turn.content());
                if (turn.retrievalContext() != null) {
                    retrievalContext.addAll(RetrievedContextData.textValues(turn.retrievalContext()));
                }
            }
        }
        if (retrievalContext.isEmpty()) {
            return List.of(new InteractionFaithfulnessScore(1.0, noContextInteractionReason(), List.of(), List.of(), List.of()));
        }

        var truths = generateTruths(retrievalContext);
        var claims = generateClaims(assistantContent.toString());
        var verdicts = claims.isEmpty() ? List.<FaithfulnessVerdict>of() : generateVerdicts(truths, claims);
        var interactionScore = calculateInteractionScore(verdicts);
        if (strictMode && interactionScore < threshold) {
            interactionScore = 0.0;
        }
        var interactionReason = includeReason
                ? verdicts.isEmpty() ? "<no claims to verify>" : generateInteractionReason(interactionScore, verdicts).reason()
                : null;
        return List.of(new InteractionFaithfulnessScore(interactionScore, interactionReason, claims, truths, verdicts));
    }

    protected FaithfulnessScoreReason generateReason() {
        requireModel();
        return FaithfulnessSchemas.parseScoreReason(model.generate("""
                You are an AI evaluator producing a single final explanation for the TurnFaithfulnessMetric result.

                Context:
                This metric evaluates conversational faithfulness by extracting truths from retrieval context, extracting claims from the assistant's output, and generating verdicts that compare each claim against the truths. Each interaction yields a reason indicating why a verdict failed or succeeded. You are given all those reasons.

                **
                IMPORTANT: Please make sure to only return in JSON format, with the 'reason' key providing the reason.
                Example JSON:
                {
                  "reason": "The score is <turn_faithfulness_score> because <your_reason>."
                }

                Inputs:
                - final_score: the averaged score across all interactions.
                - success: whether the metric passed or failed
                - reasons: a list of textual reasons generated from individual verdicts.

                Instructions:
                1. Read all reasons and synthesize them into one unified explanation.
                2. Describe patterns of claim-truth mismatches, contradictions, hallucinations, unsupported statements, or image-related errors if present.
                3. Do not repeat every reason; merge them into a concise, coherent narrative.
                4. If the metric failed, state the dominant failure modes. If it passed, state why the model's claims aligned with truths.
                5. Output a single paragraph with no lists, no bullets, no markup.

                Final Score:
                %s

                Success:
                %s

                Interaction Reasons:
                %s

                Now give me a final reason that explains why the metric passed or failed. Output ONLY the reason and nothing else.

                JSON:
                """.formatted(score, success, scores.stream().map(InteractionFaithfulnessScore::reason).toList())));
    }

    private List<String> generateTruths(List<String> retrievalContext) {
        requireModel();
        return FaithfulnessSchemas.parseTruths(
                model.generate(FaithfulnessPrompts.generateTruths(retrievalContext, truthsExtractionLimit, multimodal)))
                .truths();
    }

    private List<String> generateClaims(String assistantContent) {
        requireModel();
        return FaithfulnessSchemas.parseClaims(
                model.generate(FaithfulnessPrompts.generateClaims(assistantContent, multimodal)))
                .claims();
    }

    private List<FaithfulnessVerdict> generateVerdicts(List<String> truths, List<String> claims) {
        requireModel();
        return FaithfulnessSchemas.parseVerdicts(
                model.generate(FaithfulnessPrompts.generateVerdicts(truths, claims, multimodal)))
                .verdicts();
    }

    private FaithfulnessScoreReason generateInteractionReason(
            double interactionScore,
            List<FaithfulnessVerdict> verdicts) {
        requireModel();
        var contradictions = verdicts.stream()
                .filter(verdict -> "no".equalsIgnoreCase(verdict.verdict().strip())
                        || penalizeAmbiguousClaims && "idk".equalsIgnoreCase(verdict.verdict().strip()))
                .map(verdict -> "idk".equalsIgnoreCase(verdict.verdict().strip())
                        ? "(Ambiguous) " + verdict.reason()
                        : verdict.reason())
                .toList();
        return FaithfulnessSchemas.parseScoreReason(
                model.generate(FaithfulnessPrompts.generateReason(interactionScore, contradictions)));
    }

    private double calculateScore() {
        if (scores.isEmpty()) {
            return 1.0;
        }
        var rawScore = scores.stream().mapToDouble(InteractionFaithfulnessScore::score).average().orElse(1.0);
        return strictMode && rawScore < threshold ? 0.0 : rawScore;
    }

    private String reasonOrDefault() {
        if (scores.isEmpty() || scores.stream().allMatch(score -> score.verdicts().isEmpty() && score.truths().isEmpty())) {
            return NO_CONTEXT_REASON;
        }
        return generateReason().reason();
    }

    private double calculateInteractionScore(List<FaithfulnessVerdict> verdicts) {
        if (verdicts.isEmpty()) {
            return 1.0;
        }
        var faithfulnessCount = 0;
        for (var verdict : verdicts) {
            var value = verdict.verdict().strip().toLowerCase();
            if (!"no".equals(value)) {
                faithfulnessCount++;
            }
            if (penalizeAmbiguousClaims && "idk".equals(value)) {
                faithfulnessCount--;
            }
        }
        return faithfulnessCount / (double) verdicts.size();
    }

    private static String noContextInteractionReason() {
        return "There were no retrieval contexts in the given turns to evaluate the faithfulness.";
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
            throw new UnsupportedOperationException("Turn Faithfulness generation requires a model provider");
        }
    }

    public record InteractionFaithfulnessScore(
            double score,
            String reason,
            List<String> claims,
            List<String> truths,
            List<FaithfulnessVerdict> verdicts) {
        public InteractionFaithfulnessScore {
            claims = claims == null ? List.of() : List.copyOf(claims);
            truths = truths == null ? List.of() : List.copyOf(truths);
            verdicts = verdicts == null ? List.of() : List.copyOf(verdicts);
        }
    }
}
