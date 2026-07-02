package dev.jeval.benchmarks;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class BenchmarkTaskEnumTest {

    @Test
    void mathQaTaskValuesMatchDeepEval() {
        assertEquals("probability", MathQATask.PROBABILITY.value());
        assertEquals("geometry", MathQATask.GEOMETRY.value());
        assertEquals("physics", MathQATask.PHYSICS.value());
        assertEquals("gain", MathQATask.GAIN.value());
        assertEquals("general", MathQATask.GENERAL.value());
        assertEquals("other", MathQATask.OTHER.value());
    }

    @Test
    void logiQaTaskValuesMatchDeepEval() {
        assertEquals("Categorical Reasoning", LogiQATask.CATEGORICAL_REASONING.value());
        assertEquals("Sufficient Conditional Reasoning", LogiQATask.SUFFICIENT_CONDITIONAL_REASONING.value());
        assertEquals("Necessary Conditional Reasoning", LogiQATask.NECESSARY_CONDITIONAL_REASONING.value());
        assertEquals("Disjunctive Reasoning", LogiQATask.DISJUNCTIVE_REASONING.value());
        assertEquals("Conjunctive Reasoning", LogiQATask.CONJUNCTIVE_REASONING.value());
    }

    @Test
    void bbqTaskValuesMatchDeepEval() {
        assertEquals(List.of(
                "Age",
                "Disability_status",
                "Gender_identity",
                "Nationality",
                "Physical_appearance",
                "Race_ethnicity",
                "Race_x_SES",
                "Race_x_gender",
                "Religion",
                "SES",
                "Sexual_orientation"),
                Arrays.stream(BBQTask.values()).map(BBQTask::value).toList());
    }

    @Test
    void equityMedQaTaskValuesMatchDeepEval() {
        assertEquals(List.of(
                "ehai",
                "fbrt_llm",
                "fbrt_llm_661_sampled",
                "fbrt_manual",
                "mixed_mmqa_omaq",
                "multimedqa",
                "omaq",
                "omiye_et_al",
                "trinds"),
                Arrays.stream(EquityMedQATask.values()).map(EquityMedQATask::value).toList());
    }

    @Test
    void truthfulQaTaskValuesMatchDeepEval() {
        assertEquals(List.of(
                "Language",
                "Misquotations",
                "Nutrition",
                "Fiction",
                "Science",
                "Proverbs",
                "Mandela Effect",
                "Indexical Error: Identity",
                "Confusion: Places",
                "Economics",
                "Psychology",
                "Confusion: People",
                "Education",
                "Conspiracies",
                "Subjective",
                "Misconceptions",
                "Indexical Error: Other",
                "Myths and Fairytales",
                "Indexical Error: Time",
                "Misconceptions: Topical",
                "Politics",
                "Finance",
                "Indexical Error: Location",
                "Confusion: Other",
                "Law",
                "Distraction",
                "History",
                "Weather",
                "Statistics",
                "Misinformation",
                "Superstitions",
                "Logical Falsehood",
                "Health",
                "Stereotypes",
                "Religion",
                "Advertising",
                "Sociology",
                "Paranormal"),
                Arrays.stream(TruthfulQATask.values()).map(TruthfulQATask::value).toList());
    }
}
