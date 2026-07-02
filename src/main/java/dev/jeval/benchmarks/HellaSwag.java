package dev.jeval.benchmarks;

import dev.jeval.EvaluationModel;
import dev.jeval.Golden;
import dev.jeval.scorer.Scorer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HellaSwag {
    private static final String CONFINEMENT_INSTRUCTIONS =
            "Output 'A', 'B', 'C', or 'D'. Full answer not needed.";
    private final Map<String, List<Golden>> taskGoldens;
    private final Integer nProblemsPerTask;
    private final List<Golden> shotGoldens;
    private final int nShots;
    private List<BenchmarkTaskPrediction> predictions;
    private List<BenchmarkTaskScore> taskScores;
    private Double overallScore;

    public HellaSwag(Map<String, List<Golden>> taskGoldens) {
        this(taskGoldens, null);
    }

    public HellaSwag(Map<String, List<Golden>> taskGoldens, Integer nProblemsPerTask) {
        this(taskGoldens, nProblemsPerTask, List.of(), 0);
    }

    public HellaSwag(Map<String, List<Golden>> taskGoldens, Integer nProblemsPerTask, List<Golden> shotGoldens,
            int nShots) {
        if (taskGoldens == null || taskGoldens.isEmpty()) {
            throw new IllegalArgumentException("'taskGoldens' must not be empty");
        }
        if (shotGoldens == null) {
            throw new IllegalArgumentException("'shotGoldens' must not be null");
        }
        if (nProblemsPerTask != null && nProblemsPerTask < 1) {
            throw new IllegalArgumentException("'nProblemsPerTask' must be positive");
        }
        if (nShots < 0 || nShots > 15) {
            throw new IllegalArgumentException("HellaSwag only supports nShots between 0 and 15");
        }
        if (nShots > shotGoldens.size()) {
            throw new IllegalArgumentException("'nShots' must not exceed shotGoldens size");
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
        this.shotGoldens = List.copyOf(shotGoldens);
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
            var taskPredictions = predictions(model, entry.getKey(), goldens, batchSize, shotGoldens, nShots);
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

    private static List<String> predictions(EvaluationModel model, String task, List<Golden> goldens,
            Integer batchSize, List<Golden> shotGoldens, int nShots) {
        if (batchSize == null) {
            return goldens.stream().map(golden -> model.generate(prompt(task, golden.input(), shotGoldens, nShots)))
                    .toList();
        }
        var predictions = new ArrayList<String>();
        for (var i = 0; i < goldens.size(); i += batchSize) {
            var batch = goldens.subList(i, Math.min(i + batchSize, goldens.size()));
            var prompts = batch.stream().map(golden -> prompt(task, golden.input(), shotGoldens, nShots)).toList();
            var batchPredictions = model.batchGenerate(prompts);
            if (batchPredictions.size() != batch.size()) {
                throw new IllegalArgumentException("batchGenerate must return one response per prompt");
            }
            predictions.addAll(batchPredictions);
        }
        return predictions;
    }

    private static String prompt(String task, String input, List<Golden> shotGoldens, int nShots) {
        var prompt = new StringBuilder("The following are multiple choice questions (with answers) "
                + "are sentence completion problems about %s.\n\n".formatted(task));
        for (var i = 0; i < nShots; i++) {
            var shot = shotGoldens.get(i);
            prompt.append(shot.input()).append(' ').append(shot.expectedOutput()).append("\n\n");
        }
        return prompt.append(input).append("\n\n").append(CONFINEMENT_INSTRUCTIONS).toString();
    }
}
