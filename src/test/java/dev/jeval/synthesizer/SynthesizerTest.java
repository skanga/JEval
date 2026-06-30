package dev.jeval.synthesizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.EvaluationModel;
import dev.jeval.ConversationalGolden;
import dev.jeval.Golden;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SynthesizerTest {
    @TempDir
    Path tempDir;

    @Test
    void generatesGoldensFromContexts() {
        var model = new ScriptedModel(List.of(
                "{\"data\":[{\"input\":\"What is France's capital?\",\"used_source_files\":[\"cities.txt\"]}]}",
                "{\"rewritten_input\":\"Which city is France's capital?\"}",
                "Paris"));
        var synthesizer = new Synthesizer(
                model,
                null,
                null,
                new EvolutionConfig(1, List.of(Evolution.REASONING)),
                noFiltrationConfig(),
                SynthesizerOptions.DEFAULT);

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
    void generatesGoldensFromContextsWithMultipleSourceFilesLikeDeepEval() {
        var model = new ScriptedModel(List.of(
                "{\"data\":[{\"input\":\"How do the documents relate?\"}]}"));
        var synthesizer = new Synthesizer(
                model, null, null, noEvolutionConfig(), noFiltrationConfig(), SynthesizerOptions.DEFAULT);
        List<Object> sourceFiles = List.of(List.of("policy.md", "faq.md"));

        var goldens = synthesizer.generateGoldensFromContexts(
                List.of(List.of("Policy text", "FAQ text")), false, 1, sourceFiles);

        assertEquals("policy.md", goldens.getFirst().sourceFile());
        assertEquals(List.of("policy.md", "faq.md"),
                goldens.getFirst().additionalMetadata().get("context_source_files"));
        assertTrue(model.prompts().getFirst().contains("policy.md"));
        assertTrue(model.prompts().getFirst().contains("faq.md"));
        assertTrue(model.prompts().getFirst().contains("used_source_files"));
    }

    @Test
    void rewritesLowQualitySyntheticInputsLikeDeepEval() {
        var model = new ScriptedModel(List.of(
                "{\"data\":[{\"input\":\"What about it?\",\"used_source_files\":[\"cities.txt\"]}]}",
                "{\"feedback\":\"The query is too vague.\",\"score\":0.2}",
                "{\"rewritten_input\":\"What is the capital city of France?\"}",
                "{\"feedback\":\"The rewritten query is clear.\",\"score\":0.9}"));
        var synthesizer = new Synthesizer(
                model,
                null,
                null,
                noEvolutionConfig(),
                new FiltrationConfig(0.5, 3, null),
                SynthesizerOptions.DEFAULT);

        var goldens = synthesizer.generateGoldensFromContexts(
                List.of(List.of("Paris is the capital of France.")), false, 1, List.of("cities.txt"));

        assertEquals("What is the capital city of France?", goldens.getFirst().input());
        assertEquals(0.9, goldens.getFirst().additionalMetadata().get("synthetic_input_quality"));
        assertEquals(List.of("cities.txt"), goldens.getFirst().additionalMetadata().get("used_source_files"));
        assertTrue(model.prompts().get(1).contains("Evaluate the provided synthetic query"));
        assertTrue(model.prompts().get(2).contains("The query is too vague."));
        assertEquals(4, model.prompts().size());
    }

    @Test
    void omitsExpectedOutputWhenDisabledEvenIfModelReturnsOne() {
        var model = new ScriptedModel(List.of(
                "{\"data\":[{\"input\":\"What is France's capital?\",\"expected_output\":\"Paris\"}]}"));
        var synthesizer = new Synthesizer(
                model, null, null, noEvolutionConfig(), noFiltrationConfig(), SynthesizerOptions.DEFAULT);

        var goldens = synthesizer.generateGoldensFromContexts(
                List.of(List.of("Paris is in France.")), false, 1, null);

        assertEquals(1, goldens.size());
        assertEquals("What is France's capital?", goldens.getFirst().input());
        assertEquals(null, goldens.getFirst().expectedOutput());
        var metadata = goldens.getFirst().additionalMetadata();
        assertEquals(List.of(), metadata.get("context_source_files"));
        assertEquals(List.of(), metadata.get("used_source_files"));
        assertTrue(metadata.containsKey("context_quality"));
        assertEquals(null, metadata.get("context_quality"));
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
        var synthesizer = new Synthesizer(
                model, null, null, noEvolutionConfig(), noFiltrationConfig(), SynthesizerOptions.DEFAULT);

        var goldens = synthesizer.generateGoldensFromContexts(
                List.of(List.of("Paris is in France.")), false, 1, null);

        assertEquals(List.of("First question?"), goldens.stream().map(Golden::input).toList());
    }

    @Test
    void defaultEvolutionConfigEvolvesInputsLikeDeepEval() {
        var model = new ScriptedModel(List.of(
                "{\"data\":[{\"input\":\"plain\"}]}",
                "{\"rewritten_input\":\"evolved\"}"));
        var synthesizer = new Synthesizer(
                model, null, null, new EvolutionConfig(), noFiltrationConfig(), SynthesizerOptions.DEFAULT);

        var goldens = synthesizer.generateGoldensFromContexts(
                List.of(List.of("context")), false, 1, null);

        assertEquals("evolved", goldens.getFirst().input());
        assertEquals(List.of("Reasoning"), goldens.getFirst().additionalMetadata().get("evolutions"));
        assertEquals(2, model.prompts().size());
    }

    @Test
    void generatesGoldensFromDocsLikeDeepEval() throws Exception {
        var document = tempDir.resolve("policy.md");
        Files.writeString(document, "alpha beta gamma delta");
        var model = new ScriptedModel(List.of(
                "{\"data\":[{\"input\":\"Question one?\",\"expected_output\":\"Answer one\"}]}",
                "{\"data\":[{\"input\":\"Question two?\",\"expected_output\":\"Answer two\"}]}"));
        var synthesizer = new Synthesizer(
                model,
                null,
                null,
                noEvolutionConfig(),
                noFiltrationConfig(),
                new SynthesizerOptions(false, 100, false));

        var goldens = synthesizer.generateGoldensFromDocs(
                List.of(document),
                true,
                1,
                new ContextConstructionConfig(2, 1, 2, 0, 0.5, 0.0, 3));

        assertEquals(List.of("Question one?", "Question two?"), goldens.stream().map(Golden::input).toList());
        assertEquals(List.of("Answer one", "Answer two"), goldens.stream().map(Golden::expectedOutput).toList());
        assertEquals(List.of(List.of("alpha beta"), List.of("gamma delta")),
                goldens.stream().map(Golden::context).toList());
        assertEquals(List.of("policy.md", "policy.md"), goldens.stream().map(Golden::sourceFile).toList());
        assertEquals(List.of(0.0, 0.0), goldens.stream()
                .map(golden -> golden.additionalMetadata().get("context_quality"))
                .toList());
    }

    @Test
    void saveAsWritesLastSyntheticGoldensLikeDeepEval() throws Exception {
        var model = new ScriptedModel(List.of(
                "{\"data\":[{\"input\":\"What is France's capital?\",\"expected_output\":\"Paris\"}]}"));
        var synthesizer = new Synthesizer(
                model, null, null, noEvolutionConfig(), noFiltrationConfig(), SynthesizerOptions.DEFAULT);
        synthesizer.generateGoldensFromContexts(List.of(List.of("Paris is in France.")), true, 1, null);

        var path = synthesizer.saveAs("json", tempDir.resolve("generated"), "goldens", true);

        assertEquals(tempDir.resolve("generated").resolve("goldens.json"), path);
        var exported = Files.readString(path);
        assertTrue(exported.contains("\"input\" : \"What is France's capital?\""));
        assertTrue(exported.contains("\"expected_output\" : \"Paris\""));
    }

    @Test
    void saveAsRejectsEmptySyntheticGoldensLikeDeepEval() {
        var synthesizer = new Synthesizer(prompt -> "{}");

        var error = assertThrows(IllegalStateException.class,
                () -> synthesizer.saveAs("json", tempDir.resolve("generated"), "empty", true));

        assertEquals("No synthetic goldens found. Please generate goldens before saving goldens.",
                error.getMessage());
    }

    @Test
    void saveAsRejectsFileNameWithPeriodsLikeDeepEval() {
        var model = new ScriptedModel(List.of(
                "{\"data\":[{\"input\":\"What is France's capital?\",\"expected_output\":\"Paris\"}]}"));
        var synthesizer = new Synthesizer(
                model, null, null, noEvolutionConfig(), noFiltrationConfig(), SynthesizerOptions.DEFAULT);
        synthesizer.generateGoldensFromContexts(List.of(List.of("Paris is in France.")), true, 1, null);

        var error = assertThrows(IllegalArgumentException.class,
                () -> synthesizer.saveAs("json", tempDir.resolve("generated"), "goldens.json", true));

        assertEquals("file_name should not contain periods or file extensions. "
                        + "The file extension will be added based on the file_type parameter.",
                error.getMessage());
    }

    @Test
    void generatesConversationalGoldensFromDocsLikeDeepEval() throws Exception {
        var document = tempDir.resolve("policy.md");
        Files.writeString(document, "alpha beta gamma delta");
        var model = new ScriptedModel(List.of(
                """
                {"data":[{"scenario":"Scenario one","turns":[{"role":"user","content":"Question one?"}],"expected_outcome":"Outcome one"}]}
                """,
                """
                {"data":[{"scenario":"Scenario two","turns":[{"role":"user","content":"Question two?"}],"expected_outcome":"Outcome two"}]}
                """));
        var synthesizer = new Synthesizer(
                model,
                null,
                null,
                noEvolutionConfig(),
                noFiltrationConfig(),
                new SynthesizerOptions(false, 100, false));

        var goldens = synthesizer.generateConversationalGoldensFromDocs(
                List.of(document),
                true,
                1,
                new ContextConstructionConfig(2, 1, 2, 0, 0.5, 0.0, 3));

        assertEquals(List.of("Scenario one", "Scenario two"),
                goldens.stream().map(ConversationalGolden::scenario).toList());
        assertEquals(List.of("Outcome one", "Outcome two"),
                goldens.stream().map(ConversationalGolden::expectedOutcome).toList());
        assertEquals(List.of(List.of("alpha beta"), List.of("gamma delta")),
                goldens.stream().map(ConversationalGolden::context).toList());
        assertEquals(List.of(0.0, 0.0), goldens.stream()
                .map(golden -> golden.additionalMetadata().get("context_quality"))
                .toList());
    }

    @Test
    void asyncContextGenerationUsesMaxConcurrentAndKeepsContextOrderLikeDeepEval() {
        var model = new ConcurrentContextModel();
        var synthesizer = new Synthesizer(
                model,
                null,
                null,
                noEvolutionConfig(),
                noFiltrationConfig(),
                new SynthesizerOptions(true, 2, false));

        var goldens = synthesizer.generateGoldensFromContexts(
                List.of(List.of("first context"), List.of("second context"), List.of("third context")),
                false,
                1,
                null);

        assertEquals(List.of("first question", "second question", "third question"),
                goldens.stream().map(Golden::input).toList());
        assertEquals(2, model.maxActive());
    }

    @Test
    void generatesGoldensFromScratchAndKeepsPerGoldenEvolutionMetadata() {
        var model = new ScriptedModel(List.of(
                "{\"data\":[{\"input\":\"first\"},{\"input\":\"second\"}]}",
                "{\"rewritten_input\":\"first evolved\"}",
                "{\"rewritten_input\":\"second evolved\"}"));
        var synthesizer = new Synthesizer(
                model,
                new StylingConfig("students learning geography", "ask study questions", null, null),
                null,
                new EvolutionConfig(1, List.of(Evolution.REASONING, Evolution.COMPARATIVE)),
                noFiltrationConfig(),
                SynthesizerOptions.DEFAULT);

        var goldens = synthesizer.generateGoldensFromScratch(2);

        assertEquals(List.of("first evolved", "second evolved"), goldens.stream().map(Golden::input).toList());
        assertEquals(List.of("Reasoning"), goldens.get(0).additionalMetadata().get("evolutions"));
        assertEquals(List.of("Comparative"), goldens.get(1).additionalMetadata().get("evolutions"));
    }

    @Test
    void rewritesEvolvedInputsToStylingFormatLikeDeepEval() {
        var model = new ScriptedModel(List.of(
                "{\"data\":[{\"input\":\"raw question\"}]}",
                "{\"rewritten_input\":\"evolved question\"}",
                "{\"input\":\"FORMATTED: evolved question\"}"));
        var synthesizer = new Synthesizer(
                model,
                new StylingConfig("students learning geography", "ask study questions", "prefix with FORMATTED:", null),
                null,
                new EvolutionConfig(1, List.of(Evolution.REASONING)),
                noFiltrationConfig(),
                SynthesizerOptions.DEFAULT);

        var goldens = synthesizer.generateGoldensFromScratch(1);

        assertEquals("FORMATTED: evolved question", goldens.getFirst().input());
        assertTrue(model.prompts().get(2).contains("Input Format"));
        assertTrue(model.prompts().get(2).contains("prefix with FORMATTED:"));
        assertTrue(model.prompts().get(2).contains("evolved question"));
        assertEquals(3, model.prompts().size());
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
        var synthesizer = new Synthesizer(
                model,
                null,
                null,
                new EvolutionConfig(1, List.of(Evolution.CONCRETIZING)),
                noFiltrationConfig(),
                SynthesizerOptions.DEFAULT);
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
        var synthesizer = new Synthesizer(
                model, null, null, noEvolutionConfig(), noFiltrationConfig(), SynthesizerOptions.DEFAULT);
        var original = Golden.builder("What is the capital of France?")
                .expectedOutput("Paris")
                .build();

        var goldens = synthesizer.generateGoldensFromGoldens(List.of(original), 1, false);

        assertEquals(1, goldens.size());
        assertEquals("What is a similar question?", goldens.getFirst().input());
        assertEquals(null, goldens.getFirst().expectedOutput());
    }

    @Test
    void generateGoldensFromMixedExistingGoldensUsesContextBranchLikeDeepEval() {
        var model = new ScriptedModel(List.of(
                "{\"data\":[{\"input\":\"Context question\"}]}",
                "Context answer"));
        var synthesizer = new Synthesizer(
                model, null, null, noEvolutionConfig(), noFiltrationConfig(), SynthesizerOptions.DEFAULT);
        var contextual = Golden.builder("old context")
                .context(List.of("context"))
                .sourceFile("context.txt")
                .build();
        var plain = Golden.builder("old plain").expectedOutput("old answer").build();

        var goldens = synthesizer.generateGoldensFromGoldens(List.of(contextual, plain), 1, true);

        assertEquals(1, goldens.size());
        assertEquals("Context question", goldens.get(0).input());
        assertEquals(List.of("context"), goldens.get(0).context());
        assertEquals("Context answer", goldens.get(0).expectedOutput());
        assertEquals(2, model.prompts().size());
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
        var synthesizer = new Synthesizer(
                model, null, null, noEvolutionConfig(), noFiltrationConfig(), SynthesizerOptions.DEFAULT);

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
    void generatesConversationalGoldensFromContextsWithMultipleSourceFilesLikeDeepEval() {
        var model = new ScriptedModel(List.of(
                """
                {"data":[{"scenario":"support agent uses policy and faq","turns":[
                  {"role":"user","content":"Can I get a refund?"},
                  {"role":"assistant","content":"Let me check."}
                ]}]}
                """));
        var synthesizer = new Synthesizer(
                model, null, null, noEvolutionConfig(), noFiltrationConfig(), SynthesizerOptions.DEFAULT);
        List<Object> sourceFiles = List.of(List.of("policy.md", "faq.md"));

        var goldens = synthesizer.generateConversationalGoldensFromContexts(
                List.of(List.of("Policy text", "FAQ text")), false, 1, sourceFiles);

        assertEquals("policy.md", goldens.getFirst().additionalMetadata().get("source_files"));
        assertEquals(List.of("policy.md", "faq.md"),
                goldens.getFirst().additionalMetadata().get("context_source_files"));
        assertTrue(model.prompts().getFirst().contains("policy.md"));
        assertTrue(model.prompts().getFirst().contains("faq.md"));
        assertTrue(model.prompts().getFirst().contains("used_source_files"));
    }

    @Test
    void evolvesConversationalScenariosLikeDeepEval() {
        var model = new ScriptedModel(List.of(
                """
                {"data":[{"scenario":"user asks about refund","turns":[
                  {"role":"user","content":"Can I get a refund?"},
                  {"role":"assistant","content":"I can help."}
                ]}]}
                """,
                "{\"rewritten_scenario\":\"A customer and support agent reason through refund eligibility\"}"));
        var synthesizer = new Synthesizer(
                model,
                null,
                null,
                new EvolutionConfig(1, List.of(Evolution.REASONING)),
                noFiltrationConfig(),
                SynthesizerOptions.DEFAULT);

        var goldens = synthesizer.generateConversationalGoldensFromContexts(
                List.of(List.of("Refunds are available within 30 days.")), false, 1, null);

        assertEquals("A customer and support agent reason through refund eligibility",
                goldens.getFirst().scenario());
        assertEquals(List.of("Reasoning"), goldens.getFirst().additionalMetadata().get("evolutions"));
        assertTrue(model.prompts().get(1).contains("Reasoning"));
        assertTrue(model.prompts().get(1).contains("user asks about refund"));
        assertEquals(2, model.prompts().size());
    }

    @Test
    void rewritesLowQualityConversationalScenariosLikeDeepEval() {
        var model = new ScriptedModel(List.of(
                """
                {"data":[{"scenario":"refund question","turns":[
                  {"role":"user","content":"Can I get a refund?"},
                  {"role":"assistant","content":"I can help."}
                ],"used_source_files":["refund.md"]}]}
                """,
                "{\"feedback\":\"The scenario does not identify participants or setting.\",\"score\":0.2}",
                "{\"rewritten_scenario\":\"A customer contacts a support agent about refund eligibility for a recent purchase\"}",
                "{\"feedback\":\"The rewritten scenario is conversational and clear.\",\"score\":0.9}"));
        var synthesizer = new Synthesizer(
                model,
                null,
                null,
                noEvolutionConfig(),
                new FiltrationConfig(0.5, 3, null),
                SynthesizerOptions.DEFAULT);

        var goldens = synthesizer.generateConversationalGoldensFromContexts(
                List.of(List.of("Refunds are available within 30 days.")), false, 1, List.of("refund.md"));

        assertEquals("A customer contacts a support agent about refund eligibility for a recent purchase",
                goldens.getFirst().scenario());
        assertEquals(0.9, goldens.getFirst().additionalMetadata().get("synthetic_scenario_quality"));
        assertEquals(List.of("refund.md"), goldens.getFirst().additionalMetadata().get("used_source_files"));
        assertTrue(model.prompts().get(1).contains("Evaluate the provided conversational scenario"));
        assertTrue(model.prompts().get(2).contains("The scenario does not identify participants or setting."));
        assertEquals(4, model.prompts().size());
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
        var synthesizer = new Synthesizer(
                model, null, null, noEvolutionConfig(), noFiltrationConfig(), SynthesizerOptions.DEFAULT);

        var goldens = synthesizer.generateConversationalGoldensFromContexts(
                List.of(List.of("Refunds are available within 30 days.")), false, 1, null);

        assertEquals(List.of("first scenario"), goldens.stream().map(ConversationalGolden::scenario).toList());
        var metadata = goldens.getFirst().additionalMetadata();
        assertTrue(metadata.containsKey("source_files"));
        assertEquals(null, metadata.get("source_files"));
        assertEquals(List.of(), metadata.get("context_source_files"));
        assertEquals(List.of(), metadata.get("used_source_files"));
        assertTrue(metadata.containsKey("context_quality"));
        assertEquals(null, metadata.get("context_quality"));
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
        var synthesizer = new Synthesizer(
                model,
                null,
                new ConversationalStylingConfig("travel support", "book flights", "traveler and agent", null),
                noEvolutionConfig(),
                noFiltrationConfig(),
                SynthesizerOptions.DEFAULT);

        var goldens = synthesizer.generateConversationalGoldensFromScratch(1);

        assertEquals(List.of("traveler books a flight"), goldens.stream().map(ConversationalGolden::scenario).toList());
        assertEquals("Flight search started", goldens.getFirst().expectedOutcome());
        assertEquals("assistant", goldens.getFirst().turns().get(1).role());
    }

    @Test
    void generatesConversationalGoldensFromScratchAndKeepsPerGoldenEvolutionMetadata() {
        var model = new ScriptedModel(List.of(
                """
                {"data":[
                  {"scenario":"first scenario","turns":[{"role":"user","content":"One"}]},
                  {"scenario":"second scenario","turns":[{"role":"user","content":"Two"}]}
                ]}
                """,
                "{\"rewritten_scenario\":\"first evolved\"}",
                "{\"rewritten_scenario\":\"second evolved\"}"));
        var synthesizer = new Synthesizer(
                model,
                null,
                new ConversationalStylingConfig("support", "resolve issues", "customer and agent", null),
                new EvolutionConfig(1, List.of(Evolution.REASONING, Evolution.COMPARATIVE)),
                noFiltrationConfig(),
                SynthesizerOptions.DEFAULT);

        var goldens = synthesizer.generateConversationalGoldensFromScratch(2);

        assertEquals(List.of("first evolved", "second evolved"),
                goldens.stream().map(ConversationalGolden::scenario).toList());
        assertEquals(List.of("Reasoning"), goldens.get(0).additionalMetadata().get("evolutions"));
        assertEquals(List.of("Comparative"), goldens.get(1).additionalMetadata().get("evolutions"));
        assertTrue(model.prompts().get(1).contains("Reasoning"));
        assertTrue(model.prompts().get(2).contains("Comparative"));
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
        var synthesizer = new Synthesizer(
                model, null, null, noEvolutionConfig(), noFiltrationConfig(), SynthesizerOptions.DEFAULT);
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
        var synthesizer = new Synthesizer(
                model, null, null, noEvolutionConfig(), noFiltrationConfig(), SynthesizerOptions.DEFAULT);
        var original = ConversationalGolden.builder("traveler wants to rebook a flight").build();

        var goldens = synthesizer.generateConversationalGoldensFromGoldens(List.of(original), 1, false);

        assertEquals(1, goldens.size());
        assertEquals("flight change request", goldens.getFirst().scenario());
        assertEquals("user", goldens.getFirst().turns().getFirst().role());
        assertEquals(true, model.prompts().getFirst().contains("traveler wants to rebook a flight"));
    }

    @Test
    void generateConversationalGoldensFromMixedExistingGoldensUsesContextBranchLikeDeepEval() {
        var model = new ScriptedModel(List.of(
                """
                {"data":[{"scenario":"context scenario","turns":[
                  {"role":"user","content":"Ask about context"}
                ],"expected_outcome":"Context explained"}]}
                """));
        var synthesizer = new Synthesizer(
                model, null, null, noEvolutionConfig(), noFiltrationConfig(), SynthesizerOptions.DEFAULT);
        var contextual = ConversationalGolden.builder("old context")
                .context(List.of("context"))
                .build();
        var plain = ConversationalGolden.builder("old plain").build();

        var goldens = synthesizer.generateConversationalGoldensFromGoldens(List.of(contextual, plain), 1, true);

        assertEquals(1, goldens.size());
        assertEquals("context scenario", goldens.getFirst().scenario());
        assertEquals(List.of("context"), goldens.getFirst().context());
        assertEquals(1, model.prompts().size());
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

    private static EvolutionConfig noEvolutionConfig() {
        return new EvolutionConfig(0, List.of(Evolution.REASONING));
    }

    private static FiltrationConfig noFiltrationConfig() {
        return new FiltrationConfig(0.5, 0, null);
    }

    private static final class ConcurrentContextModel implements EvaluationModel {
        private final AtomicInteger active = new AtomicInteger();
        private final AtomicInteger maxActive = new AtomicInteger();

        @Override
        public String generate(String prompt) {
            var current = active.incrementAndGet();
            maxActive.accumulateAndGet(current, Math::max);
            try {
                Thread.sleep(100);
                if (prompt.contains("first context")) {
                    return "{\"data\":[{\"input\":\"first question\"}]}";
                }
                if (prompt.contains("second context")) {
                    return "{\"data\":[{\"input\":\"second question\"}]}";
                }
                if (prompt.contains("third context")) {
                    return "{\"data\":[{\"input\":\"third question\"}]}";
                }
                return "{\"data\":[{\"input\":\"unknown question\"}]}";
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(error);
            } finally {
                active.decrementAndGet();
            }
        }

        int maxActive() {
            return maxActive.get();
        }
    }
}
