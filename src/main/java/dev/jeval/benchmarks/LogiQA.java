package dev.jeval.benchmarks;

import dev.jeval.EvaluationModel;
import dev.jeval.Golden;
import dev.jeval.scorer.Scorer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LogiQA {
    private static final List<String> N_SHOT_EXAMPLES = List.of(
            "Input\nWrite a multi-choice question for the following article:\nArticle: David knows Mr. Zhang's friend Jack, and Jack knows David's friend Ms. Lin. Everyone of them who knows Jack has a master's degree, and everyone of them who knows Ms. Lin is from Shanghai.\nQuestion: \nWho is from Shanghai and has a master's degree?\nOptions:\nA David\nB Jack\nC Mr Zhang\nD Ms. Lin\nAnswer:\nA\n",
            "Input\nWrite a multi-choice question for the following article:\nArticle: Jimmy asked Hank to go to the mall the next day. Hank said, If it doesn't rain tomorrow, I'll go climbing. The next day, there was a drizzle. Jimmy thought that Hank would not go climbing, so he went to pick up Henry to the mall. Nevertheless, Hank went climbing the mountain. When the two met again, Jimmy blamed Hank for not keeping his word.\nQuestion: \nWhich of the following comments is appropriate?\nOptions:\nA This argument between Jimmy and Hank is meaningless\nB Jimmy's reasoning is illogical\nC Two people have different understandings of a drizzle\nD Hank broke his promise and caused the debate\nAnswer:\nB\n",
            "Input\nWrite a multi-choice question for the following article:\nArticle: Only if the government reinforce basic education can we improve our nation's education to a new stage. In order to stand out among other nations, we need to have a strong educational enterprise.\nQuestion: \nWhich can be inferred from the statement above?\nOptions:\nA The whole society should be focused on education\nB In order to stand out among nations, we should reinforce basic education\nC In order to improve our education to a new stage, it is necessary to increase the salary of college teachers\nD In order to reinforce basic education, all primary school teachers must have a bachelor degree or above.\nAnswer:\nB\n",
            "Input\nWrite a multi-choice question for the following article:\nArticle: Last night, Mark either went to play in the gym or visited his teacher Tony. If Mark drove last night, he didn't go to play in the gym. Mark would go visit his teacher Tony only if he and his teacher had an appointment. In fact, Mark had no appointment with his teacher Tony in advance.\nQuestion: \nWhich is true based on the above statement?\nOptions:\nA Mark went to the gym with his teacher Tony last night\nB Mark visited his teacher Tony last night\nC Mark didn't drive last night\nD Mark didn't go to the gym last night.\nAnswer:\nC\n",
            "Input\nWrite a multi-choice question for the following article:\nArticle: The coach of a national football team found that the best cooperative arrangement of the players U, V, W, X, Y, and Z during the training are: (1) V and X cannot be on the field at the same time, and neither can be off the field the same time. (2) V is not on the field only if U is not on the field. (3) If W is on the field, then X is on the field. (4) If Y and Z are on the field, then W must be on the field. This arrangement can yield the best performance.\nQuestion: \nIf U and Z are both on the field, for best performance, which of the following arrangement is appropriate?\nOptions:\nA X is on the field and Y is not on the field\nB V is on the field and Y is not on the field\nC V and W are both on the field\nD V and Y are not on the field\nAnswer:\nB\n");
    private static final String CONFINEMENT_INSTRUCTIONS =
            "Output 'A', 'B', 'C', or 'D'. Full answer not needed.";
    private final Map<String, List<Golden>> taskGoldens;
    private final Integer nProblemsPerTask;
    private final int nShots;
    private List<BenchmarkTaskPrediction> predictions;
    private List<BenchmarkTaskScore> taskScores;
    private Double overallScore;

    public LogiQA(Map<String, List<Golden>> taskGoldens) {
        this(taskGoldens, null);
    }

    public LogiQA(Map<String, List<Golden>> taskGoldens, Integer nProblemsPerTask) {
        this(taskGoldens, nProblemsPerTask, 5);
    }

    public LogiQA(Map<String, List<Golden>> taskGoldens, Integer nProblemsPerTask, int nShots) {
        if (taskGoldens == null || taskGoldens.isEmpty()) {
            throw new IllegalArgumentException("'taskGoldens' must not be empty");
        }
        if (nProblemsPerTask != null && nProblemsPerTask < 1) {
            throw new IllegalArgumentException("'nProblemsPerTask' must be positive");
        }
        if (nShots < 0 || nShots > 5) {
            throw new IllegalArgumentException("LogiQA only supports nShots between 0 and 5");
        }
        var copied = new LinkedHashMap<String, List<Golden>>();
        for (var entry : taskGoldens.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                throw new IllegalArgumentException("'taskGoldens' task names must not be blank");
            }
            if (entry.getValue() == null || entry.getValue().isEmpty()) {
                throw new IllegalArgumentException("'taskGoldens' values must not be empty");
            }
            copied.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        this.taskGoldens = Collections.unmodifiableMap(copied);
        this.nProblemsPerTask = nProblemsPerTask;
        this.nShots = nShots;
    }

    public BenchmarkResult evaluate(EvaluationModel model) {
        return evaluate(model, null);
    }

    public BenchmarkResult evaluate(EvaluationModel model, Integer batchSize) {
        if (model == null) {
            throw new IllegalArgumentException("'model' must not be null");
        }
        if (batchSize != null && batchSize < 1) {
            throw new IllegalArgumentException("'batchSize' must be positive");
        }
        var rows = new ArrayList<BenchmarkTaskPrediction>();
        var scores = new ArrayList<BenchmarkTaskScore>();
        var totalCorrect = 0;
        var total = 0;
        for (var entry : taskGoldens.entrySet()) {
            var goldens = limited(entry.getValue());
            var taskCorrect = 0;
            var taskPredictions = predictions(model, goldens, batchSize, nShots);
            for (var i = 0; i < goldens.size(); i++) {
                var golden = goldens.get(i);
                var prediction = taskPredictions.get(i);
                var score = Scorer.exactMatchScore(golden.expectedOutput(), prediction);
                taskCorrect += score;
                totalCorrect += score;
                rows.add(new BenchmarkTaskPrediction(entry.getKey(), golden.input(), prediction,
                        golden.expectedOutput(), score));
            }
            total += goldens.size();
            scores.add(new BenchmarkTaskScore(entry.getKey(), (double) taskCorrect / goldens.size()));
        }
        overallScore = (double) totalCorrect / total;
        predictions = List.copyOf(rows);
        taskScores = List.copyOf(scores);
        return new BenchmarkResult(overallScore);
    }

    public List<BenchmarkTaskPrediction> predictions() {
        return predictions == null ? null : List.copyOf(predictions);
    }

    public List<BenchmarkTaskScore> taskScores() {
        return taskScores == null ? null : List.copyOf(taskScores);
    }

    public Double overallScore() {
        return overallScore;
    }

    private List<Golden> limited(List<Golden> goldens) {
        if (nProblemsPerTask == null || nProblemsPerTask >= goldens.size()) {
            return goldens;
        }
        return goldens.subList(0, nProblemsPerTask);
    }

    private static List<String> predictions(EvaluationModel model, List<Golden> goldens, Integer batchSize, int nShots) {
        if (batchSize == null) {
            return goldens.stream().map(golden -> model.generate(prompt(golden.input(), nShots))).toList();
        }
        var predictions = new ArrayList<String>();
        for (var i = 0; i < goldens.size(); i += batchSize) {
            var batch = goldens.subList(i, Math.min(i + batchSize, goldens.size()));
            var prompts = batch.stream().map(golden -> prompt(golden.input(), nShots)).toList();
            var batchPredictions = model.batchGenerate(prompts);
            if (batchPredictions.size() != batch.size()) {
                throw new IllegalArgumentException("batchGenerate must return one response per prompt");
            }
            predictions.addAll(batchPredictions);
        }
        return predictions;
    }

    private static String prompt(String input, int nShots) {
        var prompt = new StringBuilder();
        for (var i = 0; i < nShots; i++) {
            prompt.append(N_SHOT_EXAMPLES.get(i)).append('\n');
        }
        return prompt.append(input).append("\n\n").append(CONFINEMENT_INSTRUCTIONS).toString();
    }
}
