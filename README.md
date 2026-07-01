# JEval

Modern Java port of [DeepEval](https://github.com/confident-ai/deepeval).

## Status

Port source: DeepEval `8ebfa33d78db4cf81c0ae340b1a925e5406469c8`.

Implemented:

- Core single-turn, conversational, golden, arena, tool-call, MCP, multimodal-placeholder, prompt, and dataset value types, including DeepEval-compatible deprecated param aliases
- `EvaluationDataset` JSON/JSONL/CSV import and export for single-turn and conversational goldens/test cases
- `Evaluator.evaluate(...)`, `Evaluator.evaluateConversations(...)`, arena evaluation, and assertion helpers
- Deterministic metrics: exact match, pattern match, JSON correctness, tool correctness, and DAG verdict paths
- Model-backed metric shells and prompt/schema parsing paths for the ported DeepEval single-turn, conversational, arena, MCP, trace, and DAG metrics
- Multimodal image metrics: text-to-image, image editing, image coherence, image helpfulness, and image reference
- Optimizer algorithm configuration, prompt configuration/report value types, and tie-breaker policy surfaces for SIMBA, COPRO, and GEPA
- DeepEval-style annotation payload values and validation for thumbs and five-star ratings
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
java -jar target/jeval-0.1.0-SNAPSHOT.jar test run path\to\eval.json --tb short --maxfail=1
java -jar target/jeval-0.1.0-SNAPSHOT.jar test run path\to\eval.json -X -W
java -jar target/jeval-0.1.0-SNAPSHOT.jar test run path\to\eval.json::case-name --quiet
java -jar target/jeval-0.1.0-SNAPSHOT.jar test run path\to\eval.json --results-folder evals --results-subfolder prompt-v3
```

Unknown pytest-style options after the target are accepted for DeepEval CLI
compatibility and ignored by the local Java runner when there is no pytest layer
to forward them to.

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
`--save dotenv` uses DeepEval's default `.env.local`; use `--save dotenv:<path>`
to choose another file.

```powershell
java -jar target/jeval-0.1.0-SNAPSHOT.jar settings -u log-level=error --save dotenv:.env
java -jar target/jeval-0.1.0-SNAPSHOT.jar settings -u log-level=info --save=dotenv:.env
java -jar target/jeval-0.1.0-SNAPSHOT.jar settings --list --save dotenv:.env
java -jar target/jeval-0.1.0-SNAPSHOT.jar set-debug --log-level DEBUG --verbose --grpc --trace-env staging -s dotenv:.env -q
java -jar target/jeval-0.1.0-SNAPSHOT.jar unset-debug --save dotenv:.env
java -jar target/jeval-0.1.0-SNAPSHOT.jar set-openai --model gpt-4o-mini --temperature 0.0 --save dotenv:.env
java -jar target/jeval-0.1.0-SNAPSHOT.jar set-openrouter --model openai/gpt-4.1 --temperature 0.0 --save dotenv:.env
java -jar target/jeval-0.1.0-SNAPSHOT.jar set-azure-openai --model gpt-4o --deployment-name eval-deployment --base-url https://example.openai.azure.com --api-version 2024-10-21 --save dotenv:.env
java -jar target/jeval-0.1.0-SNAPSHOT.jar set-bedrock --model anthropic.claude-3-5-haiku-20241022-v1:0 --region us-east-1 -i 0.000001 -o 0.000005 --save dotenv:.env
java -jar target/jeval-0.1.0-SNAPSHOT.jar set-anthropic --model claude-3-5-haiku-latest --cost-per-input-token 0.000001 --cost-per-output-token 0.000005 --save dotenv:.env
java -jar target/jeval-0.1.0-SNAPSHOT.jar set-gemini --model gemini-2.5-flash --project my-gcp-project --location us-central1 --save dotenv:.env
java -jar target/jeval-0.1.0-SNAPSHOT.jar set-gemini --model gemini-2.5-flash --service-account-file path\to\service-account.json --save dotenv:.env
java -jar target/jeval-0.1.0-SNAPSHOT.jar set-grok --model grok-4 --save dotenv:.env
java -jar target/jeval-0.1.0-SNAPSHOT.jar set-moonshot --model kimi-k2 --save dotenv:.env
java -jar target/jeval-0.1.0-SNAPSHOT.jar set-deepseek --model deepseek-chat --save dotenv:.env
java -jar target/jeval-0.1.0-SNAPSHOT.jar set-litellm --model openai/gpt-4.1 --base-url http://localhost:4000 --save dotenv:.env
java -jar target/jeval-0.1.0-SNAPSHOT.jar set-portkey --model gpt-4.1 --provider openai --save dotenv:.env
java -jar target/jeval-0.1.0-SNAPSHOT.jar set-ollama --model llama3 --base-url http://localhost:11434 --save dotenv:.env
java -jar target/jeval-0.1.0-SNAPSHOT.jar set-local-model --model local-eval --base-url http://localhost:8080/v1 --format openai --save dotenv:.env
java -jar target/jeval-0.1.0-SNAPSHOT.jar unset-openai --save dotenv:.env
```

`generate` can use saved provider settings for OpenAI, Azure OpenAI, AWS Bedrock,
Anthropic, Gemini API-key/Vertex, Grok, Moonshot/Kimi, DeepSeek, LiteLLM,
Portkey, OpenRouter, Ollama, and OpenAI-compatible local endpoints. Store the
matching API key or secret material in the same dotenv file, for example with
`settings -u openai-api-key=... --save dotenv:.env`; secret values are masked by
`settings --list`.

Embedding provider settings are persisted for DeepEval-compatible configuration
parity: `set-azure-openai-embedding`, `set-local-embeddings`, and
`set-ollama-embeddings`. The Ollama embedding command stores DeepEval's local
embedding sentinel `LOCAL_EMBEDDING_API_KEY=ollama`.

Provider retry helpers honor DeepEval-style environment settings:
`DEEPEVAL_RETRY_MAX_ATTEMPTS`, `DEEPEVAL_RETRY_INITIAL_SECONDS`,
`DEEPEVAL_RETRY_EXP_BASE`, `DEEPEVAL_RETRY_JITTER`,
`DEEPEVAL_RETRY_CAP_SECONDS`, `DEEPEVAL_SDK_RETRY_PROVIDERS`,
`DEEPEVAL_PER_ATTEMPT_TIMEOUT_SECONDS_OVERRIDE`,
`DEEPEVAL_PER_ATTEMPT_TIMEOUT_SECONDS`,
`DEEPEVAL_PER_TASK_TIMEOUT_SECONDS_OVERRIDE`,
`DEEPEVAL_PER_TASK_TIMEOUT_SECONDS`,
`DEEPEVAL_TASK_GATHER_BUFFER_SECONDS_OVERRIDE`,
`DEEPEVAL_TASK_GATHER_BUFFER_SECONDS`,
`DEEPEVAL_TIMEOUT_THREAD_LIMIT`,
`DEEPEVAL_TIMEOUT_SEMAPHORE_WARN_AFTER_SECONDS`, and `DEEPEVAL_DISABLE_TIMEOUTS`
(`openai`, `azure-openai`, or `*` to delegate retries to provider SDKs).
When saved through `settings -u`, DeepEval's deprecated computed timeout keys
`DEEPEVAL_PER_ATTEMPT_TIMEOUT_SECONDS`, `DEEPEVAL_PER_TASK_TIMEOUT_SECONDS`,
and `DEEPEVAL_TASK_GATHER_BUFFER_SECONDS` are persisted as their
`*_OVERRIDE` keys; explicit override keys win over deprecated aliases.
The retry executor retries transient provider failures up to the configured
attempt cap, treats per-attempt timeouts as retryable, derives DeepEval-style
task timeout/gather budgets, bounds timeout worker concurrency, and leaves
SDK-delegated or policy-less providers as single calls.

## CLI generate

The `generate` command supports single-turn and multi-turn `contexts`, `docs`,
`scratch`, and `goldens` generation through JEval's synthesizer. It can use any
saved LangChain4j-backed provider settings listed above, or deterministic
scripted responses with `--responses-file`:
Existing goldens supplied with `--goldens-file` can be JSON, JSONL, or CSV.
Docs generation accepts either JEval's `--document-path` or DeepEval's repeatable
`--documents` option; expected outputs use DeepEval's
`--include-expected/--no-include-expected` flag pair. Document loading supports
DeepEval-style PDF, DOCX, text, and Markdown inputs (`.pdf`, `.docx`, `.txt`,
`.md`, `.markdown`, `.mdx`) and rejects unsupported extensions with
DeepEval-style errors; text and Markdown inputs strip a leading UTF-8 BOM like
DeepEval's autodetecting text loader and preserve intra-chunk whitespace such
as Markdown table line structure.

```powershell
java -jar target/jeval-0.1.0-SNAPSHOT.jar set-openai --model gpt-4o-mini --save dotenv:.env
java -jar target/jeval-0.1.0-SNAPSHOT.jar settings -u openai-api-key=$env:OPENAI_API_KEY --save dotenv:.env
java -jar target/jeval-0.1.0-SNAPSHOT.jar generate --method contexts --variation single-turn --contexts-file contexts.json --save=dotenv:.env --output-dir generated
java -jar target/jeval-0.1.0-SNAPSHOT.jar generate --method docs --variation single-turn --documents docs\knowledge.md --chunk-size 200 --max-context-length 3 --min-context-length 1 --encoding UTF-8 --save dotenv:.env --output-dir generated
java -jar target/jeval-0.1.0-SNAPSHOT.jar generate --method docs --variation single-turn --documents docs\policy.md --documents docs\faq.md --allow-cross-file-contexts --target-files-per-context 2 --max-files-per-context 2 --save dotenv:.env --output-dir generated
java -jar target/jeval-0.1.0-SNAPSHOT.jar generate --method contexts --variation single-turn --contexts-file contexts.json --num-evolutions 2 --evolutions comparative,constrained --synthetic-input-quality-threshold 0.8 --max-quality-retries 2 --save dotenv:.env --output-dir generated
java -jar target/jeval-0.1.0-SNAPSHOT.jar generate --method contexts --variation multi-turn --contexts-file contexts.json --save dotenv:.env --output-dir generated
java -jar target/jeval-0.1.0-SNAPSHOT.jar generate --method contexts --variation single-turn --contexts-file contexts.json --responses-file responses.txt --no-include-expected --output-dir generated
```

## Synthesizer

The synthesizer can generate single-turn `Golden` values from contexts, scratch
styling, or existing goldens, and multi-turn `ConversationalGolden` values from
contexts, scratch styling, or existing conversational goldens. It also includes
DeepEval-style text-to-SQL generation from schema context. It uses any
`EvaluationModel`, including the LangChain4j adapter. When single-turn goldens
are generated from existing goldens without an explicit `StylingConfig`, JEval
infers prompt styling from those existing inputs before generating replacements,
including the contextual branch. Conversational generation follows the same
DeepEval-style pattern with inferred `ConversationalStylingConfig` from existing
scenarios before using scratch or contextual generation. Existing goldens with
an explicit empty context list still use contextual generation, matching
DeepEval's `context is None` branching.

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

Plain-text document chunking is available through the CLI, including DeepEval's
`max_context_length`, `min_context_length`, and explicit `encoding` controls. If
one document in a multi-document generation run cannot meet the minimum context
count, JEval skips that document and continues with the remaining valid
documents; a run where no document can produce contexts still fails with the
DeepEval-style validation message. Document-generated source labels preserve the
original path string provided to the generator, matching DeepEval metadata.
Multi-turn generation expects model JSON with `scenario`, optional `turns`, and
optional `expected_outcome`.

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

JEval includes LangChain4j provider modules for OpenAI-compatible endpoints,
Azure OpenAI, AWS Bedrock, Anthropic, Gemini, and Ollama. Grok,
Moonshot/Kimi, DeepSeek, LiteLLM, Portkey, OpenRouter, and custom local
OpenAI-compatible endpoints are routed through the OpenAI-compatible
LangChain4j model with provider-specific base URLs and environment keys. This
adapter is text-only because JEval's current `EvaluationModel` accepts a single
prompt string.
