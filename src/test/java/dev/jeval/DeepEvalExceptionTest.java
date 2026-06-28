package dev.jeval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DeepEvalExceptionTest {

    @Test
    void missingTestCaseParamsExceptionExtendsDeepEvalException() {
        var error = assertThrows(DeepEvalException.class, () -> {
            throw new MissingTestCaseParamsException("missing");
        });

        assertEquals("missing", error.getMessage());
    }

    @Test
    void mismatchedInputsExceptionExtendsDeepEvalException() {
        var error = assertThrows(DeepEvalException.class, () -> {
            throw new MismatchedTestCaseInputsException("mismatch");
        });

        assertEquals("mismatch", error.getMessage());
    }

    @Test
    void noMetricsExceptionExtendsDeepEvalException() {
        var error = assertThrows(DeepEvalException.class, () -> {
            throw new NoMetricsException("no metrics");
        });

        assertEquals("no metrics", error.getMessage());
    }

    @Test
    void userAppExceptionIsSeparateFromDeepEvalException() {
        var error = assertThrows(UserAppException.class, () -> {
            throw new UserAppException("user failed");
        });

        assertEquals("user failed", error.getMessage());
        assertFalse(DeepEvalException.class.isAssignableFrom(error.getClass()));
    }
}
