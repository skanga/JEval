package dev.jeval.synthesizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.EvaluationModel;
import dev.jeval.ConversationalGolden;
import dev.jeval.Golden;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
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
    void generateGoldensFromContextsRejectsEmptyContextsLikeDeepEval() {
        var synthesizer = new Synthesizer(new ScriptedModel(List.of()));

        var singleTurnError = assertThrows(IllegalArgumentException.class,
                () -> synthesizer.generateGoldensFromContexts(List.of(), false, 1, null));
        assertEquals("contexts must not be empty", singleTurnError.getMessage());

        var multiTurnError = assertThrows(IllegalArgumentException.class,
                () -> synthesizer.generateConversationalGoldensFromContexts(List.of(), false, 1, null));
        assertEquals("contexts must not be empty", multiTurnError.getMessage());
    }

    @Test
    void generateGoldensFromContextsRejectsNullContextEntriesLikeDeepEval() {
        var contexts = new ArrayList<List<String>>();
        contexts.add(null);
        var synthesizer = new Synthesizer(new ScriptedModel(List.of()));

        var error = assertThrows(IllegalArgumentException.class,
                () -> synthesizer.generateGoldensFromContexts(contexts, false, 1, null));

        assertEquals("contexts must not contain null context entries", error.getMessage());
    }

    @Test
    void generateGoldensFromContextsRejectsNullContextChunksLikeDeepEval() {
        var context = new ArrayList<String>();
        context.add("valid context");
        context.add(null);
        var synthesizer = new Synthesizer(new ScriptedModel(List.of()));

        var error = assertThrows(IllegalArgumentException.class,
                () -> synthesizer.generateGoldensFromContexts(List.of(context), false, 1, null));

        assertEquals("contexts must not contain null chunks", error.getMessage());
    }

    @Test
    void generatesExpectedOutputSeparatelyForContextGoldensLikeDeepEval() {
        var model = new ScriptedModel(List.of(
                "{\"data\":[{\"input\":\"What is France's capital?\",\"expected_output\":\"Embedded answer\"}]}",
                "Generated answer from context"));
        var synthesizer = new Synthesizer(
                model, null, null, noEvolutionConfig(), noFiltrationConfig(), SynthesizerOptions.DEFAULT);

        var goldens = synthesizer.generateGoldensFromContexts(
                List.of(List.of("Paris is in France.")), true, 1, null);

        assertEquals("Generated answer from context", goldens.getFirst().expectedOutput());
        assertTrue(model.prompts().get(1).contains("Generate the expected output"));
        assertTrue(model.prompts().get(1).contains("What is France's capital?"));
        assertEquals(2, model.prompts().size());
    }

    @Test
    void generatesTextToSqlGoldensWithoutExpectedOutputLikeDeepEval() {
        var model = new ScriptedModel(List.of(
                "{\"data\":[{\"input\":\"show all users\"}]}"));
        var synthesizer = new Synthesizer(
                model, null, null, noEvolutionConfig(), noFiltrationConfig(), SynthesizerOptions.DEFAULT);

        var goldens = synthesizer.generateTextToSqlGoldensFromContext(
                List.of("CREATE TABLE users (id INT, name TEXT)"), false, 1);

        assertEquals(1, goldens.size());
        assertEquals("show all users", goldens.getFirst().input());
        assertEquals(null, goldens.getFirst().expectedOutput());
        assertEquals(List.of("CREATE TABLE users (id INT, name TEXT)"), goldens.getFirst().context());
        assertTrue(model.prompts().getFirst().contains("SQL table schema"));
        assertEquals(1, model.prompts().size());
    }

    @Test
    void generatesTextToSqlExpectedOutputFromSqlJsonLikeDeepEval() {
        var model = new ScriptedModel(List.of(
                "{\"data\":[{\"input\":\"show all users\"}]}",
                "{\"sql\":\"SELECT * FROM users\"}"));
        var synthesizer = new Synthesizer(
                model, null, null, noEvolutionConfig(), noFiltrationConfig(), SynthesizerOptions.DEFAULT);

        var goldens = synthesizer.generateTextToSqlGoldensFromContext(
                List.of("CREATE TABLE users (id INT, name TEXT)"), true, 1);

        assertEquals(1, goldens.size());
        assertEquals("show all users", goldens.getFirst().input());
        assertEquals("SELECT * FROM users", goldens.getFirst().expectedOutput());
        assertEquals(List.of("CREATE TABLE users (id INT, name TEXT)"), goldens.getFirst().context());
        assertTrue(model.prompts().get(1).contains("generate a JSON object with a key 'sql'"));
        assertEquals(2, model.prompts().size());
    }

    @Test
    void generatesTextToSqlGoldensAsyncLikeDeepEval() {
        var model = new ScriptedModel(List.of(
                "{\"data\":[{\"input\":\"show all users\"}]}"));
        var synthesizer = new Synthesizer(
                model, null, null, noEvolutionConfig(), noFiltrationConfig(), SynthesizerOptions.DEFAULT);

        var goldens = synthesizer.generateTextToSqlGoldensFromContextAsync(
                List.of("CREATE TABLE users (id INT, name TEXT)"), false, 1).join();

        assertEquals(List.of("show all users"), goldens.stream().map(Golden::input).toList());
        assertEquals(null, goldens.getFirst().expectedOutput());
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
        assertTrue(model.prompts().getFirst().contains("[SOURCE: policy.md] Policy text"));
        assertTrue(model.prompts().getFirst().contains("[SOURCE: faq.md] FAQ text"));
        assertTrue(model.prompts().getFirst().contains("used_source_files"));
    }

    @Test
    void generatesCrossFileInstructionsForUnalignedAvailableSourceFilesLikeDeepEval() {
        var model = new ScriptedModel(List.of(
                "{\"data\":[{\"input\":\"How do policy and FAQ connect?\"}]}"));
        var synthesizer = new Synthesizer(
                model, null, null, noEvolutionConfig(), noFiltrationConfig(), SynthesizerOptions.DEFAULT);
        List<Object> sourceFiles = List.of(List.of("policy.md", "faq.md"));

        synthesizer.generateGoldensFromContexts(
                List.of(List.of("Combined policy and FAQ text")), false, 1, sourceFiles);

        assertTrue(model.prompts().getFirst().contains("policy.md"));
        assertTrue(model.prompts().getFirst().contains("faq.md"));
        assertTrue(model.prompts().getFirst().contains("used_source_files"));
    }

    @Test
    void generatesGoldensWithSeparateChunkSourceFilesLikeDeepEval() {
        var model = new ScriptedModel(List.of(
                "{\"data\":[{\"input\":\"How do policy and FAQ connect?\"}]}"));
        var synthesizer = new Synthesizer(
                model, null, null, noEvolutionConfig(), noFiltrationConfig(), SynthesizerOptions.DEFAULT);
        List<Object> sourceFiles = List.of(List.of("policy.md", "faq.md"));

        var goldens = synthesizer.generateGoldensFromContexts(
                List.of(List.of("Policy text", "FAQ text")),
                false,
                1,
                sourceFiles,
                List.of(List.of("policy.md", "faq.md")),
                null);

        assertEquals(List.of("policy.md", "faq.md"),
                goldens.getFirst().additionalMetadata().get("context_source_files"));
        assertTrue(model.prompts().getFirst().contains("[SOURCE: policy.md] Policy text"));
        assertTrue(model.prompts().getFirst().contains("[SOURCE: faq.md] FAQ text"));
        assertTrue(model.prompts().getFirst().contains("used_source_files"));
    }

    @Test
    void keepsSingleFileContextChunksUnlabeledLikeDeepEval() {
        var model = new ScriptedModel(List.of(
                "{\"data\":[{\"input\":\"What does the document say?\"}]}"));
        var synthesizer = new Synthesizer(
                model, null, null, noEvolutionConfig(), noFiltrationConfig(), SynthesizerOptions.DEFAULT);
        List<Object> sourceFiles = List.of(List.of("policy.md", "policy.md"));

        synthesizer.generateGoldensFromContexts(
                List.of(List.of("Policy text", "More policy text")), false, 1, sourceFiles);

        assertTrue(model.prompts().getFirst().contains("Policy text"));
        assertTrue(model.prompts().getFirst().contains("More policy text"));
        assertEquals(false, model.prompts().getFirst().contains("[SOURCE: policy.md]"));
        assertEquals(false, model.prompts().getFirst().contains("used_source_files"));
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
                "Answer one",
                "{\"data\":[{\"input\":\"Question two?\",\"expected_output\":\"Answer two\"}]}",
                "Answer two"));
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
        assertEquals(List.of(document.toString(), document.toString()),
                goldens.stream().map(Golden::sourceFile).toList());
        assertEquals(List.of(0.0, 0.0), goldens.stream()
                .map(golden -> golden.additionalMetadata().get("context_quality"))
                .toList());
    }

    @Test
    void generateGoldensFromDocsPreservesDocumentPathSourceLabelsLikeDeepEval() throws Exception {
        var document = tempDir.resolve("policy.md");
        Files.writeString(document, "alpha beta");
        var model = new ScriptedModel(List.of("{\"data\":[{\"input\":\"Question one?\"}]}"));
        var synthesizer = new Synthesizer(
                model,
                null,
                null,
                noEvolutionConfig(),
                noFiltrationConfig(),
                new SynthesizerOptions(false, 100, false));

        var goldens = synthesizer.generateGoldensFromDocs(
                List.of(document),
                false,
                1,
                new ContextConstructionConfig(1, 1, 2, 0, 0.5, 0.0, 3));

        assertEquals(document.toString(), goldens.getFirst().sourceFile());
        assertEquals(List.of(document.toString()),
                goldens.getFirst().additionalMetadata().get("context_source_files"));
    }

    @Test
    void generateGoldensFromDocsBuildsMultiChunkContextsLikeDeepEval() throws Exception {
        var document = tempDir.resolve("policy.md");
        Files.writeString(document, "alpha beta gamma delta");
        var model = new ScriptedModel(List.of("{\"data\":[{\"input\":\"Question from combined context?\"}]}"));
        var synthesizer = new Synthesizer(
                model,
                null,
                null,
                noEvolutionConfig(),
                noFiltrationConfig(),
                new SynthesizerOptions(false, 100, false));

        var goldens = synthesizer.generateGoldensFromDocs(
                List.of(document),
                false,
                1,
                new ContextConstructionConfig(1, 1, 2, 2, 1, 0, 0.5, 0.0, 3));

        assertEquals(1, goldens.size());
        assertEquals(List.of("alpha", "beta"), goldens.getFirst().context());
        assertTrue(model.prompts().getFirst().contains("alpha"));
        assertTrue(model.prompts().getFirst().contains("beta"));
        assertEquals(false, model.prompts().getFirst().contains("gamma"));
    }

    @Test
    void generateGoldensFromPdfDocsExtractsTextLikeDeepEval() throws Exception {
        var document = tempDir.resolve("policy.pdf");
        writePdf(document, "PDF policy allows refunds");
        var model = new ScriptedModel(List.of("{\"data\":[{\"input\":\"What does the PDF say?\"}]}"));
        var synthesizer = new Synthesizer(
                model,
                null,
                null,
                noEvolutionConfig(),
                noFiltrationConfig(),
                new SynthesizerOptions(false, 100, false));

        var goldens = synthesizer.generateGoldensFromDocs(
                List.of(document),
                false,
                1,
                new ContextConstructionConfig(1, 1, 50, 0, 0.5, 0.0, 3));

        assertEquals("What does the PDF say?", goldens.getFirst().input());
        assertTrue(goldens.getFirst().context().getFirst().contains("PDF policy allows refunds"));
        assertEquals(document.toString(), goldens.getFirst().sourceFile());
        assertTrue(model.prompts().getFirst().contains("PDF policy allows refunds"));
    }

    @Test
    void generateGoldensFromDocxDocsExtractsTextLikeDeepEval() throws Exception {
        var document = tempDir.resolve("handbook.docx");
        writeDocx(document, "DOCX handbook requires citations");
        var model = new ScriptedModel(List.of("{\"data\":[{\"input\":\"What does the DOCX require?\"}]}"));
        var synthesizer = new Synthesizer(
                model,
                null,
                null,
                noEvolutionConfig(),
                noFiltrationConfig(),
                new SynthesizerOptions(false, 100, false));

        var goldens = synthesizer.generateGoldensFromDocs(
                List.of(document),
                false,
                1,
                new ContextConstructionConfig(1, 1, 50, 0, 0.5, 0.0, 3));

        assertEquals("What does the DOCX require?", goldens.getFirst().input());
        assertTrue(goldens.getFirst().context().getFirst().contains("DOCX handbook requires citations"));
        assertEquals(document.toString(), goldens.getFirst().sourceFile());
        assertTrue(model.prompts().getFirst().contains("DOCX handbook requires citations"));
    }

    @Test
    void generateGoldensFromTextDocsStripsUtf8BomLikeDeepEval() throws Exception {
        var document = tempDir.resolve("bom.md");
        Files.write(document, new byte[] {
                (byte) 0xEF, (byte) 0xBB, (byte) 0xBF,
                'H', 'e', 'l', 'l', 'o', ' ', 'p', 'o', 'l', 'i', 'c', 'y'
        });
        var model = new ScriptedModel(List.of("{\"data\":[{\"input\":\"What is loaded?\"}]}"));
        var synthesizer = new Synthesizer(
                model,
                null,
                null,
                noEvolutionConfig(),
                noFiltrationConfig(),
                new SynthesizerOptions(false, 100, false));

        var goldens = synthesizer.generateGoldensFromDocs(
                List.of(document),
                false,
                1,
                new ContextConstructionConfig(1, 1, 50, 0, 0.5, 0.0, 3));

        assertEquals("Hello policy", goldens.getFirst().context().getFirst());
        assertTrue(model.prompts().getFirst().contains("Hello policy"));
        assertEquals(false, model.prompts().getFirst().contains("\uFEFF"));
    }

    @Test
    void generateGoldensFromTextDocsHonorsExplicitEncodingLikeDeepEval() throws Exception {
        var document = tempDir.resolve("utf16.md");
        Files.writeString(document, "alpha café", StandardCharsets.UTF_16);
        var model = new ScriptedModel(List.of("{\"data\":[{\"input\":\"What encoding is loaded?\"}]}"));
        var synthesizer = new Synthesizer(
                model,
                null,
                null,
                noEvolutionConfig(),
                noFiltrationConfig(),
                new SynthesizerOptions(false, 100, false));

        var goldens = synthesizer.generateGoldensFromDocs(
                List.of(document),
                false,
                1,
                new ContextConstructionConfig(1, 1, 1, 1, 20, 0, 0.5, 0.0, 3,
                        false, null, 3, "UTF-16"));

        assertEquals("alpha café", goldens.getFirst().context().getFirst());
        assertTrue(model.prompts().getFirst().contains("alpha café"));
    }

    @Test
    void generateGoldensFromMarkdownFamilyPreservesTableFormattingLikeDeepEval() throws Exception {
        for (var extension : List.of(".md", ".markdown", ".mdx")) {
            var document = tempDir.resolve("sample" + extension);
            Files.writeString(document, "# T\n\n| A | B |\n| - | - |\n| 1 | 2 |\n");
            var model = new ScriptedModel(List.of("{\"data\":[{\"input\":\"What table is loaded?\"}]}"));
            var synthesizer = new Synthesizer(
                    model,
                    null,
                    null,
                    noEvolutionConfig(),
                    noFiltrationConfig(),
                    new SynthesizerOptions(false, 100, false));

            var goldens = synthesizer.generateGoldensFromDocs(
                    List.of(document),
                    false,
                    1,
                    new ContextConstructionConfig(1, 1, 50, 0, 0.5, 0.0, 3));

            assertTrue(goldens.getFirst().context().getFirst().contains("| A | B |\n| - | - |\n| 1 | 2 |"));
            assertTrue(model.prompts().getFirst().contains("| A | B |\n| - | - |\n| 1 | 2 |"));
        }
    }

    @Test
    void generateGoldensFromDocsRejectsUnsupportedFileExtensionLikeDeepEval() throws Exception {
        var document = tempDir.resolve("policy.xyz");
        Files.writeString(document, "unsupported");
        var synthesizer = new Synthesizer(
                new ScriptedModel(List.of()),
                null,
                null,
                noEvolutionConfig(),
                noFiltrationConfig(),
                new SynthesizerOptions(false, 100, false));

        var error = assertThrows(IllegalArgumentException.class, () -> synthesizer.generateGoldensFromDocs(
                List.of(document),
                false,
                1,
                new ContextConstructionConfig(1, 1, 10, 0, 0.5, 0.0, 3)));

        assertEquals("Unsupported file format: .xyz", error.getMessage());
    }

    @Test
    void generateGoldensFromDocsRejectsMissingDocumentPathLikeDeepEval() {
        var document = tempDir.resolve("missing.md");
        var synthesizer = new Synthesizer(
                new ScriptedModel(List.of()),
                null,
                null,
                noEvolutionConfig(),
                noFiltrationConfig(),
                new SynthesizerOptions(false, 100, false));

        var error = assertThrows(IllegalArgumentException.class, () -> synthesizer.generateGoldensFromDocs(
                List.of(document),
                false,
                1,
                new ContextConstructionConfig(1, 1, 10, 0, 0.5, 0.0, 3)));

        assertEquals("Document path not found: " + document, error.getMessage());
    }

    @Test
    void generateGoldensFromDocsRejectsEmptyDocumentPathsLikeDeepEval() {
        var synthesizer = new Synthesizer(
                new ScriptedModel(List.of()),
                null,
                null,
                noEvolutionConfig(),
                noFiltrationConfig(),
                new SynthesizerOptions(false, 100, false));

        var singleTurnError = assertThrows(IllegalArgumentException.class, () -> synthesizer.generateGoldensFromDocs(
                List.of(),
                false,
                1,
                new ContextConstructionConfig(1, 1, 10, 0, 0.5, 0.0, 3)));
        assertEquals("document_paths must not be empty", singleTurnError.getMessage());

        var multiTurnError = assertThrows(IllegalArgumentException.class,
                () -> synthesizer.generateConversationalGoldensFromDocs(
                        List.of(),
                        false,
                        1,
                        new ContextConstructionConfig(1, 1, 10, 0, 0.5, 0.0, 3)));
        assertEquals("document_paths must not be empty", multiTurnError.getMessage());
    }

    @Test
    void generateGoldensFromDocsContinuesWhenOneDocumentCannotMeetMinimumContextsLikeDeepEval()
            throws Exception {
        var tooSmall = tempDir.resolve("tiny.md");
        var good = tempDir.resolve("good.md");
        Files.writeString(tooSmall, "short");
        Files.writeString(good, "alpha beta gamma delta");
        var model = new ScriptedModel(List.of(
                "{\"data\":[{\"input\":\"What is in the good doc?\"}]}",
                "{\"data\":[{\"input\":\"What else is in the good doc?\"}]}"));
        var synthesizer = new Synthesizer(
                model,
                null,
                null,
                noEvolutionConfig(),
                noFiltrationConfig(),
                new SynthesizerOptions(false, 100, false));

        var goldens = synthesizer.generateGoldensFromDocs(
                List.of(tooSmall, good),
                false,
                1,
                new ContextConstructionConfig(2, 2, 2, 0, 0.5, 0.0, 3));

        assertEquals(List.of("What is in the good doc?", "What else is in the good doc?"),
                goldens.stream().map(Golden::input).toList());
        assertEquals(List.of(List.of("alpha beta"), List.of("gamma delta")),
                goldens.stream().map(Golden::context).toList());
        assertEquals(good.toString(), goldens.getFirst().sourceFile());
        assertTrue(model.prompts().getFirst().contains("alpha beta"));
        assertTrue(model.prompts().get(1).contains("gamma delta"));
        assertEquals(false, model.prompts().getFirst().contains("short"));
    }

    @Test
    void generateGoldensFromDocsCanMergeCrossFileContextsLikeDeepEval() throws Exception {
        var policy = tempDir.resolve("policy.md");
        var faq = tempDir.resolve("faq.md");
        Files.writeString(policy, "policy chunk");
        Files.writeString(faq, "faq chunk");
        var model = new ScriptedModel(List.of(
                """
                {"data":[{"input":"How do policy and FAQ connect?","used_source_files":["%s","%s"]}]}
                """.formatted(jsonString(policy), jsonString(faq))));
        var synthesizer = new Synthesizer(
                model,
                null,
                null,
                noEvolutionConfig(),
                noFiltrationConfig(),
                new SynthesizerOptions(false, 100, false));

        var goldens = synthesizer.generateGoldensFromDocs(
                List.of(policy, faq),
                false,
                1,
                new ContextConstructionConfig(1, 1, 10, 0, 0.5, 0.0, 3, true, 2, 3));

        assertEquals(1, goldens.size());
        assertEquals(List.of("policy chunk", "faq chunk"), goldens.getFirst().context());
        assertEquals(policy.toString(), goldens.getFirst().sourceFile());
        assertEquals(List.of(policy.toString(), faq.toString()),
                goldens.getFirst().additionalMetadata().get("context_source_files"));
        assertEquals(List.of(policy.toString(), faq.toString()),
                goldens.getFirst().additionalMetadata().get("used_source_files"));
        assertTrue(model.prompts().getFirst().contains("[SOURCE: " + policy + "] policy chunk"));
        assertTrue(model.prompts().getFirst().contains("[SOURCE: " + faq + "] faq chunk"));
        assertTrue(model.prompts().getFirst().contains("at least 2 different source files"));
    }

    @Test
    void saveAsWritesLastSyntheticGoldensLikeDeepEval() throws Exception {
        var model = new ScriptedModel(List.of(
                "{\"data\":[{\"input\":\"What is France's capital?\",\"expected_output\":\"Paris\"}]}",
                "Paris"));
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
                "{\"data\":[{\"input\":\"What is France's capital?\",\"expected_output\":\"Paris\"}]}",
                "Paris"));
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
                "Outcome one",
                """
                {"data":[{"scenario":"Scenario two","turns":[{"role":"user","content":"Question two?"}],"expected_outcome":"Outcome two"}]}
                """,
                "Outcome two"));
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
    void generateConversationalGoldensFromDocsCanMergeCrossFileContextsLikeDeepEval() throws Exception {
        var policy = tempDir.resolve("policy.md");
        var faq = tempDir.resolve("faq.md");
        Files.writeString(policy, "policy chunk");
        Files.writeString(faq, "faq chunk");
        var model = new ScriptedModel(List.of(
                """
                {"data":[{"scenario":"policy and FAQ support","turns":[{"role":"user","content":"Need both"}],"used_source_files":["%s","%s"]}]}
                """.formatted(jsonString(policy), jsonString(faq))));
        var synthesizer = new Synthesizer(
                model,
                null,
                null,
                noEvolutionConfig(),
                noFiltrationConfig(),
                new SynthesizerOptions(false, 100, false));

        var goldens = synthesizer.generateConversationalGoldensFromDocs(
                List.of(policy, faq),
                false,
                1,
                new ContextConstructionConfig(1, 1, 10, 0, 0.5, 0.0, 3, true, 2, 3));

        assertEquals(1, goldens.size());
        assertEquals(List.of("policy chunk", "faq chunk"), goldens.getFirst().context());
        assertEquals(List.of(policy.toString(), faq.toString()),
                goldens.getFirst().additionalMetadata().get("context_source_files"));
        assertEquals(List.of(policy.toString(), faq.toString()),
                goldens.getFirst().additionalMetadata().get("used_source_files"));
        assertTrue(model.prompts().getFirst().contains("[SOURCE: " + policy + "] policy chunk"));
        assertTrue(model.prompts().getFirst().contains("[SOURCE: " + faq + "] faq chunk"));
        assertTrue(model.prompts().getFirst().contains("at least 2 different source files"));
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
    void asyncGenerateGoldensFromContextsReturnsFutureLikeDeepEval() {
        var model = new ScriptedModel(List.of(
                "{\"data\":[{\"input\":\"Async context question?\"}]}"));
        var synthesizer = new Synthesizer(
                model, null, null, noEvolutionConfig(), noFiltrationConfig(), SynthesizerOptions.DEFAULT);

        var goldens = synthesizer.generateGoldensFromContextsAsync(
                List.of(List.of("async context")), false, 1, null).join();

        assertEquals(List.of("Async context question?"), goldens.stream().map(Golden::input).toList());
    }

    @Test
    void asyncGenerateGoldensFromScratchReturnsFutureLikeDeepEval() {
        var model = new ScriptedModel(List.of(
                "{\"data\":[{\"input\":\"async scratch\"}]}",
                "{\"input\":\"async scratch\"}"));
        var synthesizer = new Synthesizer(
                model,
                new StylingConfig("students learning geography", "ask study questions", "one question", null),
                null,
                noEvolutionConfig(),
                noFiltrationConfig(),
                SynthesizerOptions.DEFAULT);

        var goldens = synthesizer.generateGoldensFromScratchAsync(1).join();

        assertEquals(List.of("async scratch"), goldens.stream().map(Golden::input).toList());
    }

    @Test
    void asyncGenerateGoldensFromDocsReturnsFutureLikeDeepEval() throws Exception {
        var document = tempDir.resolve("async-policy.md");
        Files.writeString(document, "alpha beta");
        var model = new ScriptedModel(List.of(
                "{\"data\":[{\"input\":\"Async doc question?\"}]}"));
        var synthesizer = new Synthesizer(
                model,
                null,
                null,
                noEvolutionConfig(),
                noFiltrationConfig(),
                new SynthesizerOptions(false, 100, false));

        var goldens = synthesizer.generateGoldensFromDocsAsync(
                List.of(document),
                false,
                1,
                new ContextConstructionConfig(1, 1, 2, 0, 0.5, 0.0, 3)).join();

        assertEquals(List.of("Async doc question?"), goldens.stream().map(Golden::input).toList());
        assertEquals(List.of(document.toString()), goldens.stream().map(Golden::sourceFile).toList());
    }

    @Test
    void generatesGoldensFromScratchAndKeepsPerGoldenEvolutionMetadata() {
        var model = new ScriptedModel(List.of(
                "{\"data\":[{\"input\":\"first\"},{\"input\":\"second\"}]}",
                "{\"rewritten_input\":\"first evolved\"}",
                "{\"input\":\"first evolved\"}",
                "{\"rewritten_input\":\"second evolved\"}",
                "{\"input\":\"second evolved\"}"));
        var synthesizer = new Synthesizer(
                model,
                new StylingConfig("students learning geography", "ask study questions", "one question", null),
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
    void rejectsNonPositiveGenerationCountsLikeDeepEval() {
        var synthesizer = new Synthesizer(
                new ScriptedModel(List.of(
                        "{\"data\":[{\"input\":\"unused\"}]}",
                        "{\"input\":\"unused\"}")),
                new StylingConfig("students learning geography", "ask study questions", "one question", null),
                new ConversationalStylingConfig("travel support", "book flights", "traveler and agent", null),
                noEvolutionConfig(),
                noFiltrationConfig(),
                SynthesizerOptions.DEFAULT);

        var scratchError = assertThrows(IllegalArgumentException.class,
                () -> synthesizer.generateGoldensFromScratch(0));
        assertEquals("num_goldens must be at least 1", scratchError.getMessage());

        var contextError = assertThrows(IllegalArgumentException.class,
                () -> synthesizer.generateGoldensFromContexts(List.of(List.of("context")), false, 0, null));
        assertEquals("max_goldens_per_context must be at least 1", contextError.getMessage());

        var conversationalError = assertThrows(IllegalArgumentException.class,
                () -> synthesizer.generateConversationalGoldensFromScratch(0));
        assertEquals("num_goldens must be at least 1", conversationalError.getMessage());
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
    void rewritesEvolvedInputsWhenScenarioStylingIsSetLikeDeepEval() {
        var model = new ScriptedModel(List.of(
                "{\"data\":[{\"input\":\"raw context question\"}]}",
                "{\"rewritten_input\":\"evolved context question\"}",
                "{\"input\":\"scenario-styled context question\"}"));
        var synthesizer = new Synthesizer(
                model,
                new StylingConfig("students learning geography", null, null, null),
                null,
                new EvolutionConfig(1, List.of(Evolution.REASONING)),
                noFiltrationConfig(),
                SynthesizerOptions.DEFAULT);

        var goldens = synthesizer.generateGoldensFromContexts(List.of(List.of("Paris is in France.")), false, 1, null);

        assertEquals("scenario-styled context question", goldens.getFirst().input());
        assertTrue(model.prompts().get(2).contains("Scenario: students learning geography"));
        assertTrue(model.prompts().get(2).contains("Evolved Input:"));
        assertTrue(model.prompts().get(2).contains("evolved context question"));
        assertEquals(3, model.prompts().size());
    }

    @Test
    void scratchGenerationRequiresStylingConfig() {
        var synthesizer = new Synthesizer(prompt -> "{}");

        var error = assertThrows(IllegalStateException.class, () -> synthesizer.generateGoldensFromScratch(1));

        assertEquals(
                "`scenario`, `task`, and `input_format` in `styling_config` must not be None when generation goldens from scratch.",
                error.getMessage());
    }

    @Test
    void scratchGenerationRequiresAllStylingFieldsLikeDeepEval() {
        var synthesizer = new Synthesizer(
                prompt -> "{}",
                new StylingConfig("students learning geography", "ask study questions", null, null),
                null,
                noEvolutionConfig(),
                noFiltrationConfig(),
                SynthesizerOptions.DEFAULT);

        var error = assertThrows(IllegalStateException.class, () -> synthesizer.generateGoldensFromScratch(1));

        assertEquals(
                "`scenario`, `task`, and `input_format` in `styling_config` must not be None when generation goldens from scratch.",
                error.getMessage());
    }

    @Test
    void generatesGoldensFromExistingGoldensWithContext() {
        var model = new ScriptedModel(List.of(
                "{\"scenario\":\"database class\",\"task\":\"ask SQL study questions\",\"input_format\":\"one question\"}",
                "{\"data\":[{\"input\":\"What does the table store?\"}]}",
                "{\"rewritten_input\":\"What data is stored in the table?\"}",
                "{\"input\":\"What data is stored in the table?\"}"));
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
        assertTrue(model.prompts().getFirst().contains("infer the common prompt structure"));
    }

    @Test
    void generatesGoldensFromContextGoldensWithInferredStylingLikeDeepEval() {
        var model = new ScriptedModel(List.of(
                "{\"scenario\":\"database class\",\"task\":\"ask SQL study questions\",\"input_format\":\"one question\"}",
                "{\"data\":[{\"input\":\"raw context question\"}]}",
                "{\"input\":\"styled context question\"}"));
        var synthesizer = new Synthesizer(
                model, null, null, noEvolutionConfig(), noFiltrationConfig(), SynthesizerOptions.DEFAULT);
        var original = Golden.builder("How is the students table structured?")
                .context(List.of("CREATE TABLE students (id INT)"))
                .sourceFile("schema.sql")
                .build();

        var goldens = synthesizer.generateGoldensFromGoldens(List.of(original), 1, false);

        assertEquals(1, goldens.size());
        assertEquals("styled context question", goldens.getFirst().input());
        assertEquals(List.of("CREATE TABLE students (id INT)"), goldens.getFirst().context());
        assertEquals("schema.sql", goldens.getFirst().sourceFile());
        assertTrue(model.prompts().getFirst().contains("infer the common prompt structure"));
        assertTrue(model.prompts().get(2).contains("Input Format: one question"));
    }

    @Test
    void generateGoldensFromGoldensTreatsExplicitEmptyContextAsContextLikeDeepEval() {
        var model = new ScriptedModel(List.of(
                "{\"scenario\":\"database class\",\"task\":\"ask SQL study questions\",\"input_format\":\"one question\"}",
                "{\"data\":[{\"input\":\"raw empty-context question\"}]}",
                "{\"input\":\"styled empty-context question\"}"));
        var synthesizer = new Synthesizer(
                model, null, null, noEvolutionConfig(), noFiltrationConfig(), SynthesizerOptions.DEFAULT);
        var original = Golden.builder("How is the empty context handled?")
                .context(List.of())
                .sourceFile("empty.txt")
                .build();

        var goldens = synthesizer.generateGoldensFromGoldens(List.of(original), 1, false);

        assertEquals("styled empty-context question", goldens.getFirst().input());
        assertEquals(List.of(), goldens.getFirst().context());
        assertEquals("empty.txt", goldens.getFirst().sourceFile());
        assertTrue(model.prompts().get(1).contains("Context:"));
        assertEquals(false, model.prompts().get(1).contains("Generate 1 synthetic user inputs for this scenario"));
    }

    @Test
    void generatesGoldensFromExistingGoldensWithoutStylingConfig() {
        var model = new ScriptedModel(List.of(
                "{\"scenario\":\"students learning geography\",\"task\":\"ask study questions\",\"input_format\":\"one question\"}",
                "{\"data\":[{\"input\":\"Extracted-style question?\"}]}",
                "{\"input\":\"Extracted-style question?\"}"));
        var synthesizer = new Synthesizer(
                model, null, null, noEvolutionConfig(), noFiltrationConfig(), SynthesizerOptions.DEFAULT);
        var original = Golden.builder("What is the capital of France?")
                .expectedOutput("Paris")
                .build();

        var goldens = synthesizer.generateGoldensFromGoldens(List.of(original), 1, false);

        assertEquals(1, goldens.size());
        assertEquals("Extracted-style question?", goldens.getFirst().input());
        assertEquals(null, goldens.getFirst().expectedOutput());
        assertTrue(model.prompts().getFirst().contains("infer the common prompt structure"));
        assertTrue(model.prompts().get(1).contains("Generate 1 synthetic user inputs for this scenario"));
    }

    @Test
    void generateGoldensFromPlainGoldensUsesScratchBranchWhenStyledLikeDeepEval() {
        var model = new ScriptedModel(List.of(
                "{\"data\":[{\"input\":\"Scratch-style question?\"}]}",
                "{\"input\":\"Scratch-style question?\"}"));
        var synthesizer = new Synthesizer(
                model,
                new StylingConfig("students learning geography", "ask study questions", "one question", null),
                null,
                noEvolutionConfig(),
                noFiltrationConfig(),
                SynthesizerOptions.DEFAULT);
        var original = Golden.builder("What is the capital of France?").build();

        var goldens = synthesizer.generateGoldensFromGoldens(List.of(original), 2, false);

        assertEquals(List.of("Scratch-style question?"), goldens.stream().map(Golden::input).toList());
        assertTrue(model.prompts().getFirst().contains("Generate 2 synthetic user inputs for this scenario"));
        assertTrue(model.prompts().getFirst().contains("students learning geography"));
        assertEquals(false, model.prompts().getFirst().contains("similar in style and domain"));
    }

    @Test
    void generateGoldensFromMixedExistingGoldensUsesContextBranchLikeDeepEval() {
        var model = new ScriptedModel(List.of(
                "{\"scenario\":\"mixed class\",\"task\":\"ask context questions\",\"input_format\":\"one question\"}",
                "{\"data\":[{\"input\":\"Context question\"}]}",
                "{\"input\":\"Styled context question\"}",
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
        assertEquals("Styled context question", goldens.get(0).input());
        assertEquals(List.of("context"), goldens.get(0).context());
        assertEquals("Context answer", goldens.get(0).expectedOutput());
        assertTrue(model.prompts().getFirst().contains("infer the common prompt structure"));
        assertEquals(4, model.prompts().size());
    }

    @Test
    void asyncGenerateGoldensFromExistingGoldensReturnsFutureLikeDeepEval() {
        var model = new ScriptedModel(List.of(
                "{\"scenario\":\"async class\",\"task\":\"ask async questions\",\"input_format\":\"one question\"}",
                "{\"data\":[{\"input\":\"Async expanded question?\"}]}",
                "{\"input\":\"Styled async expanded question?\"}"));
        var synthesizer = new Synthesizer(
                model, null, null, noEvolutionConfig(), noFiltrationConfig(), SynthesizerOptions.DEFAULT);
        var original = Golden.builder("old")
                .context(List.of("Async context"))
                .sourceFile("async.txt")
                .build();

        var goldens = synthesizer.generateGoldensFromGoldensAsync(List.of(original), 1, false).join();

        assertEquals(List.of("Styled async expanded question?"), goldens.stream().map(Golden::input).toList());
        assertEquals(List.of("Async context"), goldens.getFirst().context());
        assertEquals("async.txt", goldens.getFirst().sourceFile());
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
    void asyncGenerateConversationalGoldensFromContextsReturnsFutureLikeDeepEval() {
        var model = new ScriptedModel(List.of(
                """
                {"data":[{"scenario":"async refund help","turns":[
                  {"role":"user","content":"Can I get a refund?"}
                ]}]}
                """));
        var synthesizer = new Synthesizer(
                model, null, null, noEvolutionConfig(), noFiltrationConfig(), SynthesizerOptions.DEFAULT);

        var goldens = synthesizer.generateConversationalGoldensFromContextsAsync(
                List.of(List.of("Refunds are available within 30 days.")), false, 1, null).join();

        assertEquals(List.of("async refund help"),
                goldens.stream().map(ConversationalGolden::scenario).toList());
        assertEquals("user", goldens.getFirst().turns().getFirst().role());
    }

    @Test
    void asyncGenerateConversationalGoldensFromScratchReturnsFutureLikeDeepEval() {
        var model = new ScriptedModel(List.of(
                """
                {"data":[{"scenario":"async flight booking","turns":[
                  {"role":"user","content":"Book a flight"}
                ]}]}
                """,
                "{\"scenario\":\"async flight booking\"}"));
        var synthesizer = new Synthesizer(
                model,
                null,
                new ConversationalStylingConfig("travel support", "book flights", "traveler and agent", null),
                noEvolutionConfig(),
                noFiltrationConfig(),
                SynthesizerOptions.DEFAULT);

        var goldens = synthesizer.generateConversationalGoldensFromScratchAsync(1).join();

        assertEquals(List.of("async flight booking"),
                goldens.stream().map(ConversationalGolden::scenario).toList());
        assertEquals("Book a flight", goldens.getFirst().turns().getFirst().content());
    }

    @Test
    void asyncGenerateConversationalGoldensFromDocsReturnsFutureLikeDeepEval() throws Exception {
        var document = tempDir.resolve("async-conversation-policy.md");
        Files.writeString(document, "refund policy");
        var model = new ScriptedModel(List.of(
                """
                {"data":[{"scenario":"async doc refund","turns":[
                  {"role":"user","content":"Refund question"}
                ]}]}
                """));
        var synthesizer = new Synthesizer(
                model,
                null,
                null,
                noEvolutionConfig(),
                noFiltrationConfig(),
                new SynthesizerOptions(false, 100, false));

        var goldens = synthesizer.generateConversationalGoldensFromDocsAsync(
                List.of(document),
                false,
                1,
                new ContextConstructionConfig(1, 1, 2, 0, 0.5, 0.0, 3)).join();

        assertEquals(List.of("async doc refund"),
                goldens.stream().map(ConversationalGolden::scenario).toList());
        assertEquals(List.of(List.of("refund policy")),
                goldens.stream().map(ConversationalGolden::context).toList());
    }

    @Test
    void generatesConversationalExpectedOutcomeSeparatelyLikeDeepEval() {
        var model = new ScriptedModel(List.of(
                """
                {"data":[{"scenario":"user asks about refund","turns":[
                  {"role":"user","content":"Can I get a refund?"}
                ],"expected_outcome":"Embedded outcome should be ignored"}]}
                """,
                "Generated outcome from context"));
        var synthesizer = new Synthesizer(
                model, null, null, noEvolutionConfig(), noFiltrationConfig(), SynthesizerOptions.DEFAULT);

        var goldens = synthesizer.generateConversationalGoldensFromContexts(
                List.of(List.of("Refunds are available within 30 days.")), true, 1, null);

        assertEquals("Generated outcome from context", goldens.getFirst().expectedOutcome());
        assertTrue(model.prompts().get(1).contains("Generate the expected outcome"));
        assertTrue(model.prompts().get(1).contains("user asks about refund"));
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
    void generatesConversationalGoldensWithSeparateChunkSourceFilesLikeDeepEval() {
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
                List.of(List.of("Policy text", "FAQ text")),
                false,
                1,
                sourceFiles,
                List.of(List.of("policy.md", "faq.md")),
                null);

        assertEquals(List.of("policy.md", "faq.md"),
                goldens.getFirst().additionalMetadata().get("context_source_files"));
        assertTrue(model.prompts().getFirst().contains("[SOURCE: policy.md] Policy text"));
        assertTrue(model.prompts().getFirst().contains("[SOURCE: faq.md] FAQ text"));
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
    void rewritesEvolvedConversationalScenariosToStyleLikeDeepEval() {
        var model = new ScriptedModel(List.of(
                """
                {"data":[{"scenario":"raw refund scenario","turns":[
                  {"role":"user","content":"Can I get a refund?"},
                  {"role":"assistant","content":"I can help."}
                ]}]}
                """,
                "{\"rewritten_scenario\":\"evolved refund scenario\"}",
                "{\"scenario\":\"styled refund scenario\"}"));
        var synthesizer = new Synthesizer(
                model,
                null,
                new ConversationalStylingConfig("support desk", null, null, null),
                new EvolutionConfig(1, List.of(Evolution.REASONING)),
                noFiltrationConfig(),
                SynthesizerOptions.DEFAULT);

        var goldens = synthesizer.generateConversationalGoldensFromContexts(
                List.of(List.of("Refunds are available within 30 days.")), false, 1, null);

        assertEquals("styled refund scenario", goldens.getFirst().scenario());
        assertTrue(model.prompts().get(2).contains("Scenario Context: support desk"));
        assertTrue(model.prompts().get(2).contains("Evolved Scenario:"));
        assertTrue(model.prompts().get(2).contains("evolved refund scenario"));
        assertEquals(3, model.prompts().size());
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
                """,
                "{\"scenario\":\"traveler books a flight\"}"));
        var synthesizer = new Synthesizer(
                model,
                null,
                new ConversationalStylingConfig("travel support", "book flights", "traveler and agent", null),
                noEvolutionConfig(),
                noFiltrationConfig(),
                SynthesizerOptions.DEFAULT);

        var goldens = synthesizer.generateConversationalGoldensFromScratch(1);

        assertEquals(List.of("traveler books a flight"), goldens.stream().map(ConversationalGolden::scenario).toList());
        assertEquals(null, goldens.getFirst().expectedOutcome());
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
                "{\"scenario\":\"first evolved\"}",
                "{\"rewritten_scenario\":\"second evolved\"}",
                "{\"scenario\":\"second evolved\"}"));
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
        assertTrue(model.prompts().get(3).contains("Comparative"));
    }

    @Test
    void conversationalScratchRequiresStylingConfig() {
        var synthesizer = new Synthesizer(prompt -> "{}");

        var error = assertThrows(IllegalStateException.class,
                () -> synthesizer.generateConversationalGoldensFromScratch(1));

        assertEquals(
                "`scenario_context`, `conversational_task`, and `participant_roles` in `conversational_styling_config` must not be None when generating conversational goldens from scratch.",
                error.getMessage());
    }

    @Test
    void generatesConversationalGoldensFromExistingGoldensWithContext() {
        var model = new ScriptedModel(List.of(
                """
                {"scenario_context":"refund support","conversational_task":"explain refund eligibility","participant_roles":"customer and support agent"}
                """,
                """
                {"data":[{"scenario":"refund follow up","turns":[
                  {"role":"user","content":"Can I still get a refund?"},
                  {"role":"assistant","content":"Yes, within 30 days."}
                ],"expected_outcome":"Refund eligibility explained"}]}
                """,
                "{\"scenario\":\"styled refund follow up\"}",
                "Refund eligibility explained"));
        var synthesizer = new Synthesizer(
                model, null, null, noEvolutionConfig(), noFiltrationConfig(), SynthesizerOptions.DEFAULT);
        var original = ConversationalGolden.builder("old refund scenario")
                .context(List.of("Refunds are available within 30 days."))
                .build();

        var goldens = synthesizer.generateConversationalGoldensFromGoldens(List.of(original), 1, true);

        assertEquals(1, goldens.size());
        assertEquals("styled refund follow up", goldens.getFirst().scenario());
        assertEquals(List.of("Refunds are available within 30 days."), goldens.getFirst().context());
        assertEquals("Refund eligibility explained", goldens.getFirst().expectedOutcome());
        assertTrue(model.prompts().getFirst().contains("extract the common structural elements"));
    }

    @Test
    void generatesConversationalContextGoldensWithInferredStylingLikeDeepEval() {
        var model = new ScriptedModel(List.of(
                """
                {"scenario_context":"support desk","conversational_task":"resolve refund issues","participant_roles":"customer and support agent"}
                """,
                """
                {"data":[{"scenario":"raw refund scenario","turns":[
                  {"role":"user","content":"Can I still get a refund?"}
                ]}]}
                """,
                "{\"scenario\":\"styled refund scenario\"}"));
        var synthesizer = new Synthesizer(
                model, null, null, noEvolutionConfig(), noFiltrationConfig(), SynthesizerOptions.DEFAULT);
        var original = ConversationalGolden.builder("customer asks about refund timing")
                .context(List.of("Refunds are available within 30 days."))
                .build();

        var goldens = synthesizer.generateConversationalGoldensFromGoldens(List.of(original), 1, false);

        assertEquals(1, goldens.size());
        assertEquals("styled refund scenario", goldens.getFirst().scenario());
        assertEquals(List.of("Refunds are available within 30 days."), goldens.getFirst().context());
        assertTrue(model.prompts().getFirst().contains("extract the common structural elements"));
        assertTrue(model.prompts().get(2).contains("Participant Roles: customer and support agent"));
    }

    @Test
    void generateConversationalGoldensFromGoldensTreatsExplicitEmptyContextAsContextLikeDeepEval() {
        var model = new ScriptedModel(List.of(
                """
                {"scenario_context":"support desk","conversational_task":"resolve refund issues","participant_roles":"customer and support agent"}
                """,
                """
                {"data":[{"scenario":"raw empty-context scenario","turns":[
                  {"role":"user","content":"Can I still get a refund?"}
                ]}]}
                """,
                "{\"scenario\":\"styled empty-context scenario\"}"));
        var synthesizer = new Synthesizer(
                model, null, null, noEvolutionConfig(), noFiltrationConfig(), SynthesizerOptions.DEFAULT);
        var original = ConversationalGolden.builder("customer asks about refund timing")
                .context(List.of())
                .build();

        var goldens = synthesizer.generateConversationalGoldensFromGoldens(List.of(original), 1, false);

        assertEquals("styled empty-context scenario", goldens.getFirst().scenario());
        assertEquals(List.of(), goldens.getFirst().context());
        assertTrue(model.prompts().get(1).contains("Context:"));
        assertEquals(false, model.prompts().get(1).contains("Generate 1 synthetic multi-turn conversation scenarios."));
    }

    @Test
    void generatesConversationalGoldensFromExistingGoldensWithoutContext() {
        var model = new ScriptedModel(List.of(
                """
                {"scenario_context":"travel support","conversational_task":"book flights","participant_roles":"traveler and agent"}
                """,
                """
                {"data":[{"scenario":"flight change request","turns":[
                  {"role":"user","content":"Change my flight"},
                  {"role":"assistant","content":"I can help with that."}
                ]}]}
                """,
                "{\"scenario\":\"styled flight change request\"}"));
        var synthesizer = new Synthesizer(
                model, null, null, noEvolutionConfig(), noFiltrationConfig(), SynthesizerOptions.DEFAULT);
        var original = ConversationalGolden.builder("traveler wants to rebook a flight").build();

        var goldens = synthesizer.generateConversationalGoldensFromGoldens(List.of(original), 1, false);

        assertEquals(1, goldens.size());
        assertEquals("styled flight change request", goldens.getFirst().scenario());
        assertEquals("user", goldens.getFirst().turns().getFirst().role());
        assertTrue(model.prompts().getFirst().contains("traveler wants to rebook a flight"));
        assertTrue(model.prompts().get(1).contains("Generate 1 synthetic multi-turn conversation scenarios"));
        assertEquals(false, model.prompts().get(1).contains("similar in style and domain"));
    }

    @Test
    void generateConversationalGoldensFromMixedExistingGoldensUsesContextBranchLikeDeepEval() {
        var model = new ScriptedModel(List.of(
                """
                {"scenario_context":"mixed support","conversational_task":"answer context questions","participant_roles":"user and assistant"}
                """,
                """
                {"data":[{"scenario":"context scenario","turns":[
                  {"role":"user","content":"Ask about context"}
                ],"expected_outcome":"Context explained"}]}
                """,
                "{\"scenario\":\"styled context scenario\"}",
                "Context explained"));
        var synthesizer = new Synthesizer(
                model, null, null, noEvolutionConfig(), noFiltrationConfig(), SynthesizerOptions.DEFAULT);
        var contextual = ConversationalGolden.builder("old context")
                .context(List.of("context"))
                .build();
        var plain = ConversationalGolden.builder("old plain").build();

        var goldens = synthesizer.generateConversationalGoldensFromGoldens(List.of(contextual, plain), 1, true);

        assertEquals(1, goldens.size());
        assertEquals("styled context scenario", goldens.getFirst().scenario());
        assertEquals(List.of("context"), goldens.getFirst().context());
        assertEquals("Context explained", goldens.getFirst().expectedOutcome());
        assertTrue(model.prompts().getFirst().contains("extract the common structural elements"));
        assertEquals(4, model.prompts().size());
    }

    @Test
    void asyncGenerateConversationalGoldensFromExistingGoldensReturnsFutureLikeDeepEval() {
        var model = new ScriptedModel(List.of(
                """
                {"scenario_context":"async support","conversational_task":"answer async questions","participant_roles":"user and assistant"}
                """,
                """
                {"data":[{"scenario":"async conversational expansion","turns":[
                  {"role":"user","content":"Async question"}
                ]}]}
                """,
                "{\"scenario\":\"styled async conversational expansion\"}"));
        var synthesizer = new Synthesizer(
                model, null, null, noEvolutionConfig(), noFiltrationConfig(), SynthesizerOptions.DEFAULT);
        var original = ConversationalGolden.builder("old async scenario").build();

        var goldens = synthesizer.generateConversationalGoldensFromGoldensAsync(
                List.of(original), 1, false).join();

        assertEquals(List.of("styled async conversational expansion"),
                goldens.stream().map(ConversationalGolden::scenario).toList());
        assertEquals("Async question", goldens.getFirst().turns().getFirst().content());
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

    private static String jsonString(Path path) {
        return path.toString().replace("\\", "\\\\");
    }

    private static void writePdf(Path path, String text) throws Exception {
        try (var document = new PDDocument()) {
            var page = new PDPage();
            document.addPage(page);
            try (var content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);
                content.showText(text);
                content.endText();
            }
            document.save(path.toFile());
        }
    }

    private static void writeDocx(Path path, String text) throws Exception {
        try (var document = new XWPFDocument();
                var out = Files.newOutputStream(path)) {
            document.createParagraph().createRun().setText(text);
            document.write(out);
        }
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
