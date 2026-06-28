package dev.jeval.prompt;

import java.util.Map;
import java.util.regex.Pattern;

public final class PromptInterpolation {
    private static final Pattern MUSTACHE = Pattern.compile("\\{\\{([a-zA-Z_][a-zA-Z0-9_]*)\\}\\}");
    private static final Pattern MUSTACHE_WITH_SPACE = Pattern.compile("\\{\\{ ([a-zA-Z_][a-zA-Z0-9_]*) \\}\\}");
    private static final Pattern FSTRING = Pattern.compile("\\{([a-zA-Z_][a-zA-Z0-9_]*)\\}");
    private static final Pattern DOLLAR_BRACKETS = Pattern.compile("\\$\\{([a-zA-Z_][a-zA-Z0-9_]*)\\}");
    private static final Pattern JINJA = Pattern.compile("\\{\\{\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\}\\}");

    private PromptInterpolation() {
    }

    public static String interpolateMustache(String text, Map<String, ?> values) {
        return interpolate(MUSTACHE, text, values);
    }

    public static String interpolateMustacheWithSpace(String text, Map<String, ?> values) {
        return interpolate(MUSTACHE_WITH_SPACE, text, values);
    }

    public static String interpolateFString(String text, Map<String, ?> values) {
        return interpolate(FSTRING, text, values);
    }

    public static String interpolateDollarBrackets(String text, Map<String, ?> values) {
        return interpolate(DOLLAR_BRACKETS, text, values);
    }

    public static String interpolateJinja(String text, Map<String, ?> values) {
        return interpolateLenient(JINJA, text, values);
    }

    public static String interpolateText(PromptInterpolationType interpolationType, String text, Map<String, ?> values) {
        return switch (interpolationType) {
            case MUSTACHE -> interpolateMustache(text, values);
            case MUSTACHE_WITH_SPACE -> interpolateMustacheWithSpace(text, values);
            case FSTRING -> interpolateFString(text, values);
            case DOLLAR_BRACKETS -> interpolateDollarBrackets(text, values);
            case JINJA -> interpolateJinja(text, values);
        };
    }

    private static String interpolate(Pattern pattern, String text, Map<String, ?> values) {
        var matcher = pattern.matcher(text);
        var result = new StringBuilder();
        while (matcher.find()) {
            var name = matcher.group(1);
            if (!values.containsKey(name)) {
                throw new IllegalArgumentException("Missing variable in template: " + name);
            }
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(String.valueOf(values.get(name))));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String interpolateLenient(Pattern pattern, String text, Map<String, ?> values) {
        var matcher = pattern.matcher(text);
        var result = new StringBuilder();
        while (matcher.find()) {
            var value = values.get(matcher.group(1));
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(value == null ? "" : String.valueOf(value)));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
