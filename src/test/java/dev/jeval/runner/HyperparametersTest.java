package dev.jeval.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.jeval.prompt.OutputType;
import dev.jeval.prompt.Prompt;
import dev.jeval.prompt.PromptInterpolationType;
import dev.jeval.prompt.PromptMessage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HyperparametersTest {

    @Test
    void processStringifiesScalarValuesAndSkipsNullsLikeDeepEval() {
        var raw = new LinkedHashMap<String, Object>();
        raw.put("model", "gpt-4.1");
        raw.put("temperature", 0.7);
        raw.put("shots", 3);
        raw.put("ignored", null);

        var processed = Hyperparameters.process(raw);

        assertEquals(Map.of(
                "model", "gpt-4.1",
                "temperature", "0.7",
                "shots", "3"), processed);
    }

    @Test
    void processRejectsNonStringKeysAndUnsupportedValuesLikeDeepEval() {
        var badKey = new LinkedHashMap<Object, Object>();
        badKey.put(1, "value");
        var badValue = Map.<String, Object>of("config", List.of("nested"));

        assertThrows(IllegalArgumentException.class, () -> Hyperparameters.process(badKey));
        assertThrows(IllegalArgumentException.class, () -> Hyperparameters.process(badValue));
    }

    @Test
    void processPromptsExtractsUniquePromptDataWithoutConfidentHash() {
        var textPrompt = new Prompt("assistant", "Hello {name}");
        var messagesPrompt = new Prompt(
                "chat",
                null,
                List.of(new PromptMessage("user", "Hi {{name}}")),
                null,
                OutputType.TEXT,
                null,
                PromptInterpolationType.MUSTACHE,
                null,
                null);
        var raw = new LinkedHashMap<String, Object>();
        raw.put("prompt", textPrompt);
        raw.put("duplicate", textPrompt);
        raw.put("messages", messagesPrompt);

        var prompts = Hyperparameters.processPrompts(raw);

        assertEquals(2, prompts.size());
        assertEquals("assistant", prompts.get(0).alias());
        assertEquals(null, prompts.get(0).hash());
        assertEquals("Hello {name}", prompts.get(0).textTemplate());
        assertEquals("chat", prompts.get(1).alias());
        assertEquals(List.of(new PromptMessage("user", "Hi {{name}}")), prompts.get(1).messagesTemplate());
        assertEquals(OutputType.TEXT, prompts.get(1).outputType());
        assertEquals(PromptInterpolationType.MUSTACHE, prompts.get(1).interpolationType());
    }
}
