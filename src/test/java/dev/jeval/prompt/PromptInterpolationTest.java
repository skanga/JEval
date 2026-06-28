package dev.jeval.prompt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PromptInterpolationTest {

    @Test
    void interpolationTypeValuesMatchDeepEval() {
        assertEquals("MUSTACHE", PromptInterpolationType.MUSTACHE.value());
        assertEquals("MUSTACHE_WITH_SPACE", PromptInterpolationType.MUSTACHE_WITH_SPACE.value());
        assertEquals("FSTRING", PromptInterpolationType.FSTRING.value());
        assertEquals("DOLLAR_BRACKETS", PromptInterpolationType.DOLLAR_BRACKETS.value());
        assertEquals("JINJA", PromptInterpolationType.JINJA.value());
    }

    @Test
    void mustacheInterpolatesIdentifierPlaceholdersWithoutTouchingJson() {
        var result = PromptInterpolation.interpolateMustache(
                "{{name}} likes {\"key\": \"value\", \"count\": 42} and {{item123}}",
                Map.of("name", "Alice", "item123", 5));

        assertEquals("Alice likes {\"key\": \"value\", \"count\": 42} and 5", result);
    }

    @Test
    void mustacheRaisesWhenPlaceholderVariableIsMissing() {
        var error = assertThrows(IllegalArgumentException.class,
                () -> PromptInterpolation.interpolateMustache("Hello {{name}}", Map.of()));

        assertEquals("Missing variable in template: name", error.getMessage());
    }

    @Test
    void mustacheLeavesInvalidIdentifiersAndSpacedPlaceholdersUntouched() {
        var result = PromptInterpolation.interpolateMustache(
                "{{123invalid}} {{user-name}} {{ name }} {{name}}",
                Map.of("name", "Alice"));

        assertEquals("{{123invalid}} {{user-name}} {{ name }} Alice", result);
    }

    @Test
    void mustacheWithSpaceRequiresExactlyOneSpaceAroundIdentifier() {
        var result = PromptInterpolation.interpolateMustacheWithSpace(
                "{{ name }} likes {\"key\": \"value\"}; {{name}} and {{  name  }} stay",
                Map.of("name", "Alice"));

        assertEquals("Alice likes {\"key\": \"value\"}; {{name}} and {{  name  }} stay", result);
    }

    @Test
    void fstringInterpolatesIdentifierPlaceholdersWithoutTouchingJsonBraces() {
        var result = PromptInterpolation.interpolateFString(
                "{name} data: {\"key\": \"value\"} and {user_id}",
                Map.of("name", "Alice", "user_id", 123));

        assertEquals("Alice data: {\"key\": \"value\"} and 123", result);
    }

    @Test
    void dollarBracketsInterpolatesOnlyDollarBracePlaceholders() {
        var result = PromptInterpolation.interpolateDollarBrackets(
                "${HOME}/docs for ${name}; {name} stays",
                Map.of("HOME", "/home/user", "name", "Alice"));

        assertEquals("/home/user/docs for Alice; {name} stays", result);
    }

    @Test
    void jinjaInterpolatesWhitespaceWrappedVariablesAndMissingValuesAsEmptyStrings() {
        var result = PromptInterpolation.interpolateText(
                PromptInterpolationType.JINJA,
                "Hello {{ name }} from {{city}}. Missing: '{{ missing }}'.",
                Map.of("name", "Ada", "city", "London"));

        assertEquals("Hello Ada from London. Missing: ''.", result);
    }

    @Test
    void otherFormatsRaiseWhenPlaceholderVariableIsMissing() {
        assertEquals("Missing variable in template: name", assertThrows(IllegalArgumentException.class,
                () -> PromptInterpolation.interpolateMustacheWithSpace("Hello {{ name }}", Map.of())).getMessage());
        assertEquals("Missing variable in template: name", assertThrows(IllegalArgumentException.class,
                () -> PromptInterpolation.interpolateFString("Hello {name}", Map.of())).getMessage());
        assertEquals("Missing variable in template: name", assertThrows(IllegalArgumentException.class,
                () -> PromptInterpolation.interpolateDollarBrackets("Hello ${name}", Map.of())).getMessage());
    }

    @Test
    void interpolateTextDispatchesByInterpolationType() {
        assertEquals("Hello Alice", PromptInterpolation.interpolateText(
                PromptInterpolationType.MUSTACHE, "Hello {{name}}", Map.of("name", "Alice")));
        assertEquals("Hello Alice", PromptInterpolation.interpolateText(
                PromptInterpolationType.MUSTACHE_WITH_SPACE, "Hello {{ name }}", Map.of("name", "Alice")));
        assertEquals("Hello Alice", PromptInterpolation.interpolateText(
                PromptInterpolationType.FSTRING, "Hello {name}", Map.of("name", "Alice")));
        assertEquals("Hello Alice", PromptInterpolation.interpolateText(
                PromptInterpolationType.DOLLAR_BRACKETS, "Hello ${name}", Map.of("name", "Alice")));
        assertEquals("Hello Alice", PromptInterpolation.interpolateText(
                PromptInterpolationType.JINJA, "Hello {{ name }}", Map.of("name", "Alice")));
    }
}
