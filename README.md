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
- Multimodal image metrics: text-to-image, image editing, image coherence, image helpfulness, and image reference
- Optimizer algorithm configuration, prompt configuration/report value types, and tie-breaker policy surfaces for SIMBA, COPRO, and GEPA
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
java -jar target/jeval-0.1.0-SNAPSHOT.jar test run path\to\eval.json -id release-smoke --format markdown --output reports
java -jar target/jeval-0.1.0-SNAPSHOT.jar test run path\to\eval.json -r 3 --quiet
java -jar target/jeval-0.1.0-SNAPSHOT.jar test run path\to\eval.json -x
java -jar target/jeval-0.1.0-SNAPSHOT.jar test run path\to\eval.json -d failing
java -jar target/jeval-0.1.0-SNAPSHOT.jar test run path\to\eval.json -i
java -jar target/jeval-0.1.0-SNAPSHOT.jar test run path\to\eval.json -s
java -jar target/jeval-0.1.0-SNAPSHOT.jar test run path\to\eval.json -c
java -jar target/jeval-0.1.0-SNAPSHOT.jar test run path\to\eval.json -v
java -jar target/jeval-0.1.0-SNAPSHOT.jar test run path\to\eval.json -m smoke
java -jar target/jeval-0.1.0-SNAPSHOT.jar test run path\to\eval.json -o
java -jar target/jeval-0.1.0-SNAPSHOT.jar test run path\to\eval.json --color no --durations 5 -w -n 2
java -jar target/jeval-0.1.0-SNAPSHOT.jar test run path\to\eval.json -X -W
java -jar target/jeval-0.1.0-SNAPSHOT.jar test run path\to\eval.json::case-name --quiet
java -jar target/jeval-0.1.0-SNAPSHOT.jar test run path\to\eval.json --results-folder evals --results-subfolder prompt-v3
```

Each run writes DeepEval-style local artifacts to `.deepeval/.latest_run_full.json`
and `.deepeval/test_run_<YYYYMMDD_HHMMSS>.json`; JEval also keeps the legacy
`.jeval/.jeval` snapshot. Use `--results-folder` and optional
`--results-subfolder`, or set `DEEPEVAL_RESULTS_FOLDER`, to also write
timestamped DeepEval-style run files to a custom directory.

Inspect the latest local run or a folder of timestamped runs:

```powershell
java -jar target/jeval-0.1.0-SNAPSHOT.jar inspect
java -jar target/jeval-0.1.0-SNAPSHOT.jar inspect .deepeval
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
java -jar target/jeval-0.1.0-SNAPSHOT.jar settings -u log-level=info --save=dotenv:.env
java -jar target/jeval-0.1.0-SNAPSHOT.jar settings --list --save dotenv:.env
java -jar target/jeval-0.1.0-SNAPSHOT.jar set-debug --log-level DEBUG --verbose --grpc --trace-env staging -s dotenv:.env -q
java -jar target/jeval-0.1.0-SNAPSHOT.jar unset-debug --save dotenv:.env
java -jar target/jeval-0.1.0-SNAPSHOT.jar set-openai --model gpt-4o-mini --temperature 0.0 --save dotenv:.env
java -jar target/jeval-0.1.0-SNAPSHOT.jar set-openrouter --model openai/gpt-4.1 --temperature 0.0 --save dotenv:.env
java -jar target/jeval-0.1.0-SNAPSHOT.jar set-gemini --model gemini-2.5-flash --project my-gcp-project --location us-central1 --save dotenv:.env
java -jar target/jeval-0.1.0-SNAPSHOT.jar set-gemini --model gemini-2.5-flash --service-account-file path\to\service-account.json --save dotenv:.env
java -jar target/jeval-0.1.0-SNAPSHOT.jar unset-openai --save dotenv:.env
```

OpenAI, OpenRouter, and Ollama provider settings can be used by `generate`; other provider
commands currently persist configuration only.

Provider retry helpers honor DeepEval-style environment settings:
`DEEPEVAL_RETRY_MAX_ATTEMPTS`, `DEEPEVAL_RETRY_INITIAL_SECONDS`,
`DEEPEVAL_RETRY_EXP_BASE`, `DEEPEVAL_RETRY_JITTER`,
`DEEPEVAL_RETRY_CAP_SECONDS`, `DEEPEVAL_SDK_RETRY_PROVIDERS`,
`DEEPEVAL_PER_ATTEMPT_TIMEOUT_SECONDS_OVERRIDE`,
`DEEPEVAL_PER_ATTEMPT_TIMEOUT_SECONDS`,
`DEEPEVAL_PER_TASK_TIMEOUT_SECONDS_OVERRIDE`,
`DEEPEVAL_PER_TASK_TIMEOUT_SECONDS`,
`DEEPEVAL_TASK_GATHER_BUFFER_SECONDS_OVERRIDE`,
`DEEPEVAL_TASK_GATHER_BUFFER_SECONDS`, and `DEEPEVAL_DISABLE_TIMEOUTS`
(`openai`, `azure-openai`, or `*` to delegate retries to provider SDKs).
The retry executor retries transient provider failures up to the configured
attempt cap, treats per-attempt timeouts as retryable, derives DeepEval-style
task timeout/gather budgets, and leaves SDK-delegated or policy-less providers
as single calls.

## CLI generate

The `generate` command supports single-turn and multi-turn `contexts`, `docs`,
`scratch`, and `goldens` generation through JEval's synthesizer. It can use OpenAI/OpenRouter/Ollama settings saved with provider
commands, or deterministic scripted responses with `--responses-file`:
Existing goldens supplied with `--goldens-file` can be JSON, JSONL, or CSV.
Docs generation accepts either JEval's `--document-path` or DeepEval's repeatable
`--documents` option; expected outputs can be suppressed with `--no-include-expected`.

```powershell
java -jar target/jeval-0.1.0-SNAPSHOT.jar set-openai --model gpt-4o-mini --save dotenv:.env
java -jar target/jeval-0.1.0-SNAPSHOT.jar settings -u openai-api-key=$env:OPENAI_API_KEY --save dotenv:.env
java -jar target/jeval-0.1.0-SNAPSHOT.jar generate --method contexts --variation single-turn --contexts-file contexts.json --save=dotenv:.env --output-dir generated
java -jar target/jeval-0.1.0-SNAPSHOT.jar generate --method docs --variation single-turn --documents docs\knowledge.md --chunk-size 200 --save dotenv:.env --output-dir generated
java -jar target/jeval-0.1.0-SNAPSHOT.jar generate --method docs --variation single-turn --documents docs\policy.md --documents docs\faq.md --allow-cross-file-contexts --target-files-per-context 2 --max-files-per-context 2 --save dotenv:.env --output-dir generated
java -jar target/jeval-0.1.0-SNAPSHOT.jar generate --method contexts --variation multi-turn --contexts-file contexts.json --save dotenv:.env --output-dir generated
java -jar target/jeval-0.1.0-SNAPSHOT.jar generate --method contexts --variation single-turn --contexts-file contexts.json --responses-file responses.txt --no-include-expected --output-dir generated
```

## Synthesizer

The synthesizer can generate single-turn `Golden` values from contexts, scratch
styling, or existing goldens, and multi-turn `ConversationalGolden` values from
contexts, scratch styling, or existing conversational goldens. It also includes
DeepEval-style text-to-SQL generation from schema context. It uses any
`EvaluationModel`, including the LangChain4j adapter.

```java
import dev.jeval.Golden;
import dev.jeval.synthesizer.Synthesizer;
import dev.jeval.synthesizer.StylingConfig;

