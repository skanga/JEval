package dev.jeval.metrics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import dev.jeval.ConversationalTestCase;
import dev.jeval.ArenaTestCase;
import dev.jeval.LlmTestCase;
import dev.jeval.MissingTestCaseParamsException;
import dev.jeval.MultiTurnParam;
import dev.jeval.SingleTurnParam;
import dev.jeval.Turn;
import dev.jeval.ToolCall;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MetricUtils {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DefaultPrettyPrinter PYTHON_INDENT_PRETTY_PRINTER = pythonIndentPrettyPrinter();
    private static final String INVALID_JSON_MESSAGE =
            "Evaluation LLM outputted an invalid JSON. Please use a better evaluation model.";

    private MetricUtils() {
    }

    public static void checkLlmTestCaseParams(
            LlmTestCase testCase,
            List<SingleTurnParam> testCaseParams,
            String metricName) {
        if (testCaseParams.contains(SingleTurnParam.ACTUAL_OUTPUT) && "".equals(testCase.actualOutput())) {
            throw new MissingTestCaseParamsException(
                    "'actual_output' cannot be empty for the '" + metricName + "' metric");
        }
        var missingParams = new ArrayList<String>();
        for (var param : testCaseParams) {
            if (singleTurnValue(testCase, param) == null) {
                missingParams.add("'" + param.value() + "'");
            }
        }
        if (!missingParams.isEmpty()) {
            throw new MissingTestCaseParamsException(
                    joinMissingParams(missingParams) + " cannot be None for the '" + metricName + "' metric");
        }
    }

    public static void checkArenaTestCaseParams(
            ArenaTestCase arenaTestCase,
            List<SingleTurnParam> testCaseParams,
            String metricName) {
        for (var contestant : arenaTestCase.contestants()) {
            checkLlmTestCaseParams(contestant.testCase(), testCaseParams, metricName);
        }
    }

    public static List<Map<String, Object>> formatTurns(
            List<LlmTestCase> testCases,
            List<SingleTurnParam> testCaseParams) {
        var turns = new ArrayList<Map<String, Object>>();
        for (var testCase : testCases) {
            var fields = new LinkedHashMap<String, Object>();
            for (var param : testCaseParams) {
                var value = switch (param) {
                    case INPUT -> testCase.input();
                    case ACTUAL_OUTPUT -> testCase.actualOutput();
                    case EXPECTED_OUTPUT -> testCase.expectedOutput();
                    case CONTEXT -> testCase.context();
                    case RETRIEVAL_CONTEXT -> testCase.retrievalContext();
                    case METADATA -> testCase.metadata();
                    case TAGS -> testCase.tags();
                    case TOOLS_CALLED -> testCase.toolsCalled();
                    case EXPECTED_TOOLS -> testCase.expectedTools();
                    case MCP_SERVERS -> testCase.mcpServers();
                    case MCP_TOOLS_CALLED -> testCase.mcpToolsCalled();
                    case MCP_RESOURCES_CALLED -> testCase.mcpResourcesCalled();
                    case MCP_PROMPTS_CALLED -> testCase.mcpPromptsCalled();
                };
                if (!isEmpty(value)) {
                    fields.put(param.value(), value);
                }
            }
            turns.add(fields);
        }
        return turns;
    }

    public static void checkConversationalTestCaseParams(
            ConversationalTestCase testCase,
            List<MultiTurnParam> testCaseParams,
            String metricName) {
        checkConversationalTestCaseParams(testCase, testCaseParams, metricName, false);
    }

    public static void checkConversationalTestCaseParams(
            ConversationalTestCase testCase,
            List<MultiTurnParam> testCaseParams,
            String metricName,
            boolean requireChatbotRole) {
        if (testCaseParams.contains(MultiTurnParam.EXPECTED_OUTCOME) && testCase.expectedOutcome() == null) {
            throw new MissingTestCaseParamsException(
                    "'expected_outcome' in a conversational test case cannot be empty for the '" + metricName + "' metric.");
        }
        if (testCaseParams.contains(MultiTurnParam.SCENARIO) && testCase.scenario() == null) {
            throw new MissingTestCaseParamsException(
                    "'scenario' in a conversational test case cannot be empty for the '" + metricName + "' metric.");
        }
        if (testCaseParams.contains(MultiTurnParam.METADATA) && testCase.metadata() == null) {
            throw new MissingTestCaseParamsException(
                    "'metadata' in a conversational test case cannot be empty for the '" + metricName + "' metric.");
        }
        if (testCaseParams.contains(MultiTurnParam.TAGS) && testCase.tags() == null) {
            throw new MissingTestCaseParamsException(
                    "'tags' in a conversational test case cannot be empty for the '" + metricName + "' metric.");
        }
        if (requireChatbotRole && testCase.chatbotRole() == null) {
            throw new MissingTestCaseParamsException(
                    "'chatbot_role' in a conversational test case cannot be empty for the '" + metricName + "' metric.");
        }
    }

    public static Map<String, Object> convertTurnToDict(Turn turn) {
        return convertTurnToDict(turn, List.of(MultiTurnParam.CONTENT, MultiTurnParam.ROLE));
    }

    public static Map<String, Object> convertTurnToDict(Turn turn, List<MultiTurnParam> turnParams) {
        var result = new LinkedHashMap<String, Object>();
        for (var param : turnParams) {
            var value = switch (param) {
                case CONTENT -> turn.content();
                case ROLE -> turn.role();
                case RETRIEVAL_CONTEXT -> turn.retrievalContext();
                case TOOLS_CALLED -> turn.toolsCalled();
                case MCP_TOOLS -> turn.mcpToolsCalled();
                case MCP_RESOURCES -> turn.mcpResourcesCalled();
                case MCP_PROMPTS -> turn.mcpPromptsCalled();
                default -> null;
            };
            if (value != null) {
                result.put(param.value(), value);
            }
        }
        return result;
    }

    public static List<List<Turn>> getTurnsInSlidingWindow(List<Turn> turns, int windowSize) {
        var windows = new ArrayList<List<Turn>>();
        for (var i = 0; i < turns.size(); i++) {
            windows.add(List.copyOf(turns.subList(Math.max(0, i - windowSize + 1), i + 1)));
        }
        return windows;
    }

    public static List<List<Turn>> getUnitInteractions(List<Turn> turns) {
        var units = new ArrayList<List<Turn>>();
        var current = new ArrayList<Turn>();
        var hasUser = false;
        for (var turn : turns) {
            if (!current.isEmpty()
                    && "assistant".equals(current.getLast().role())
                    && "user".equals(turn.role())
                    && hasUser) {
                units.add(List.copyOf(current));
                current = new ArrayList<>();
                current.add(turn);
                hasUser = true;
                continue;
            }
            current.add(turn);
            if ("user".equals(turn.role())) {
                hasUser = true;
            }
        }
        if (current.size() > 1 && "assistant".equals(current.getLast().role()) && hasUser) {
            units.add(List.copyOf(current));
        }
        return units;
    }

    public static String printToolsCalled(List<ToolCall> toolsCalledList) {
        if (toolsCalledList == null || toolsCalledList.isEmpty()) {
            return "";
        }
        var text = new StringBuilder("[\n");
        for (var i = 0; i < toolsCalledList.size(); i++) {
            try {
                var json = MAPPER.writer(PYTHON_INDENT_PRETTY_PRINTER)
                        .writeValueAsString(toolsCalledList.get(i).modelDump(false));
                text.append(json.lines()
                        .map(line -> "  " + line)
                        .collect(java.util.stream.Collectors.joining("\n")));
            } catch (JsonProcessingException error) {
                throw new IllegalArgumentException("Unable to serialize tool call.", error);
            }
            text.append(i < toolsCalledList.size() - 1 ? ",\n" : "\n");
        }
        return text.append("]").toString();
    }

    private static DefaultPrettyPrinter pythonIndentPrettyPrinter() {
        var printer = new PythonIndentPrettyPrinter();
        var indenter = new DefaultIndenter("    ", "\n");
        printer.indentObjectsWith(indenter);
        printer.indentArraysWith(indenter);
        return printer;
    }

    private static final class PythonIndentPrettyPrinter extends DefaultPrettyPrinter {
        private PythonIndentPrettyPrinter() {
        }

        private PythonIndentPrettyPrinter(PythonIndentPrettyPrinter base) {
            super(base);
        }

        @Override
        public DefaultPrettyPrinter createInstance() {
            return new PythonIndentPrettyPrinter(this);
        }

        @Override
        public void writeObjectFieldValueSeparator(JsonGenerator generator) throws IOException {
            generator.writeRaw(": ");
        }
    }

    public static JsonNode trimAndLoadJson(String input) {
        if (input == null) {
            throw new IllegalArgumentException(INVALID_JSON_MESSAGE);
        }
        var start = input.indexOf('{');
        var end = input.lastIndexOf('}') + 1;
        if (end == 0 && start != -1) {
            input = input + "}";
            end = input.length();
        }
        var json = start != -1 && end != 0 ? input.substring(start, end) : "";
        if (json.isEmpty()) {
            throw new IllegalArgumentException(INVALID_JSON_MESSAGE);
        }
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException ignored) {
            try {
                return MAPPER.readTree(json.replaceAll(",\\s*([\\]}])", "$1"));
            } catch (JsonProcessingException ignoredAgain) {
                throw new IllegalArgumentException(INVALID_JSON_MESSAGE);
            }
        }
    }

    public static JsonNode required(JsonNode node, String field) {
        var value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            throw new IllegalArgumentException("Missing required schema field: " + field);
        }
        return value;
    }

    public static String requiredText(JsonNode node, String field) {
        var value = required(node, field);
        if (!value.isTextual()) {
            throw new IllegalArgumentException("Schema field must be a string: " + field);
        }
        return value.asText();
    }

    public static String optionalText(JsonNode node, String field) {
        var value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw new IllegalArgumentException("Schema field must be a string: " + field);
        }
        return value.asText();
    }

    public static double requiredDouble(JsonNode node, String field) {
        var value = required(node, field);
        if (value.isNumber()) {
            return finiteDouble(value.asDouble(), field);
        }
        if (value.isTextual()) {
            try {
                return finiteDouble(Double.parseDouble(value.asText()), field);
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException("Schema field must be a number: " + field, error);
            }
        }
        throw new IllegalArgumentException("Schema field must be a number: " + field);
    }

    public static List<Double> requiredDoubleList(JsonNode node, String field) {
        var value = required(node, field);
        if (!value.isArray()) {
            throw new IllegalArgumentException("Schema field must be a number list: " + field);
        }
        var values = new ArrayList<Double>();
        for (var item : value) {
            if (item.isNumber()) {
                values.add(finiteDouble(item.asDouble(), field));
            } else if (item.isTextual()) {
                try {
                    values.add(finiteDouble(Double.parseDouble(item.asText()), field));
                } catch (NumberFormatException error) {
                    throw new IllegalArgumentException("Schema list values must be numbers: " + field, error);
                }
            } else {
                throw new IllegalArgumentException("Schema list values must be numbers: " + field);
            }
        }
        return List.copyOf(values);
    }

    private static double finiteDouble(double value, String field) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Schema field must be a finite number: " + field);
        }
        return value;
    }

    public static List<String> requiredStringList(JsonNode node, String field) {
        var value = required(node, field);
        if (!value.isArray()) {
            throw new IllegalArgumentException("Schema field must be a string list: " + field);
        }
        var values = new ArrayList<String>();
        for (var item : value) {
            if (!item.isTextual()) {
                throw new IllegalArgumentException("Schema list values must be strings: " + field);
            }
            values.add(item.asText());
        }
        return List.copyOf(values);
    }

    private static boolean isEmpty(Object value) {
        return value == null
                || value instanceof String text && text.isEmpty()
                || value instanceof List<?> list && list.isEmpty()
                || value instanceof Map<?, ?> map && map.isEmpty();
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
            case MCP_SERVERS -> testCase.mcpServers();
            case MCP_TOOLS_CALLED -> testCase.mcpToolsCalled();
            case MCP_RESOURCES_CALLED -> testCase.mcpResourcesCalled();
            case MCP_PROMPTS_CALLED -> testCase.mcpPromptsCalled();
        };
    }

    private static String joinMissingParams(List<String> missingParams) {
        if (missingParams.size() == 1) {
            return missingParams.getFirst();
        }
        if (missingParams.size() == 2) {
            return String.join(" and ", missingParams);
        }
        return String.join(", ", missingParams.subList(0, missingParams.size() - 1))
                + ", and "
                + missingParams.getLast();
    }
}
