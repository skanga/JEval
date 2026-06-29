package dev.jeval.benchmarks;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jeval.EvaluationModel;
import dev.jeval.Golden;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class IFEval {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern SENTENCES = Pattern.compile("[.!?]+");
    private static final Pattern BULLET = Pattern.compile("^\\s*(?:[-*\\u2022]\\s+|\\d+\\.\\s+).*");
    private static final Pattern HIGHLIGHT = Pattern.compile("\\*[^*]+\\*");
    private static final Pattern TITLE = Pattern.compile("<<[^>]+>>");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\[[^\\]]+]");

    private final List<Golden> goldens;
    private List<IFEvalPrediction> predictions;
    private Map<String, Double> instructionBreakdown;
    private Double overallScore;

    public IFEval(List<Golden> goldens) {
        this(goldens, null);
    }

    public IFEval(List<Golden> goldens, Integer nProblems) {
        if (goldens == null || goldens.isEmpty()) {
            throw new IllegalArgumentException("'goldens' must not be empty");
        }
        if (nProblems != null && nProblems < 1) {
            throw new IllegalArgumentException("'nProblems' must be positive");
        }
        this.goldens = nProblems == null || nProblems >= goldens.size()
                ? List.copyOf(goldens)
                : List.copyOf(goldens.subList(0, nProblems));
    }

    public BenchmarkResult evaluate(EvaluationModel model) {
        if (model == null) {
            throw new IllegalArgumentException("'model' must not be null");
        }
        var rows = new ArrayList<IFEvalPrediction>();
        var totals = new LinkedHashMap<String, int[]>();
        var correct = 0;
        for (var golden : goldens) {
            var prediction = model.generate(golden.input());
            var instructionScores = instructionScores(prediction, golden.metadata());
            var allPassed = instructionScores.values().stream().allMatch(Boolean::booleanValue);
            if (allPassed) {
                correct++;
            }
            for (var entry : instructionScores.entrySet()) {
                var count = totals.computeIfAbsent(entry.getKey(), ignored -> new int[2]);
                count[1]++;
                if (entry.getValue()) {
                    count[0]++;
                }
            }
            rows.add(new IFEvalPrediction(golden.input(), prediction, allPassed, Map.copyOf(instructionScores)));
        }
        var breakdown = new LinkedHashMap<String, Double>();
        for (var entry : totals.entrySet()) {
            breakdown.put(entry.getKey(), (double) entry.getValue()[0] / entry.getValue()[1]);
        }
        predictions = List.copyOf(rows);
        instructionBreakdown = Map.copyOf(breakdown);
        overallScore = (double) correct / goldens.size();
        return new BenchmarkResult(overallScore);
    }

    public List<IFEvalPrediction> predictions() {
        return predictions == null ? null : List.copyOf(predictions);
    }

    public Map<String, Double> instructionBreakdown() {
        return instructionBreakdown == null ? null : Map.copyOf(instructionBreakdown);
    }

    public Double overallScore() {
        return overallScore;
    }

    public static boolean verifyInstruction(String response, String instructionId, Map<String, ?> kwargs) {
        var args = kwargs == null ? Map.<String, Object>of() : kwargs;
        return switch (category(instructionId)) {
            case "punctuation" -> punctuation(response, instructionId);
            case "length_constraints" -> length(response, instructionId, args);
            case "detectable_format" -> format(response, instructionId, args);
            case "detectable_content" -> content(response, instructionId, args);
            case "structural_constraints" -> structural(response, instructionId, args);
            case "combination" -> contains(response, text(args, "prompt_to_repeat", null));
            case "change_case" -> caseConstraint(response, instructionId);
            case "startend" -> startEnd(response, instructionId, args);
            case "keywords" -> keywords(response, instructionId, args);
            default -> false;
        };
    }

    private static Map<String, Boolean> instructionScores(String prediction, Map<String, Object> metadata) {
        var ids = strings(metadata == null ? null : metadata.get("instruction_ids"));
        var kwargs = list(metadata == null ? null : metadata.get("kwargs_list"));
        var scores = new LinkedHashMap<String, Boolean>();
        for (var i = 0; i < ids.size(); i++) {
            scores.put(ids.get(i), verifyInstruction(prediction, ids.get(i), map(i < kwargs.size() ? kwargs.get(i) : null)));
        }
        return scores;
    }

    private static String category(String instructionId) {
        var split = instructionId == null ? -1 : instructionId.indexOf(':');
        return split < 0 ? "" : instructionId.substring(0, split);
    }

    private static boolean punctuation(String response, String id) {
        return switch (id) {
            case "punctuation:no_comma" -> !response.contains(",");
            case "punctuation:no_period" -> !response.contains(".");
            case "punctuation:no_question_mark" -> !response.contains("?");
            case "punctuation:no_exclamation_mark" -> !response.contains("!");
            default -> true;
        };
    }

    private static boolean length(String response, String id, Map<String, ?> args) {
        return switch (id) {
            case "length_constraints:number_words" ->
                    !args.containsKey("num_words") || compare(words(response), number(args, "num_words"), relation(args));
            case "length_constraints:number_characters" ->
                    !args.containsKey("num_chars") || compare(response.length(), number(args, "num_chars"), relation(args));
            case "length_constraints:number_sentences" ->
                    !args.containsKey("num_sentences") || compare((int) SENTENCES.splitAsStream(response).filter(s -> !s.strip().isEmpty()).count(),
                            number(args, "num_sentences"), relation(args));
            default -> true;
        };
    }

    private static boolean format(String response, String id, Map<String, ?> args) {
        return switch (id) {
            case "detectable_format:json" -> validJson(response);
            case "detectable_format:list" -> response.strip().startsWith("-")
                    || response.strip().startsWith("*")
                    || response.strip().startsWith("1.")
                    || response.strip().startsWith("\u2022")
                    || response.strip().contains("\n");
            case "detectable_format:number_bullets" ->
                    response.lines().filter(line -> BULLET.matcher(line).matches()).count() >= number(args, "num_bullets");
            case "detectable_format:number_highlighted_sections" ->
                    HIGHLIGHT.matcher(response).results().count() >= number(args, "num_highlights");
            case "detectable_format:title" -> TITLE.matcher(response).find();
            default -> true;
        };
    }

    private static boolean content(String response, String id, Map<String, ?> args) {
        return switch (id) {
            case "detectable_content:keyword_frequency" ->
                    !args.containsKey("keyword") || !args.containsKey("frequency")
                            || compare(count(response, text(args, "keyword", "")), number(args, "frequency"), relation(args));
            case "detectable_content:forbidden_words" ->
                    strings(args.get("forbidden_words")).stream().noneMatch(word -> contains(response, word));
            case "detectable_content:number_placeholders" ->
                    PLACEHOLDER.matcher(response).results().count() >= number(args, "num_placeholders");
            case "detectable_content:postscript" -> response.contains(text(args, "postscript_marker", "P.S."));
            case "detectable_content:first_word" -> firstWord(response).equalsIgnoreCase(text(args, "first_word", ""));
            default -> true;
        };
    }

    private static boolean structural(String response, String id, Map<String, ?> args) {
        return switch (id) {
            case "structural_constraints:number_paragraphs" ->
                    !args.containsKey("num_paragraphs") || compare((int) Pattern.compile("\\n\\n").splitAsStream(response).filter(s -> !s.strip().isEmpty()).count(),
                            number(args, "num_paragraphs"), relation(args));
            case "structural_constraints:number_sections" -> {
                var splitter = text(args, "section_spliter", "---");
                yield Pattern.compile(Pattern.quote(splitter)).splitAsStream(response).filter(s -> !s.strip().isEmpty()).count()
                        >= number(args, "num_sections");
            }
            default -> true;
        };
    }

    private static boolean caseConstraint(String response, String id) {
        return switch (id) {
            case "change_case:english_lowercase" -> response.equals(response.toLowerCase(Locale.ROOT))
                    && response.chars().anyMatch(Character::isLowerCase);
            case "change_case:english_uppercase" -> response.equals(response.toUpperCase(Locale.ROOT))
                    && response.chars().anyMatch(Character::isUpperCase);
            case "change_case:english_titlecase" -> titleCase(response);
            default -> true;
        };
    }

    private static boolean startEnd(String response, String id, Map<String, ?> args) {
        return switch (id) {
            case "startend:start_with" -> response.strip().startsWith(text(args, "start_text", ""));
            case "startend:end_with" -> response.strip().endsWith(text(args, "end_text", ""));
            default -> true;
        };
    }

    private static boolean keywords(String response, String id, Map<String, ?> args) {
        var keywords = strings(args.get("keywords"));
        return switch (id) {
            case "keywords:must_include" -> keywords.stream().allMatch(keyword -> contains(response, keyword));
            case "keywords:must_not_include" -> keywords.stream().noneMatch(keyword -> contains(response, keyword));
            default -> true;
        };
    }

    private static boolean compare(int actual, int expected, String relation) {
        return switch (relation) {
            case "at least" -> actual >= expected;
            case "less than" -> actual < expected;
            case "more than" -> actual > expected;
            default -> actual == expected;
        };
    }

    private static boolean validJson(String response) {
        try {
            JSON.readTree(response);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean contains(String response, String value) {
        return value == null || response.toLowerCase(Locale.ROOT).contains(value.toLowerCase(Locale.ROOT));
    }

    private static int count(String response, String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        var total = 0;
        var from = 0;
        var source = response.toLowerCase(Locale.ROOT);
        var needle = value.toLowerCase(Locale.ROOT);
        while ((from = source.indexOf(needle, from)) >= 0) {
            total++;
            from += needle.length();
        }
        return total;
    }

    private static int words(String response) {
        var stripped = response.strip();
        return stripped.isEmpty() ? 0 : stripped.split("\\s+").length;
    }

    private static String firstWord(String response) {
        var stripped = response.strip();
        return stripped.isEmpty() ? "" : stripped.split("\\s+")[0];
    }

    private static boolean titleCase(String response) {
        for (var word : response.strip().split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!Character.isUpperCase(word.charAt(0))) {
                return false;
            }
        }
        return !response.strip().isEmpty();
    }

    private static int number(Map<String, ?> args, String key) {
        var value = args.get(key);
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static String relation(Map<String, ?> args) {
        return text(args, "relation", "exactly");
    }

    private static String text(Map<String, ?> args, String key, String fallback) {
        var value = args.get(key);
        return value == null ? fallback : value.toString();
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream().map(Object::toString).toList();
    }

    private static List<?> list(Object value) {
        return value instanceof List<?> values ? values : List.of();
    }

    private static Map<String, ?> map(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return Map.of();
        }
        var copied = new LinkedHashMap<String, Object>();
        for (var entry : source.entrySet()) {
            copied.put(entry.getKey().toString(), entry.getValue());
        }
        return copied;
    }

    public record IFEvalPrediction(String input, String prediction, boolean allInstructionsCorrect,
                                   Map<String, Boolean> instructionScores) {
    }
}
