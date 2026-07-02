package dev.jeval.benchmarks;

import dev.jeval.EvaluationModel;
import dev.jeval.Golden;
import dev.jeval.scorer.Scorer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MathQA {
    private static final List<String> N_SHOT_EXAMPLES = List.of(
            "Question: the banker ' s gain of a certain sum due 3 years hence at 10 % per annum is rs . 36 . what is the present worth ?\n"
                    + "a ) rs . 400\nb ) rs . 300\nc ) rs . 500\nd ) rs . 350\ne ) none of these\nAnswer: a\n\n",
            "Question: average age of students of an adult school is 40 years . 120 new students whose average age is 32 years joined the school . as a result the average age is decreased by 4 years . find the number of students of the school after joining of the new students .\n"
                    + "a ) 1200\nb ) 120\nc ) 360\nd ) 240\ne ) none of these\nAnswer: d\n\n",
            "Question: sophia finished 2 / 3 of a book . she calculated that she finished 90 more pages than she has yet to read . how long is her book ?\n"
                    + "a ) 229\nb ) 270\nc ) 877\nd ) 266\ne ) 281\nAnswer: b\n\n",
            "Question: 120 is what percent of 50 ?\n"
                    + "a ) 5 %\nb ) 240 %\nc ) 50 %\nd ) 2 %\ne ) 500 %\nAnswer: b\n\n",
            "Question: there are 10 girls and 20 boys in a classroom . what is the ratio of girls to boys ?\n"
                    + "a ) 1 / 2\nb ) 1 / 3\nc ) 1 / 5\nd ) 10 / 30\ne ) 2 / 5\nAnswer: a\n\n");
    private static final String CONFINEMENT_INSTRUCTIONS =
            "Output 'a', 'b', 'c', or 'd'. Full answer not needed.";
    private final Map<String, List<Golden>> taskGoldens;
    private final Integer nProblemsPerTask;
    private final int nShots;
    private List<BenchmarkTaskPrediction> predictions;
    private List<BenchmarkTaskScore> taskScores;
    private Double overallScore;

    public MathQA(Map<String, List<Golden>> taskGoldens) {
        this(taskGoldens, null);
    }

    public MathQA(Map<String, List<Golden>> taskGoldens, Integer nProblemsPerTask) {
        this(taskGoldens, nProblemsPerTask, 5);
    }

    public MathQA(Map<String, List<Golden>> taskGoldens, Integer nProblemsPerTask, int nShots) {
        if (taskGoldens == null || taskGoldens.isEmpty()) {
            throw new IllegalArgumentException("'taskGoldens' must not be empty");
        }
        if (nProblemsPerTask != null && nProblemsPerTask < 1) {
            throw new IllegalArgumentException("'nProblemsPerTask' must be positive");
        }
        if (nShots < 0 || nShots > 5) {
            throw new IllegalArgumentException("MathQA only supports nShots between 0 and 5");
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
            prompt.append(N_SHOT_EXAMPLES.get(i));
        }
        return prompt.append(input).append("\n\n").append(CONFINEMENT_INSTRUCTIONS).toString();
    }
}
