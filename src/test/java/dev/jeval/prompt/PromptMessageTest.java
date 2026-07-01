package dev.jeval.prompt;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PromptMessageTest {

    @Test
    void rejectsNullFields() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new PromptMessage(null, "hello")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new PromptMessage("user", null)));
    }
}
