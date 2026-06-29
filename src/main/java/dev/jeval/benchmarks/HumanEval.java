package dev.jeval.benchmarks;

import dev.jeval.EvaluationModel;
import dev.jeval.Golden;
import dev.jeval.scorer.Scorer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

public final class HumanEval {
    private final Map<String, Golden> taskGoldens;
    private final BiPredicate<Golden, String> checker;
    private final int n;
    private List<HumanEvalPrediction> predictions;
    private List<BenchmarkTaskScore> taskScores;
    private Double overallScore;

    public HumanEval(Map<String, Golden> taskGoldens, BiPredicate<Golden, String> checker) {
        this(taskGoldens, checker, 200);
    }

    public HumanEval(Map<String, Golden> taskGoldens, BiPredicate<Golden, String> checker, int n) {
        if (taskGoldens == null || taskGoldens.isEmpty()) {
            throw new IllegalArgumentException("'taskGoldens' must not be empty");
        }
        if (checker == null) {
            throw new IllegalArgumentException("'checker' must not be null");
        }
        if (n < 1) {
            throw new IllegalArgumentException("'n' must be positive");
        }
        var copied = new LinkedHashMap<String, Golden>();
        for (var entry : taskGoldens.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                throw new IllegalArgumentException("'taskGoldens' task names must not be blank");
            }
            if (entry.getValue() == null) {
                throw new IllegalArgumentException("'taskGoldens' values must not be null");
            }
            copied.put(entry.getKey(), entry.getValue());
        }
        this.taskGoldens = Collections.unmodifiableMap(copied);
        this.checker = checker;
        this.n = n;
    }

    public BenchmarkResult evaluate(EvaluationModel model) {
        return evaluate(model, 1);
    }

    public BenchmarkResult evaluate(EvaluationModel model, int k) {
        if (model == null) {
            throw new IllegalArgumentException("'model' must not be null");
        }
        if (k < 1 || k > n) {
            throw new IllegalArgumentException("'k' must be between 1 and n");
        }
        var rows = new ArrayList<HumanEvalPrediction>();
        var scores = new ArrayList<BenchmarkTaskScore>();
        var successfulTasks = 0;
        for (var entry : taskGoldens.entrySet()) {
            var task = entry.getKey();
            var golden = entry.getValue();
            var taskPredictions = new ArrayList<String>();
            var correctSamples = 0;
            var prompt = prompt(golden.input(), task);
            for (var i = 0; i < n; i++) {
                var prediction = model.generate(prompt);
                taskPredictions.add(prediction);
                if (checker.test(golden, prediction)) {
                    correctSamples++;
                }
            }
            var passAtK = Scorer.passAtK(n, correctSamples, k);
            var taskScore = passAtK == 0.0 ? 0 : 1;
            successfulTasks += taskScore;
            rows.add(new HumanEvalPrediction(task, golden.input(), List.copyOf(taskPredictions),
                    golden.expectedOutput(), correctSamples, passAtK));
            scores.add(new BenchmarkTaskScore(task, taskScore));
        }
        overallScore = (double) successfulTasks / taskGoldens.size();
        predictions = List.copyOf(rows);
        taskScores = List.copyOf(scores);
        return new BenchmarkResult(overallScore);
    }

    public int n() {
        return n;
    }

    public List<HumanEvalPrediction> predictions() {
        return predictions == null ? null : List.copyOf(predictions);
    }

    public List<BenchmarkTaskScore> taskScores() {
        return taskScores == null ? null : List.copyOf(taskScores);
    }

    public Double overallScore() {
        return overallScore;
    }

    private static String prompt(String input, String task) {
        return "Complete the following function.\n"
                + input
                + "Only output the function with the following entry_point: `%s` in string format.".formatted(task)
                + "Make sure your output begins with 'def'. No explanations needed. "
                + "Do not format as markdown (such as *```python ... ```*).";
    }

    public record HumanEvalPrediction(String task, String input, List<String> predictions, String expectedOutput,
                                      int correctSamples, double score) {
    }
}
