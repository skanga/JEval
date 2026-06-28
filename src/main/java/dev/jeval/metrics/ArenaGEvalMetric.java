package dev.jeval.metrics;

import dev.jeval.ArenaTestCase;
import dev.jeval.ArenaMetric;
import dev.jeval.EvaluationModel;
import dev.jeval.SingleTurnParam;
import dev.jeval.Utils;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ArenaGEvalMetric implements ArenaMetric {
    private static final List<String> FAKE_NAMES = List.of(
            "Alice", "Bob", "Charlie", "Diana", "Eve", "Frank", "Grace", "Henry", "Iris", "Jack");

    private final EvaluationModel model;
    private final String name;
    private final List<SingleTurnParam> evaluationParams;
    private final String criteria;
    private List<String> evaluationSteps;
    private String winner;
    private String reason;
    private boolean success;

    public ArenaGEvalMetric(String name, List<SingleTurnParam> evaluationParams, String criteria) {
        this(null, name, evaluationParams, criteria, null);
    }

    public ArenaGEvalMetric(
            EvaluationModel model,
            String name,
            List<SingleTurnParam> evaluationParams,
            String criteria) {
        this(model, name, evaluationParams, criteria, null);
    }

    public ArenaGEvalMetric(
            EvaluationModel model,
            String name,
            List<SingleTurnParam> evaluationParams,
            String criteria,
            List<String> evaluationSteps) {
        if (evaluationParams == null || evaluationParams.isEmpty()) {
            throw new IllegalArgumentException("evaluation_params cannot be an empty list.");
        }
        GEvalUtils.validateCriteriaAndEvaluationSteps(criteria, evaluationSteps);
        this.model = model;
        this.name = name;
        this.evaluationParams = List.copyOf(evaluationParams);
        this.criteria = criteria;
        this.evaluationSteps = evaluationSteps == null ? null : List.copyOf(evaluationSteps);
    }

    @Override
    public String measure(ArenaTestCase testCase) {
        MetricUtils.checkArenaTestCaseParams(testCase, evaluationParams, name());
        if (evaluationSteps == null) {
            evaluationSteps = List.copyOf(generateEvaluationSteps(testCase.multimodal()));
        }
        var formatted = formatArenaTestCase(testCase);
        var comparison = compare(formatted.testCase(), testCase.multimodal());
        var maskedWinner = comparison.winner();
        winner = formatted.dummyToRealNames().get(maskedWinner);
        if (winner == null) {
            throw new IllegalArgumentException("Evaluation model returned an unknown arena winner: " + maskedWinner);
        }
        reason = rewriteReason(comparison.reason(), formatted.dummyToRealNames());
        success = true;
        return winner;
    }

    public String name() {
        return name + " [Arena GEval]";
    }

    public List<String> evaluationSteps() {
        return evaluationSteps;
    }

    public String winner() {
        return winner;
    }

    public String reason() {
        return reason;
    }

    @Override
    public boolean success() {
        return success;
    }

    protected List<String> generateEvaluationSteps(boolean multimodal) {
        requireModel();
        return GEvalSchemas.parseSteps(model.generate(GEvalPrompts.generateArenaEvaluationSteps(
                criteria, GEvalUtils.constructParamsString(evaluationParams), multimodal))).steps();
    }

    protected ArenaGEvalSchemas.Winner compare(String formattedTestCase, boolean multimodal) {
        requireModel();
        return ArenaGEvalSchemas.parseWinner(model.generate("""
                You are a judge. Given the following evaluation steps, select the single contestant that best aligns with the evaluation steps.
                %s

                Return a JSON object with two fields:
                - "winner": the exact contestant name that is best aligned with the evaluation steps. This MUST NOT contain any symbols or $ wrappers.
                - "reason": a brief explanation for why the contestant was chosen. This must mention specific strengths or shortcomings, and reference relevant details from BOTH the winner's parameters AND ALL the other contestants' parameters. Refer to contestants ONLY by their unique contestant name, and wrap contestant names as $contestant_name$ in the reason.

                Only return valid JSON. Do not include any extra commentary or text.

                Evaluation Steps:
                %s

                Contestants:
                %s

                Parameters:
                %s

                JSON:
                """.formatted(
                GEvalPrompts.multimodalRules(multimodal),
                GEvalUtils.numberEvaluationSteps(evaluationSteps),
                formattedTestCase,
                GEvalUtils.constructParamsString(evaluationParams))));
    }

    protected String rewriteReason(String maskedReason, Map<String, String> dummyToRealNames) {
        requireModel();
        return ArenaGEvalSchemas.parseRewrittenReason(model.generate("""
                Given the following reason that explains which contestant is the winner, rewrite the reason to REPLACE all contestant names with their real names.

                The contestant names are wrapped in $name$ format (e.g., $Alice$, $Bob$, $Charlie$).
                Use the provided dummy-to-real names mapping to convert each $dummy_name$ to its corresponding real name.

                Dummy-to-real mapping:
                %s

                Reason:
                %s

                Return only the rewritten reason as JSON with key rewritten_reason.

                JSON:
                """.formatted(dummyToRealNames, maskedReason))).rewrittenReason();
    }

    private FormattedArenaTestCase formatArenaTestCase(ArenaTestCase testCase) {
        var dummyToRealNames = new LinkedHashMap<String, String>();
        var contestants = new LinkedHashMap<String, Map<String, Object>>();
        for (var i = 0; i < testCase.contestants().size(); i++) {
            var fakeName = fakeName(i);
            var contestant = testCase.contestants().get(i);
            dummyToRealNames.put(fakeName, contestant.name());
            contestants.put(fakeName, contestantFields(contestant.testCase()));
        }
        var first = testCase.contestants().getFirst().testCase();
        var data = new LinkedHashMap<String, Object>();
        if (evaluationParams.contains(SingleTurnParam.INPUT)) {
            data.put("input", first.input());
        }
        if (evaluationParams.contains(SingleTurnParam.EXPECTED_OUTPUT)) {
            data.put("expected_output", first.expectedOutput());
        }
        data.put("arena_test_cases", contestants);
        return new FormattedArenaTestCase(Utils.serializeToJson(data), dummyToRealNames);
    }

    private Map<String, Object> contestantFields(dev.jeval.LlmTestCase testCase) {
        var fields = new LinkedHashMap<String, Object>();
        if (evaluationParams.contains(SingleTurnParam.ACTUAL_OUTPUT)) {
            fields.put("actual_output", testCase.actualOutput());
        }
        if (evaluationParams.contains(SingleTurnParam.CONTEXT)) {
            fields.put("context", testCase.context());
        }
        if (evaluationParams.contains(SingleTurnParam.RETRIEVAL_CONTEXT)) {
            fields.put("retrieval_context", testCase.retrievalContext());
        }
        if (evaluationParams.contains(SingleTurnParam.TOOLS_CALLED)) {
            fields.put("tools_called", testCase.toolsCalled());
        }
        if (evaluationParams.contains(SingleTurnParam.EXPECTED_TOOLS)) {
            fields.put("expected_tools", testCase.expectedTools());
        }
        return fields;
    }

    private static String fakeName(int index) {
        return index < FAKE_NAMES.size() ? FAKE_NAMES.get(index) : "Contestant" + (index + 1);
    }

    private void requireModel() {
        if (model == null) {
            throw new UnsupportedOperationException("Arena GEval generation requires a model provider");
        }
    }

    private record FormattedArenaTestCase(String testCase, Map<String, String> dummyToRealNames) {
    }
}
