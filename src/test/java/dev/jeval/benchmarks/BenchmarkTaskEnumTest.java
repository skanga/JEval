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
    void bigBenchHardTaskValuesMatchDeepEval() {
        assertEquals(List.of(
                "boolean_expressions",
                "causal_judgement",
                "date_understanding",
                "disambiguation_qa",
                "dyck_languages",
                "formal_fallacies",
                "geometric_shapes",
                "hyperbaton",
                "logical_deduction_five_objects",
                "logical_deduction_seven_objects",
                "logical_deduction_three_objects",
                "movie_recommendation",
                "multistep_arithmetic_two",
                "navigate",
                "object_counting",
                "penguins_in_a_table",
                "reasoning_about_colored_objects",
                "ruin_names",
                "salient_translation_error_detection",
                "snarks",
                "sports_understanding",
                "temporal_sequences",
                "tracking_shuffled_objects_five_objects",
                "tracking_shuffled_objects_seven_objects",
                "tracking_shuffled_objects_three_objects",
                "web_of_lies",
                "word_sorting"),
                Arrays.stream(BigBenchHardTask.values()).map(BigBenchHardTask::value).toList());
    }

    @Test
    void squadTaskValuesMatchDeepEval() {
        assertEquals(List.of(
                "Pharmacy",
                "Normans",
                "Huguenot",
                "Doctor_Who",
                "1973_oil_crisis",
                "Computational_complexity_theory",
                "Warsaw",
                "American_Broadcasting_Company",
                "Chloroplast",
                "Apollo_program",
                "Teacher",
                "Martin_Luther",
                "Economic_inequality",
                "Yuan_dynasty",
                "Scottish_Parliament",
                "Islamism",
                "United_Methodist_Church",
                "Immune_system",
                "Newcastle_upon_Tyne",
                "Ctenophora",
                "Fresno,_California",
                "Steam_engine",
                "Packet_switching",
                "Force",
                "Jacksonville,_Florida",
                "European_Union_law",
                "Super_Bowl_50",
                "Victoria_and_Albert_Museum",
                "Black_Death",
                "Construction",
                "Sky_(United_Kingdom)",
                "University_of_Chicago",
                "Victoria_(Australia)",
                "French_and_Indian_War",
                "Imperialism",
                "Private_school",
                "Geology",
                "Harvard_University",
                "Rhine",
                "Prime_number",
                "Intergovernmental_Panel_on_Climate_Change",
                "Amazon_rainforest",
                "Kenya",
                "Southern_California",
                "Nikola_Tesla",
                "Civil_disobedience",
                "Genghis_Khan",
                "Oxygen"),
                Arrays.stream(SQuADTask.values()).map(SQuADTask::value).toList());
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
