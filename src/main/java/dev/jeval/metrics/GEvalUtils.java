package dev.jeval.metrics;

import dev.jeval.ConversationalTestCase;
import dev.jeval.LlmTestCase;
import dev.jeval.MultiTurnParam;
import dev.jeval.SingleTurnParam;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GEvalUtils {
    private static final Map<SingleTurnParam, String> G_EVAL_PARAMS = Map.of(
            SingleTurnParam.INPUT, "Input",
            SingleTurnParam.ACTUAL_OUTPUT, "Actual Output",
            SingleTurnParam.EXPECTED_OUTPUT, "Expected Output",
            SingleTurnParam.CONTEXT, "Context",
            SingleTurnParam.RETRIEVAL_CONTEXT, "Retrieval Context",
            SingleTurnParam.METADATA, "Metadata",
            SingleTurnParam.TAGS, "Tags",
            SingleTurnParam.EXPECTED_TOOLS, "Expected Tools",
            SingleTurnParam.TOOLS_CALLED, "Tools Called");

    private static final Map<MultiTurnParam, String> CONVERSATIONAL_G_EVAL_PARAMS = Map.of(
            MultiTurnParam.CONTENT, "Content",
            MultiTurnParam.ROLE, "Role",
            MultiTurnParam.METADATA, "Metadata",
            MultiTurnParam.TAGS, "Tags",
            MultiTurnParam.TOOLS_CALLED, "Tools Called",
            MultiTurnParam.RETRIEVAL_CONTEXT, "Retrieval Context",
            MultiTurnParam.EXPECTED_OUTCOME, "Expected Outcome",
            MultiTurnParam.SCENARIO, "Scenario");

    private static final Map<SingleTurnParam, String> G_EVAL_API_PARAMS = Map.of(
            SingleTurnParam.INPUT, "input",
            SingleTurnParam.ACTUAL_OUTPUT, "actualOutput",
            SingleTurnParam.EXPECTED_OUTPUT, "expectedOutput",
            SingleTurnParam.CONTEXT, "context",
            SingleTurnParam.RETRIEVAL_CONTEXT, "retrievalContext",
            SingleTurnParam.METADATA, "metadata",
            SingleTurnParam.TAGS, "tags",
            SingleTurnParam.EXPECTED_TOOLS, "expectedTools",
            SingleTurnParam.TOOLS_CALLED, "toolsCalled");

    private static final Map<MultiTurnParam, String> CONVERSATIONAL_G_EVAL_API_PARAMS = Map.of(
            MultiTurnParam.ROLE, "role",
            MultiTurnParam.CONTENT, "content",
            MultiTurnParam.METADATA, "metadata",
            MultiTurnParam.TAGS, "tags",
            MultiTurnParam.SCENARIO, "scenario",
            MultiTurnParam.EXPECTED_OUTCOME, "expectedOutcome",
            MultiTurnParam.RETRIEVAL_CONTEXT, "retrievalContext",
            MultiTurnParam.TOOLS_CALLED, "toolsCalled");

    private GEvalUtils() {
    }

    public record Rubric(int start, int end, String expectedOutcome) {
        public Rubric {
            if (start < 0 || start > 10 || end < 0 || end > 10) {
                throw new IllegalArgumentException(
                        "Both Rubric's 'score_range' values must be between 0 and 10 inclusive.");
            }
            if (start > end) {
                throw new IllegalArgumentException(
                        "Rubric's 'score_range' start must be less than or equal to end.");
            }
        }
    }

    public record ApiRubric(List<Double> scoreRange, String expectedOutcome) {
        public ApiRubric {
            if (scoreRange == null || scoreRange.size() != 2
                    || scoreRange.stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
                throw new IllegalArgumentException("ApiRubric scoreRange must contain two finite values.");
            }
            scoreRange = List.copyOf(scoreRange);
        }
    }

    public record MetricPullResponse(
            String id,
            String criteria,
            List<String> evaluationSteps,
            List<String> requiredParameters,
            List<ApiRubric> rubric) {
        public MetricPullResponse {
            evaluationSteps = evaluationSteps == null ? null : List.copyOf(evaluationSteps);
            requiredParameters = requiredParameters == null ? List.of() : List.copyOf(requiredParameters);
            rubric = rubric == null ? null : List.copyOf(rubric);
        }
    }

    public static String constructTestCaseString(List<SingleTurnParam> evaluationParams, LlmTestCase testCase) {
        var text = new StringBuilder();
        for (var param : evaluationParams) {
            text.append(G_EVAL_PARAMS.get(param))
                    .append(":\n")
                    .append(singleTurnValue(testCase, param))
                    .append(" \n\n");
        }
        return text.toString();
    }

    public static String constructNonTurnsTestCaseString(
            List<MultiTurnParam> turnParams,
            ConversationalTestCase testCase) {
        var body = new StringBuilder();
        for (var param : turnParams) {
            if (param == MultiTurnParam.RETRIEVAL_CONTEXT
                    || param == MultiTurnParam.TOOLS_CALLED
                    || param == MultiTurnParam.CONTENT
                    || param == MultiTurnParam.ROLE) {
                continue;
            }
            body.append(CONVERSATIONAL_G_EVAL_PARAMS.get(param))
                    .append(":\n")
                    .append(conversationalValue(testCase, param))
                    .append(" \n\n");
        }
        return body.isEmpty() ? "" : "Conversation-level fields:\n" + body;
    }

    public static Map<String, Object> constructUploadPayload(
            String name,
            List<SingleTurnParam> evaluationParams,
            String criteria) {
        return constructUploadPayload(name, evaluationParams, criteria, null, null);
    }

    public static Map<String, Object> constructUploadPayload(
            String name,
            List<SingleTurnParam> evaluationParams,
            String criteria,
            List<String> evaluationSteps,
            List<Rubric> rubric) {
        return constructPayload(
                name,
                apiParams(evaluationParams, G_EVAL_API_PARAMS, "Unsupported evaluation params for GEval upload: "),
                criteria,
                evaluationSteps,
                false,
                rubric);
    }

    public static Map<String, Object> constructConversationalUploadPayload(
            String name,
            List<MultiTurnParam> evaluationParams,
            String criteria) {
        return constructPayload(
                name,
                apiParams(evaluationParams, CONVERSATIONAL_G_EVAL_API_PARAMS, "Unsupported evaluation params for GEval upload: "),
                criteria,
                null,
                true,
                null);
    }

    public static void ensureRequiredParams(
            List<?> evaluationParams,
            String criteria,
            List<String> evaluationSteps,
            String operation) {
        if (evaluationParams == null || evaluationParams.isEmpty()) {
            throw new IllegalArgumentException(
                    "GEval requires evaluation_params. Provide them at initialization or call pull() before "
                            + operation
                            + ".");
        }
        validateCriteriaAndEvaluationSteps(criteria, evaluationSteps);
    }

    public static void validateCriteriaAndEvaluationSteps(String criteria, List<String> evaluationSteps) {
        if (criteria == null && evaluationSteps == null) {
            throw new IllegalArgumentException("Either 'criteria' or 'evaluation_steps' must be provided.");
        }
        if (criteria != null && criteria.isBlank()) {
            throw new IllegalArgumentException("Criteria provided cannot be an empty string.");
        }
        if (evaluationSteps != null && evaluationSteps.isEmpty()) {
            throw new IllegalArgumentException(
                    "'evaluation_steps' must not be an empty list. Either omit evaluation steps or include a non-empty list of steps.");
        }
    }

    public static List<?> constructPullEvaluationParams(List<String> requiredParameters, boolean multiTurn) {
        if (requiredParameters == null || requiredParameters.isEmpty()) {
            throw new IllegalArgumentException("This metric has no evaluation parameters and cannot be pulled.");
        }
        var reverseParams = new LinkedHashMap<String, Object>();
        var source = multiTurn ? CONVERSATIONAL_G_EVAL_API_PARAMS : G_EVAL_API_PARAMS;
        source.forEach((key, value) -> reverseParams.put(value, key));
        var unsupported = requiredParameters.stream().filter(param -> !reverseParams.containsKey(param)).toList();
        if (!unsupported.isEmpty()) {
            throw new IllegalArgumentException(
                    "Unsupported evaluation params encountered while pulling metric: "
                            + String.join(", ", unsupported)
                            + ".");
        }
        return requiredParameters.stream().map(reverseParams::get).toList();
    }

    public static String constructParamsString(List<SingleTurnParam> params) {
        return joinLabels(params.stream().map(G_EVAL_PARAMS::get).toList());
    }

    public static String constructConversationalTurnParamsString(List<MultiTurnParam> params) {
        return joinLabels(params.stream().map(CONVERSATIONAL_G_EVAL_PARAMS::get).toList());
    }

    public static List<Rubric> validateAndSortRubrics(List<Rubric> rubrics) {
        if (rubrics == null || rubrics.isEmpty()) {
            return null;
        }
        var sorted = rubrics.stream().sorted(Comparator.comparingInt(Rubric::start)).toList();
        for (var i = 0; i < sorted.size(); i++) {
            var current = sorted.get(i);
            for (var j = i + 1; j < sorted.size(); j++) {
                var next = sorted.get(j);
                if (current.end() >= next.start()) {
                    throw new IllegalArgumentException(
                            "Overlapping score ranges: ["
                                    + current.start()
                                    + ", "
                                    + current.end()
                                    + "] and ["
                                    + next.start()
                                    + ", "
                                    + next.end()
                                    + "]");
                }
            }
        }
        return sorted;
    }

    public static String formatRubrics(List<Rubric> rubrics) {
        if (rubrics == null) {
            return null;
        }
        return String.join("\n", rubrics.stream()
                .map(rubric -> rubric.start() == rubric.end()
                        ? rubric.start() + ": " + rubric.expectedOutcome()
                        : rubric.start() + "-" + rubric.end() + ": " + rubric.expectedOutcome())
                .toList());
    }

    public static String numberEvaluationSteps(List<String> evaluationSteps) {
        var text = new StringBuilder();
        for (var i = 0; i < evaluationSteps.size(); i++) {
            text.append(i + 1).append(". ").append(evaluationSteps.get(i)).append("\n");
        }
        return text.toString();
    }

    public static String numberTestCaseContents(List<String> testCaseContents) {
        var text = new StringBuilder();
        for (var i = 0; i < testCaseContents.size(); i++) {
            text.append(i).append(". ").append(testCaseContents.get(i)).append("\n");
        }
        return text.toString();
    }

    public static List<Integer> getScoreRange(List<Rubric> rubrics) {
        if (rubrics == null) {
            return List.of(0, 10);
        }
        return List.of(rubrics.getFirst().start(), rubrics.getLast().end());
    }

    private static Map<String, Object> constructPayload(
            String name,
            List<String> evaluationParams,
            String criteria,
            List<String> evaluationSteps,
            boolean multiTurn,
            List<Rubric> rubric) {
        if (evaluationParams.isEmpty()) {
            throw new IllegalArgumentException("GEval requires at least one evaluation parameter.");
        }
        var payload = new LinkedHashMap<String, Object>();
        payload.put("name", name);
        payload.put("evaluationParams", evaluationParams);
        payload.put("multiTurn", multiTurn);
        if (criteria != null) {
            payload.put("criteria", criteria);
        } else {
            payload.put("evaluationSteps", evaluationSteps);
        }
        if (rubric != null) {
            payload.put("rubric", rubric.stream()
                    .map(item -> Map.of(
                            "scoreRange", List.of(item.start(), item.end()),
                            "expectedOutcome", item.expectedOutcome()))
                    .toList());
        }
        return payload;
    }

    private static <T extends Enum<T>> List<String> apiParams(
            List<T> evaluationParams,
            Map<T, String> supportedParams,
            String messagePrefix) {
        if (evaluationParams == null) {
            throw new IllegalArgumentException("GEval requires at least one evaluation parameter.");
        }
        if (evaluationParams.isEmpty()) {
            return List.of();
        }
        var unsupported = evaluationParams.stream()
                .filter(param -> !supportedParams.containsKey(param))
                .map(Enum::name)
                .toList();
        if (!unsupported.isEmpty()) {
            throw new IllegalArgumentException(messagePrefix + String.join(", ", unsupported));
        }
        return evaluationParams.stream().map(supportedParams::get).toList();
    }

    private static String joinLabels(List<String> labels) {
        if (labels.size() == 1) {
            return labels.getFirst();
        }
        if (labels.size() == 2) {
            return String.join(" and ", labels);
        }
        return String.join(", ", labels.subList(0, labels.size() - 1)) + ", and " + labels.getLast();
    }

    private static Object singleTurnValue(LlmTestCase testCase, SingleTurnParam param) {
        return switch (param) {
            case INPUT -> testCase.input();
            case ACTUAL_OUTPUT -> testCase.actualOutput();
            case EXPECTED_OUTPUT -> testCase.expectedOutput();
            case CONTEXT -> testCase.context();
            case RETRIEVAL_CONTEXT -> testCase.retrievalContext();
            case METADATA -> testCase.metadata();
            case TAGS -> testCase.tags();
            case TOOLS_CALLED -> testCase.toolsCalled();
            case EXPECTED_TOOLS -> testCase.expectedTools();
            default -> null;
        };
    }

    private static Object conversationalValue(ConversationalTestCase testCase, MultiTurnParam param) {
        return switch (param) {
            case METADATA -> testCase.metadata();
            case TAGS -> testCase.tags();
            case EXPECTED_OUTCOME -> testCase.expectedOutcome();
            case SCENARIO -> testCase.scenario();
            default -> null;
        };
    }
}
