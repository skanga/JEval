package dev.jeval.benchmarks;

import dev.jeval.EvaluationModel;
import dev.jeval.Golden;
import dev.jeval.metrics.MetricUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SQuAD {
    private final Map<String, List<Golden>> taskGoldens;
    private final Integer nProblemsPerTask;
    private final EvaluationModel evaluationModel;
    private List<BenchmarkTaskPrediction> predictions;
    private List<BenchmarkTaskScore> taskScores;
    private Double overallScore;

    public SQuAD(Map<String, List<Golden>> taskGoldens, EvaluationModel evaluationModel) {
        this(taskGoldens, evaluationModel, null);
    }

    public SQuAD(Map<String, List<Golden>> taskGoldens, EvaluationModel evaluationModel, Integer nProblemsPerTask) {
        if (taskGoldens == null || taskGoldens.isEmpty()) {
            throw new IllegalArgumentException("'taskGoldens' must not be empty");
        }
        if (evaluationModel == null) {
            throw new IllegalArgumentException("'evaluationModel' must not be null");
        }
        if (nProblemsPerTask != null && nProblemsPerTask < 1) {
            throw new IllegalArgumentException("'nProblemsPerTask' must be positive");
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
        this.evaluationModel = evaluationModel;
        this.nProblemsPerTask = nProblemsPerTask;
    }

    public BenchmarkResult evaluate(EvaluationModel model) {
        if (model == null) {
            throw new IllegalArgumentException("'model' must not be null");
        }
        var rows = new ArrayList<BenchmarkTaskPrediction>();
        var scores = new ArrayList<BenchmarkTaskScore>();
        var totalCorrect = 0;
        var total = 0;
        for (var entry : taskGoldens.entrySet()) {
            var goldens = limited(entry.getValue());
            var taskCorrect = 0;
            for (var golden : goldens) {
                var prediction = model.generate(golden.input());
                var score = squadScore(golden.input(), prediction, golden.expectedOutput());
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

    private int squadScore(String input, String prediction, String expectedOutput) {
        var prompt = """
                Given the question and context, evaluate if the prediction is correct based on the expected output.
                Ensure to account for cases where the prediction and expected output might differ in form, such as '2' versus 'two'.

                %s
                Prediction: %s
                Expected Output: %s

                IMPORTANT:
                1. Make sure to output 1 if the prediction is correct and 0 if it's not.
                2. Respond in JSON format with the following structure:
                {
                    "answer": <number>
                }
                """.formatted(input, prediction, expectedOutput);
        var answer = MetricUtils.required(MetricUtils.trimAndLoadJson(evaluationModel.generate(prompt)), "answer");
        if (!answer.canConvertToInt()) {
            throw new IllegalArgumentException("Schema field must be an integer: answer");
        }
        return answer.asInt();
    }

    private List<Golden> limited(List<Golden> goldens) {
        if (nProblemsPerTask == null || nProblemsPerTask >= goldens.size()) {
            return goldens;
        }
        return goldens.subList(0, nProblemsPerTask);
    }
}
