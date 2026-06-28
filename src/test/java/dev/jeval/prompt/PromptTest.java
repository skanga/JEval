package dev.jeval.prompt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PromptTest {
    @TempDir
    Path tempDir;

    @Test
    void promptTypeValuesMatchDeepEval() {
        assertEquals("TEXT", PromptType.TEXT.value());
        assertEquals("LIST", PromptType.LIST.value());
    }

    @Test
    void loadPlainTextFileSetsAliasAndTextTemplate() throws IOException {
        var file = Files.writeString(tempDir.resolve("assistant.txt"), "You are a helpful assistant.");

        var prompt = new Prompt();
        var loaded = prompt.load(file);

        assertEquals("You are a helpful assistant.", loaded);
        assertEquals("assistant", prompt.alias());
        assertEquals("You are a helpful assistant.", prompt.textTemplate());
        assertEquals(PromptType.TEXT, prompt.type());
        assertNull(prompt.messagesTemplate());
    }

    @Test
    void constructorAcceptsTextTemplateAndUsesDefaultFStringInterpolation() {
        var prompt = new Prompt("assistant", "Hello {name}");

        assertEquals("assistant", prompt.alias());
        assertEquals("Hello {name}", prompt.textTemplate());
        assertEquals(PromptType.TEXT, prompt.type());
        assertNull(prompt.messagesTemplate());
        assertEquals(PromptInterpolationType.FSTRING, prompt.interpolationType());
        assertEquals("Hello Ada", prompt.interpolate(Map.of("name", "Ada")));
    }

    @Test
    void constructorAcceptsMessagesTemplateAndCustomInterpolationType() {
        var prompt = new Prompt(
                "chat",
                List.of(new PromptMessage("user", "Hello {{name}}")),
                PromptInterpolationType.MUSTACHE);

        assertEquals("chat", prompt.alias());
        assertNull(prompt.textTemplate());
        assertEquals(PromptType.LIST, prompt.type());
        assertEquals(List.of(new PromptMessage("user", "Hello {{name}}")), prompt.messagesTemplate());
        assertEquals(PromptInterpolationType.MUSTACHE, prompt.interpolationType());
        assertEquals(List.of(new PromptMessage("user", "Hello Ada")), prompt.interpolate(Map.of("name", "Ada")));
    }

    @Test
    void constructorStoresPromptApiOptions() {
        var settings = new ModelSettings(ModelProvider.OPEN_AI, "gpt-4", 0.0, 100, null, null, null, null, null, null);
        var schema = new OutputSchema("schema-1", List.of(), "Answer");

        var prompt = new Prompt(
                "assistant",
                "Hello {name}",
                null,
                settings,
                OutputType.SCHEMA,
                schema,
                PromptInterpolationType.FSTRING,
                "secret-key",
                "experiment");

        assertEquals(settings, prompt.modelSettings());
        assertEquals(OutputType.SCHEMA, prompt.outputType());
        assertEquals(schema, prompt.outputSchema());
        assertEquals("secret-key", prompt.confidentApiKey());
        assertEquals("experiment", prompt.branch());
    }

    @Test
    void constructorRejectsBothTextAndMessagesTemplate() {
        var error = assertThrows(IllegalArgumentException.class, () -> new Prompt(
                "bad",
                "Hello {name}",
                List.of(new PromptMessage("user", "Hello {name}")),
                PromptInterpolationType.FSTRING));

        assertEquals("Unable to create Prompt where 'text_template' and 'messages_template' are both provided. Please provide only one to continue.",
                error.getMessage());
    }

    @Test
    void loadJsonListFileSetsMessagesTemplate() throws IOException {
        var file = Files.writeString(tempDir.resolve("chat.json"), """
                [
                  {"role": "system", "content": "You are helpful."},
                  {"role": "user", "content": "Hello"}
                ]
                """);

        var prompt = new Prompt();
        var loaded = prompt.load(file);
        var expectedMessages = List.of(
                new PromptMessage("system", "You are helpful."),
                new PromptMessage("user", "Hello"));

        assertEquals("chat", prompt.alias());
        assertEquals(PromptType.LIST, prompt.type());
        assertEquals(2, prompt.messagesTemplate().size());
        assertEquals(expectedMessages, loaded);
        assertEquals(expectedMessages.getFirst(), prompt.messagesTemplate().getFirst());
        assertEquals(expectedMessages.get(1), prompt.messagesTemplate().get(1));
        assertNull(prompt.textTemplate());
    }

    @Test
    void loadJsonDictUsesProvidedMessagesKey() throws IOException {
        var file = Files.writeString(tempDir.resolve("chat.json"),
                "{\"custom_messages\":[{\"role\":\"system\",\"content\":\"Test\"}]}");

        var prompt = new Prompt();
        var loaded = prompt.load(file, "custom_messages");
        var expectedMessages = List.of(new PromptMessage("system", "Test"));

        assertEquals(1, prompt.messagesTemplate().size());
        assertEquals(expectedMessages, loaded);
        assertEquals(expectedMessages.getFirst(), prompt.messagesTemplate().getFirst());
    }

    @Test
    void loadJsonDictRequiresMessagesKey() throws IOException {
        var file = Files.writeString(tempDir.resolve("chat.json"),
                "{\"messages\":[{\"role\":\"system\",\"content\":\"Test\"}]}");

        var error = assertThrows(IllegalArgumentException.class, () -> new Prompt().load(file));

        assertEquals("messages `key` must be provided if file is a dictionary", error.getMessage());
    }

    @Test
    void loadInvalidJsonFallsBackToTextTemplate() throws IOException {
        var file = Files.writeString(tempDir.resolve("broken.json"), "This is not valid JSON content");

        var prompt = new Prompt();
        prompt.load(file);

        assertEquals("broken", prompt.alias());
        assertEquals("This is not valid JSON content", prompt.textTemplate());
        assertNull(prompt.messagesTemplate());
    }

    @Test
    void loadMalformedMessagesFallsBackToTextTemplate() throws IOException {
        var file = Files.writeString(tempDir.resolve("broken.json"), "[{\"invalid\":\"structure\"}]");

        var prompt = new Prompt();
        prompt.load(file);

        assertEquals("[{\"invalid\":\"structure\"}]", prompt.textTemplate());
        assertNull(prompt.messagesTemplate());
    }

    @Test
    void loadRejectsUnsupportedFileExtension() throws IOException {
        var file = Files.writeString(tempDir.resolve("prompt.py"), "print('hello')");

        var error = assertThrows(IllegalArgumentException.class, () -> new Prompt().load(file));

        assertEquals("Only .json and .txt files are supported", error.getMessage());
    }

    @Test
    void interpolateRendersLoadedTextTemplateWithDefaultFStringInterpolation() throws IOException {
        var file = Files.writeString(tempDir.resolve("assistant.txt"), "Hello {name}");
        var prompt = new Prompt();
        prompt.load(file);

        var rendered = prompt.interpolate(Map.of("name", "Ada"));

        assertEquals("Hello Ada", rendered);
    }

    @Test
    void interpolateRendersLoadedMessageTemplatesWithDefaultFStringInterpolation() throws IOException {
        var file = Files.writeString(tempDir.resolve("chat.json"), """
                [
                  {"role": "system", "content": "You help {company}."},
                  {"role": "user", "content": "Hi {name}"}
                ]
                """);
        var prompt = new Prompt();
        prompt.load(file);

        var rendered = prompt.interpolate(Map.of("company", "Acme", "name", "Ada"));

        assertEquals(List.of(
                new PromptMessage("system", "You help Acme."),
                new PromptMessage("user", "Hi Ada")), rendered);
    }

    @Test
    void interpolateRejectsEmptyPrompt() {
        var error = assertThrows(IllegalStateException.class, () -> new Prompt().interpolate(Map.of()));

        assertEquals("Unsupported prompt type: null", error.getMessage());
    }
}
