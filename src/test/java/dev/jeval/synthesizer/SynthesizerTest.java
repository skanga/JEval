package dev.jeval.synthesizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.jeval.EvaluationModel;
import dev.jeval.ConversationalGolden;
import dev.jeval.Golden;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SynthesizerTest {

    @Test
    void generatesGoldensFromContexts() {
        var model = new ScriptedModel(List.of(
                "{\"data\":[{\"input\":\"What is France's capital?\",\"used_source_files\":[\"cities.txt\"]}]}",
                "{\"rewritten_input\":\"Which city is France's capital?\"}",
                "Paris"));
        var synthesizer = new Synthesizer(model, null,
                new EvolutionConfig(1, List.of(Evolution.REASONING)));

        var goldens = synthesizer.generateGoldensFromContexts(
                List.of(List.of("Paris is in France.")), true, 1, List.of("cities.txt"));

        assertEquals(1, goldens.size());
        assertEquals("Which city is France's capital?", goldens.getFirst().input());
        assertEquals("Paris", goldens.getFirst().expectedOutput());
        assertEquals(List.of("Paris is in France."), goldens.getFirst().context());
        assertEquals("cities.txt", goldens.getFirst().sourceFile());
        assertEquals(List.of("Reasoning"), goldens.getFirst().additionalMetadata().get("evolutions"));
        assertEquals(List.of("cities.txt"), goldens.getFirst().additionalMetadata().get("used_source_files"));
        assertEquals(3, model.prompts().size());
    }

    @Test
    void omitsExpectedOutputWhenDisabledEvenIfModelReturnsOne() {
        var model = new ScriptedModel(List.of(
                "{\"data\":[{\"input\":\"What is France's capital?\",\"expected_output\":\"Paris\"}]}"));
        var synthesizer = new Synthesizer(model);

        var goldens = synthesizer.generateGoldensFromContexts(
                List.of(List.of("Paris is in France.")), false, 1, null);

        assertEquals(1, goldens.size());
        assertEquals("What is France's capital?", goldens.getFirst().input());
        assertEquals(null, goldens.getFirst().expectedOutput());
        assertEquals(false, model.prompts().getFirst().contains("expected_output"));
        assertEquals(1, model.prompts().size());
    }

    @Test
    void capsSyntheticInputsReturnedForContextLikeDeepEval() {
        var model = new ScriptedModel(List.of(
                """
                {"data":[
                  {"input":"First question?"},
                  {"input":"Second question?"}
                ]}
                """));
        var synthesizer = new Synthesizer(model);

        var goldens = synthesizer.generateGoldensFromContexts(
                List.of(List.of("Paris is in France.")), false, 1, null);

        assertEquals(List.of("First question?"), goldens.stream().map(Golden::input).toList());
    }

    @Test
    void defaultConfigDoesNotEvolveInputs() {
        var model = new ScriptedModel(List.of(
                "{\"data\":[{\"input\":\"plain\"}]}"));
        var synthesizer = new Synthesizer(model);

        var goldens = synthesizer.generateGoldensFromContexts(
                List.of(List.of("context")), false, 1, null);

        assertEquals("plain", goldens.getFirst().input());
        assertEquals(List.of(), goldens.getFirst().additionalMetadata().get("evolutions"));
        assertEquals(1, model.prompts().size());
    }

    @Test
    void generatesGoldensFromScratchAndKeepsPerGoldenEvolutionMetadata() {
        var model = new ScriptedModel(List.of(
                "{\"data\":[{\"input\":\"first\"},{\"input\":\"second\"}]}",
                "{\"rewritten_input\":\"first evolved\"}",
                "{\"rewritten_input\":\"second evolved\"}"));
        var synthesizer = new Synthesizer(model,
                new StylingConfig("students learning geography", "ask study questions", "one question", null),
                new EvolutionConfig(1, List.of(Evolution.REASONING, Evolution.COMPARATIVE)));

        var goldens = synthesizer.generateGoldensFromScratch(2);

        assertEquals(List.of("first evolved", "second evolved"), goldens.stream().map(Golden::input).toList());
        assertEquals(List.of("Reasoning"), goldens.get(0).additionalMetadata().get("evolutions"));
        assertEquals(List.of("Comparative"), goldens.get(1).additionalMetadata().get("evolutions"));
    }

    @Test
    void scratchGenerationRequiresStylingConfig() {
        var synthesizer = new Synthesizer(prompt -> "{}");

        assertThrows(IllegalStateException.class, () -> synthesizer.generateGoldensFromScratch(1));
    }

    @Test
    void generatesGoldensFromExistingGoldensWithContext() {
        var model = new ScriptedModel(List.of(
                "{\"data\":[{\"input\":\"What does the table store?\"}]}",
                "{\"rewritten_input\":\"What data is stored in the table?\"}"));
        var synthesizer = new Synthesizer(model, null,
                new EvolutionConfig(1, List.of(Evolution.CONCRETIZING)));
        var original = Golden.builder("old")
                .context(List.of("CREATE TABLE students (id INT)"))
                .sourceFile("schema.sql")
                .build();

        var goldens = synthesizer.generateGoldensFromGoldens(List.of(original), 1, false);

        assertEquals(1, goldens.size());
        assertEquals("What data is stored in the table?", goldens.getFirst().input());
        assertEquals(List.of("CREATE TABLE students (id INT)"), goldens.getFirst().context());
        assertEquals("schema.sql", goldens.getFirst().sourceFile());
    }

    @Test
    void generatesGoldensFromExistingGoldensWithoutStylingConfig() {
        var model = new ScriptedModel(List.of(
                "{\"data\":[{\"input\":\"What is a similar question?\"}]}"));
        var synthesizer = new Synthesizer(model);
        var original = Golden.builder("What is the capital of France?")
                .expectedOutput("Paris")
                .build();

        var goldens = synthesizer.generateGoldensFromGoldens(List.of(original), 1, false);

        assertEquals(1, goldens.size());
        assertEquals("What is a similar question?", goldens.getFirst().input());
        assertEquals(null, goldens.getFirst().expectedOutput());
    }

    @Test
    void generatesFromMixedExistingGoldens() {
        var model = new ScriptedModel(List.of(
                "{\"data\":[{\"input\":\"Context question\"}]}",
                "Context answer",
                "{\"data\":[{\"input\":\"Plain question\",\"expected_output\":\"Plain answer\"}]}"));
        var synthesizer = new Synthesizer(model);
        var contextual = Golden.builder("old context")
                .context(List.of("context"))
                .sourceFile("context.txt")
                .build();
        var plain = Golden.builder("old plain").expectedOutput("old answer").build();

        var goldens = synthesizer.generateGoldensFromGoldens(List.of(contextual, plain), 1, true);

        assertEquals(2, goldens.size());
        assertEquals("Context question", goldens.get(0).input());
        assertEquals(List.of("context"), goldens.get(0).context());
        assertEquals("Context answer", goldens.get(0).expectedOutput());
        assertEquals("Plain question", goldens.get(1).input());
        assertEquals("Plain answer", goldens.get(1).expectedOutput());
        assertEquals(true, model.prompts().get(2).contains("expected_output"));
    }

    @Test
    void generatesConversationalGoldensFromContexts() {
        var model = new ScriptedModel(List.of(
                """
                {"data":[{"scenario":"user needs refund help","turns":[
                  {"role":"user","content":"I need a refund"},
                  {"role":"assistant","content":"I can help"}
                ],"used_source_files":["refund.md"]}]}
                """,
                "Help the user get a refund"));
        var synthesizer = new Synthesizer(model);

        var goldens = synthesizer.generateConversationalGoldensFromContexts(
                List.of(List.of("Refunds are available within 30 days.")), true, 1, List.of("refund.md"));

        assertEquals(1, goldens.size());
        assertEquals("user needs refund help", goldens.getFirst().scenario());
        assertEquals("Help the user get a refund", goldens.getFirst().expectedOutcome());
        assertEquals(List.of("Refunds are available within 30 days."), goldens.getFirst().context());
        assertEquals(List.of("refund.md"), goldens.getFirst().additionalMetadata().get("used_source_files"));
        assertEquals("user", goldens.getFirst().turns().getFirst().role());
        assertEquals(2, model.prompts().size());
    }

    @Test
    void capsConversationalScenariosReturnedForContextLikeDeepEval() {
        var model = new ScriptedModel(List.of(
                """
                {"data":[
                  {"scenario":"first scenario","turns":[{"role":"user","content":"One"}]},
                  {"scenario":"second scenario","turns":[{"role":"user","content":"Two"}]}
                ]}
                """));
        var synthesizer = new Synthesizer(model);

        var goldens = synthesizer.generateConversationalGoldensFromContexts(
                List.of(List.of("Refunds are available within 30 days.")), false, 1, null);

        assertEquals(List.of("first scenario"), goldens.stream().map(ConversationalGolden::scenario).toList());
    }

    @Test
    void parsesConversationalTurnsFromModelJson() {
        var data = SynthesizerSchemas.parseConversationalData("""
                {"data":[{"scenario":"support","turns":[{"role":"user","content":"hello"}]}]}
                """);

        assertEquals("user", data.getFirst().turns().getFirst().role());
        assertEquals("hello", data.getFirst().turns().getFirst().content());
    }

    @Test
    void generatesConversationalGoldensFromScratch() {
        var model = new ScriptedModel(List.of(
                """
                {"data":[{"scenario":"traveler books a flight","turns":[
                  {"role":"user","content":"Book me a flight"},
                  {"role":"assistant","content":"Where to?"}
                ],"expected_outcome":"Flight search started"}]}
                """));
        var synthesizer = new Synthesizer(model,
                new ConversationalStylingConfig("travel support", "book flights", "traveler and agent", null));

        var goldens = synthesizer.generateConversationalGoldensFromScratch(1);

        assertEquals(List.of("traveler books a flight"), goldens.stream().map(ConversationalGolden::scenario).toList());
        assertEquals("Flight search started", goldens.getFirst().expectedOutcome());
        assertEquals("assistant", goldens.getFirst().turns().get(1).role());
    }

    @Test
    void conversationalScratchRequiresStylingConfig() {
        var synthesizer = new Synthesizer(prompt -> "{}");

        assertThrows(IllegalStateException.class, () -> synthesizer.generateConversationalGoldensFromScratch(1));
    }

    @Test
    void generatesConversationalGoldensFromExistingGoldensWithContext() {
        var model = new ScriptedModel(List.of(
                """
                {"data":[{"scenario":"refund follow up","turns":[
                  {"role":"user","content":"Can I still get a refund?"},
                  {"role":"assistant","content":"Yes, within 30 days."}
                ],"expected_outcome":"Refund eligibility explained"}]}
                """));
        var synthesizer = new Synthesizer(model);
        var original = ConversationalGolden.builder("old refund scenario")
                .context(List.of("Refunds are available within 30 days."))
                .build();

        var goldens = synthesizer.generateConversationalGoldensFromGoldens(List.of(original), 1, true);

        assertEquals(1, goldens.size());
        assertEquals("refund follow up", goldens.getFirst().scenario());
        assertEquals(List.of("Refunds are available within 30 days."), goldens.getFirst().context());
        assertEquals("Refund eligibility explained", goldens.getFirst().expectedOutcome());
    }

    @Test
    void generatesConversationalGoldensFromExistingGoldensWithoutContext() {
        var model = new ScriptedModel(List.of(
                """
                {"data":[{"scenario":"flight change request","turns":[
                  {"role":"user","content":"Change my flight"},
                  {"role":"assistant","content":"I can help with that."}
                ]}]}
                """));
        var synthesizer = new Synthesizer(model);
        var original = ConversationalGolden.builder("traveler wants to rebook a flight").build();

        var goldens = synthesizer.generateConversationalGoldensFromGoldens(List.of(original), 1, false);

        assertEquals(1, goldens.size());
        assertEquals("flight change request", goldens.getFirst().scenario());
        assertEquals("user", goldens.getFirst().turns().getFirst().role());
        assertEquals(true, model.prompts().getFirst().contains("traveler wants to rebook a flight"));
    }

    private static final class ScriptedModel implements EvaluationModel {
        private final List<String> responses;
        private final List<String> prompts = new ArrayList<>();

        private ScriptedModel(List<String> responses) {
            this.responses = responses;
        }

        @Override
        public String generate(String prompt) {
            prompts.add(prompt);
            return responses.get(prompts.size() - 1);
        }

        List<String> prompts() {
            return prompts;
        }
    }
}
