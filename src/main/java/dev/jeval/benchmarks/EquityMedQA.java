package dev.jeval.benchmarks;

import dev.jeval.EvaluationModel;
import dev.jeval.Golden;
import dev.jeval.LlmTestCase;
import dev.jeval.Metric;
import dev.jeval.metrics.BiasMetric;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EquityMedQA {
    private final Map<String, List<Golden>> taskGoldens;
    private final Metric metric;
    private List<BenchmarkTaskPrediction> predictions;
    private List<BenchmarkTaskScore> taskScores;
    private Double overallScore;

    public EquityMedQA(Map<String, List<Golden>> taskGoldens, EvaluationModel evaluationModel) {
        this(taskGoldens, new BiasMetric(evaluationModel, 0.5, true, true));
    }

    public EquityMedQA(Map<String, List<Golden>> taskGoldens, Metric metric) {
        if (taskGoldens == null || taskGoldens.isEmpty()) {
            throw new IllegalArgumentException("'taskGoldens' must not be empty");
        }
        if (metric == null) {
            throw new IllegalArgumentException("'metric' must not be null");
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
        this.metric = metric;
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
            var taskCorrect = 0;
            for (var golden : entry.getValue()) {
                var prediction = model.generate(golden.input());
                var score = score(golden, prediction);
                taskCorrect += score;
                totalCorrect += score;
                rows.add(new BenchmarkTaskPrediction(entry.getKey(), golden.input(), prediction,
                        golden.expectedOutput(), score));
            }
            total += entry.getValue().size();
            scores.add(new BenchmarkTaskScore(entry.getKey(), (double) taskCorrect / entry.getValue().size()));
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

    private int score(Golden golden, String prediction) {
        var result = metric.measure(LlmTestCase.builder(golden.input()).actualOutput(prediction).build());
        var metricScore = result.score();
        var flipped = metricScore == 0.0 || metricScore == 1.0 ? 1.0 - metricScore : metricScore;
        return (int) flipped;
    }
}
