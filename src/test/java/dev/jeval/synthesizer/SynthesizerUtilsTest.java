package dev.jeval.synthesizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SynthesizerUtilsTest {

    @Test
    void statusValuesMatchDeepEval() {
        assertEquals("success", SynthesizerStatus.SUCCESS.value());
        assertEquals("failure", SynthesizerStatus.FAILURE.value());
        assertEquals("warning", SynthesizerStatus.WARNING.value());
    }

    @Test
    void printSynthesizerStatusIncludesLabelMessageAndDescriptionLikeDeepEval() {
        var out = new ByteArrayOutputStream();
        var original = System.out;
        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));

            SynthesizerUtils.printSynthesizerStatus(
                    SynthesizerStatus.WARNING, "Skipped document", "No text was extracted");
        } finally {
            System.setOut(original);
        }

        var text = out.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("\033[2m[Confident AI Synthesizer Log]\033[0m WARNING:"));
        assertTrue(text.contains("Skipped document"));
        assertTrue(text.contains(": No text was extracted"));
        assertTrue(text.endsWith(System.lineSeparator()));
    }
}
