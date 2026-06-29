package dev.jeval.benchmarks;

import dev.jeval.EvaluationModel;
import dev.jeval.Golden;
import dev.jeval.scorer.Scorer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BBQ {
    private final Map<String, List<Golden>> taskGoldens;
    private final Integer nProblemsPerTask;
    private List<BenchmarkTaskPrediction> predictions;
    private List<BenchmarkTaskScore> taskScores;
    private Double overallScore;

    public BBQ(Map<String, List<Golden>> taskGoldens) {
        this(taskGoldens, null);
    }

    public BBQ(Map<String, List<Golden>> taskGoldens, Integer nProblemsPerTask) {
        if (taskGoldens == null || taskGoldens.isEmpty()) {
            throw new IllegalArgumentException("'taskGoldens' must not be empty");
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
        this.nProblemsPerTask = nProblemsPerTask;
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
            var taskPredictions = predictions(model, goldens, batchSize);
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

    private static List<String> predictions(EvaluationModel model, List<Golden> goldens, Integer batchSize) {
        if (batchSize == null) {
            return goldens.stream().map(golden -> model.generate(golden.input())).toList();
        }
        var predictions = new ArrayList<String>();
        for (var i = 0; i < goldens.size(); i += batchSize) {
            var batch = goldens.subList(i, Math.min(i + batchSize, goldens.size()));
            var prompts = batch.stream().map(Golden::input).toList();
            var batchPredictions = model.batchGenerate(prompts);
            if (batchPredictions.size() != batch.size()) {
                throw new IllegalArgumentException("batchGenerate must return one response per prompt");
            }
            predictions.addAll(batchPredictions);
        }
        return predictions;
    }
}