var synthesizer = new Synthesizer(
        model,
        new StylingConfig("students learning geography", "ask study questions", "one question", null));

var goldens = synthesizer.generateGoldensFromContexts(
        List.of(List.of("Paris is the capital of France.")));

var sqlGoldens = synthesizer.generateTextToSqlGoldensFromContext(
        List.of("CREATE TABLE users (id INT, name TEXT)"), true, 1);
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

JEval includes local benchmark surfaces for supplied `Golden` rows. They keep
DeepEval-style prediction rows and `overallScore` without downloading external
datasets:

```java
import dev.jeval.Golden;
import dev.jeval.benchmarks.ARC;
import dev.jeval.benchmarks.BBQ;
import dev.jeval.benchmarks.BigBenchHard;
import dev.jeval.benchmarks.BoolQ;
import dev.jeval.benchmarks.DROP;
import dev.jeval.benchmarks.EquityMedQA;
import dev.jeval.benchmarks.GSM8K;
import dev.jeval.benchmarks.HellaSwag;
import dev.jeval.benchmarks.HumanEval;
import dev.jeval.benchmarks.IFEval;
import dev.jeval.benchmarks.LAMBADA;
import dev.jeval.benchmarks.LogiQA;
import dev.jeval.benchmarks.MathQA;
import dev.jeval.benchmarks.SQuAD;
import dev.jeval.benchmarks.TruthfulQA;
import dev.jeval.benchmarks.TruthfulQAMode;
import dev.jeval.benchmarks.Winogrande;
import java.util.List;
import java.util.Map;

var benchmark = new BoolQ(List.of(
        Golden.builder("Passage: Java is statically typed.\nQuestion: Is Java statically typed?")
                .expectedOutput("Yes")
                .build()));

var result = benchmark.evaluate(model);
var arcResult = new ARC(goldens).evaluate(model);
var bbq = new BBQ(Map.of("Age", goldens)).evaluate(model);
var bbh = new BigBenchHard(Map.of("boolean_expressions", goldens)).evaluate(model);
var drop = new DROP(Map.of("history", goldens)).evaluate(model);
var equityMedQA = new EquityMedQA(Map.of("EHAI", goldens), biasMetric).evaluate(model);
var gsm8k = new GSM8K(goldens).evaluate(model);
var hellaswagResult = new HellaSwag(Map.of("Applying sunscreen", goldens)).evaluate(model);
var humanEval = new HumanEval(
        Map.of("add", Golden.builder("def add(a, b):\n").expectedOutput("assert add(1, 2) == 3").build()),
        (golden, prediction) -> prediction.contains("return a + b"),
        4).evaluate(model, 2);
var ifeval = new IFEval(goldens).evaluate(model);
var lambada = new LAMBADA(goldens).evaluate(model);
var logiqa = new LogiQA(Map.of("deduction", goldens)).evaluate(model);
var mathqa = new MathQA(Map.of("general", goldens)).evaluate(model);
var squad = new SQuAD(Map.of("Apollo_program", goldens), evaluatorModel).evaluate(model);
var truthful = new TruthfulQA(Map.of("Health", goldens), TruthfulQAMode.MC1).evaluate(model);
var winogrande = new Winogrande(goldens).evaluate(model);
```

MMLU-style task scoring is available for task-grouped goldens:

```java
import dev.jeval.benchmarks.MMLU;
import java.util.Map;

var mmlu = new MMLU(Map.of("abstract_algebra", goldens));
var mmluResult = mmlu.evaluate(model);
var batched = mmlu.evaluate(model, 16);
```

## Multimodal image metrics

JEval supports DeepEval-style image placeholders through `MllmImage` and the
multimodal image metrics ported from DeepEval:

- `TextToImageMetric`
- `ImageEditingMetric`
- `ImageCoherenceMetric`
- `ImageHelpfulnessMetric`
- `ImageReferenceMetric`

```java
import dev.jeval.LlmTestCase;
import dev.jeval.MllmImage;
import dev.jeval.metrics.TextToImageMetric;

var image = new MllmImage("base64-encoded-png", "image/png");
var testCase = LlmTestCase.builder("Draw a red car")
        .actualOutput("Generated image: " + image)
        .build();

var result = new TextToImageMetric(model).measure(testCase);
```

These metrics use the local `EvaluationModel` abstraction for prompt execution
and validate the same input-image/output-image cardinality rules as DeepEval.

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
