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

JEval includes only `langchain4j-core`; provider modules such as OpenAI,
Anthropic, Ollama, Gemini, or Bedrock stay application dependencies. This
adapter is text-only because JEval's current `EvaluationModel` accepts a
single prompt string.
