package dev.jeval.benchmarks;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
