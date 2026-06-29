# JEval

Modern Java port of [DeepEval](https://github.com/confident-ai/deepeval).

## Status

Port source: DeepEval `8ebfa33d78db4cf81c0ae340b1a925e5406469c8`.

Implemented:

- Core single-turn, conversational, golden, arena, tool-call, MCP, multimodal-placeholder, prompt, and dataset value types
- `EvaluationDataset` JSON/JSONL/CSV import and export for single-turn and conversational goldens/test cases
- `Evaluator.evaluate(...)`, `Evaluator.evaluateConversations(...)`, arena evaluation, and assertion helpers
- Deterministic metrics: exact match, pattern match, JSON correctness, tool correctness, and DAG verdict paths
- Model-backed metric shells and prompt/schema parsing paths for the ported DeepEval single-turn, conversational, arena, MCP, trace, and DAG metrics
- Shared DeepEval-style validation and formatting utilities

See `docs/deepeval-parity-plan.md` for the detailed parity ledger.

## Build

Requires JDK 25.

```powershell
mvn test
```

If Maven is launched with an older Java runtime, configure a JDK 25 Maven toolchain or set `JAVA_HOME` to JDK 25 before running Maven.

## CLI test runner

Package the jar, then run JSON evaluations with the `test` command:

```powershell
mvn -q -DskipTests package
java -jar target/jeval-0.1.0-SNAPSHOT.jar test path\to\eval.json --format markdown --output reports
```

Evaluation files can use any file name and contain a name, metrics, and cases:

```json
{
  "name": "answer-check",
  "metrics": [{"type": "exact_match"}],
  "cases": [
    {"name": "case-1", "input": "question", "actualOutput": "answer", "expectedOutput": "answer"}
  ]
}
```

Supported formats are `markdown` and `html`. Results are also written to `.jeval/.jeval`.

Specs can also point at JSON, JSONL, or CSV datasets:

```json
{
  "name": "answer-check",
  "dataset": "cases.jsonl",
  "metrics": [{"type": "exact_match"}]
}
```

## CLI settings and providers

Settings and provider commands persist DeepEval-style environment keys to dotenv files:

```powershell
java -jar target/jeval-0.1.0-SNAPSHOT.jar settings -u log-level=error --save dotenv:.env
java -jar target/jeval-0.1.0-SNAPSHOT.jar set-openai --model gpt-4o-mini --save dotenv:.env
java -jar target/jeval-0.1.0-SNAPSHOT.jar unset-openai --save dotenv:.env
```

OpenAI and Ollama provider settings can be used by `generate`; other provider
commands currently persist configuration only.

## CLI generate

The `generate` command supports single-turn and multi-turn `contexts`, `docs`,
`scratch`, and `goldens` generation through JEval's synthesizer. It can use OpenAI/Ollama settings saved with provider
commands, or deterministic scripted responses with `--responses-file`:

```powershell
java -jar target/jeval-0.1.0-SNAPSHOT.jar set-openai --model gpt-4o-mini --save dotenv:.env
java -jar target/jeval-0.1.0-SNAPSHOT.jar settings -u openai-api-key=$env:OPENAI_API_KEY --save dotenv:.env
java -jar target/jeval-0.1.0-SNAPSHOT.jar generate --method contexts --variation single-turn --contexts-file contexts.json --save dotenv:.env --output-dir generated
java -jar target/jeval-0.1.0-SNAPSHOT.jar generate --method docs --variation single-turn --document-path docs\knowledge.md --chunk-size 200 --save dotenv:.env --output-dir generated
java -jar target/jeval-0.1.0-SNAPSHOT.jar generate --method contexts --variation multi-turn --contexts-file contexts.json --save dotenv:.env --output-dir generated
java -jar target/jeval-0.1.0-SNAPSHOT.jar generate --method contexts --variation single-turn --contexts-file contexts.json --responses-file responses.txt --output-dir generated
```

## Synthesizer

The synthesizer can generate single-turn `Golden` values from contexts, scratch
styling, or existing goldens, and multi-turn `ConversationalGolden` values from
contexts, scratch styling, or existing conversational goldens. It uses any `EvaluationModel`, including the
LangChain4j adapter.

```java
import dev.jeval.Golden;
import dev.jeval.synthesizer.Synthesizer;
import dev.jeval.synthesizer.StylingConfig;

var synthesizer = new Synthesizer(
        model,
        new StylingConfig("students learning geography", "ask study questions", "one question", null));

var goldens = synthesizer.generateGoldensFromContexts(
        List.of(List.of("Paris is the capital of France.")));
```

Plain-text document chunking is available through the CLI. Multi-turn generation
expects model JSON with `scenario`, optional `turns`, and optional
`expected_outcome`.

## Tracing

JEval includes a small local tracer for DeepEval-style agent trace maps. Use
`Tracer.observe(...)` around agent/tool/LLM calls, then attach `tracer.trace()`
to `LlmTestCase.trace` for trace metrics:

```java
import dev.jeval.LlmTestCase;
import dev.jeval.tracing.Tracer;
import java.util.Map;

var tracer = new Tracer();
var answer = tracer.observe("agent", "agent", Map.of("input", "refund"), () ->
        "answer: " + tracer.observe("lookup", "tool", Map.of("query", "refund"), () -> "policy"));

var testCase = LlmTestCase.builder("refund")
        .actualOutput(answer)
        .trace(tracer.trace())
        .build();
```

## Benchmarks

JEval includes a local BoolQ benchmark surface for supplied `Golden` rows. It
keeps DeepEval-style prediction rows and `overallScore` without downloading
external datasets:

```java
import dev.jeval.Golden;
import dev.jeval.benchmarks.BoolQ;
import java.util.List;
import java.util.Map;

var benchmark = new BoolQ(List.of(
        Golden.builder("Passage: Java is statically typed.\nQuestion: Is Java statically typed?")
                .expectedOutput("Yes")
                .build()));

var result = benchmark.evaluate(model);
```

MMLU-style task scoring is available for task-grouped goldens:

```java
import dev.jeval.benchmarks.MMLU;
import java.util.Map;

var mmlu = new MMLU(Map.of("abstract_algebra", goldens));
var mmluResult = mmlu.evaluate(model);
```

## LangChain4j providers

JEval can use any LangChain4j `ChatModel` through `LangChain4jEvaluationModel`.
Add the LangChain4j provider module you need, then wrap the model:

```java
import dev.jeval.EvaluationModel;
import dev.jeval.langchain4j.LangChain4jEvaluationModel;
import dev.jeval.metrics.AnswerRelevancyMetric;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

ChatModel chatModel = OpenAiChatModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName("gpt-4o-mini")
        .build();

EvaluationModel model = LangChain4jEvaluationModel.from(chatModel);
var metric = new AnswerRelevancyMetric(model);
```

JEval includes OpenAI and Ollama provider modules for CLI generation. Other
providers stay application dependencies. This adapter is text-only because
JEval's current `EvaluationModel` accepts a single prompt string.
