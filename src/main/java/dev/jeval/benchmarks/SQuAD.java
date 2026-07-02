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
    private static final String CONFINEMENT_INSTRUCTIONS =
            "Output the answer, which should a text segment taken from the context.";
    private static final List<String> N_SHOT_EXAMPLES = List.of(
            """
            Context: After Hurricane Katrina in 2005, Beyonce and Rowland founded the Survivor Foundation to provide transitional housing for victims in the Houston area, to which Beyonce contributed an initial $250,000. The foundation has since expanded to work with other charities in the city, and also provided relief following Hurricane Ike three years later.
            Question: What did Beyonce and Rowland found in 2005?
            Answer: the Survivor Foundation

            """,
            """
            Context: With his health further deteriorating, Chopin desired to have a family member with him. In June 1849 his sister Ludwika came to Paris with her husband and daughter, and in September, supported by a loan from Jane Stirling, he took an apartment at Place Vendome 12. After 15 October, when his condition took a marked turn for the worse, only a handful of his closest friends remained with him.
            Question: Which family member came to Paris in June 1849?
            Answer: his sister

            """,
            """
            Context: Twilight Princess received the awards for Best Artistic Design, Best Original Score, and Best Use of Sound from IGN for its GameCube version. Both IGN and Nintendo Power gave Twilight Princess the awards for Best Graphics and Best Story. Twilight Princess received Game of the Year awards from GameTrailers, 1UP.com, Electronic Gaming Monthly, Game Informer, Games Radar, GameSpy, Spacey Awards, X-Play and Nintendo Power. It was also given awards for Best Adventure Game from the Game Critics Awards, X-Play, IGN, GameTrailers, 1UP.com, and Nintendo Power. The game was considered the Best Console Game by the Game Critics Awards and GameSpy.
            Question: What award did Game Critics Awards and GameSpy give Twilight Princess?
            Answer: Best Console Game

            """,
            """
            Context: The city and surrounding area suffered the bulk of the economic damage and largest loss of human life in the aftermath of the September 11, 2001 attacks when 10 of the 19 terrorists associated with Al-Qaeda piloted American Airlines Flight 11 into the North Tower of the World Trade Center and United Airlines Flight 175 into the South Tower of the World Trade Center, and later destroyed them, killing 2,192 civilians, 343 firefighters, and 71 law enforcement officers who were in the towers and in the surrounding area.
            Question: How many firefighters died in the World Trade Center attack?
            Answer: 343

            """,
            """
            Context: Greenhouses convert solar light to heat, enabling year-round production and the growth of specialty crops and other plants not naturally suited to the local climate.
            Question: What do greenhouses do with solar energy?
            Answer: convert solar light to heat

            """);
    private final Map<String, List<Golden>> taskGoldens;
    private final Integer nProblemsPerTask;
    private final int nShots;
    private final EvaluationModel evaluationModel;
    private List<BenchmarkTaskPrediction> predictions;
    private List<BenchmarkTaskScore> taskScores;
    private Double overallScore;

    public SQuAD(Map<String, List<Golden>> taskGoldens, EvaluationModel evaluationModel) {
        this(taskGoldens, evaluationModel, null);
    }

    public SQuAD(Map<String, List<Golden>> taskGoldens, EvaluationModel evaluationModel, Integer nProblemsPerTask) {
        this(taskGoldens, evaluationModel, nProblemsPerTask, 5);
    }

    public SQuAD(Map<String, List<Golden>> taskGoldens, EvaluationModel evaluationModel, Integer nProblemsPerTask,
            int nShots) {
        if (taskGoldens == null || taskGoldens.isEmpty()) {
            throw new IllegalArgumentException("'taskGoldens' must not be empty");
        }
        if (evaluationModel == null) {
            throw new IllegalArgumentException("'evaluationModel' must not be null");
        }
        if (nProblemsPerTask != null && nProblemsPerTask < 1) {
            throw new IllegalArgumentException("'nProblemsPerTask' must be positive");
        }
        if (nShots < 0 || nShots > N_SHOT_EXAMPLES.size()) {
            throw new IllegalArgumentException("'nShots' must be between 0 and " + N_SHOT_EXAMPLES.size());
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
        this.nShots = nShots;
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
                var prediction = model.generate(prompt(golden.input()));
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

    private String prompt(String input) {
        var prompt = new StringBuilder();
        for (var i = 0; i < nShots; i++) {
            prompt.append(N_SHOT_EXAMPLES.get(i));
        }
        return prompt.append(input).append("\n\n").append(CONFINEMENT_INSTRUCTIONS).toString();
    }

    private List<Golden> limited(List<Golden> goldens) {
        if (nProblemsPerTask == null || nProblemsPerTask >= goldens.size()) {
            return goldens;
        }
        return goldens.subList(0, nProblemsPerTask);
    }
}
