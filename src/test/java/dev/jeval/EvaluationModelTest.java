package dev.jeval;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EvaluationModelTest {

    @Test
    void batchGenerateFallsBackToGenerateForEachPrompt() {
        var prompts = new ArrayList<String>();
        EvaluationModel model = prompt -> {
            prompts.add(prompt);
            return prompt.toUpperCase();
        };

        var responses = model.batchGenerate(List.of("a", "b"));

        assertEquals(List.of("A", "B"), responses);
        assertEquals(List.of("a", "b"), prompts);
    }
}
