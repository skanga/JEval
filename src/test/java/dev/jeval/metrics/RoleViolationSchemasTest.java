package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class RoleViolationSchemasTest {

    @Test
    void parsesViolationsVerdictsAndReasonFromModelJson() {
        var violations = RoleViolationSchemas.parseRoleViolations(
                "{\"role_violations\":[\"I am your doctor.\"]}");
        var verdicts = RoleViolationSchemas.parseVerdicts("""
                {"verdicts":[
                  {"verdict":"yes","reason":"Claims to be a doctor."},
                  {"verdict":"no","reason":"Still within role."}
                ]}
                """);
        var reason = RoleViolationSchemas.parseScoreReason("{\"reason\":\"One role violation was found.\"}");

        assertAll(
                () -> assertEquals(List.of("I am your doctor."), violations.roleViolations()),
                () -> assertEquals(2, verdicts.verdicts().size()),
                () -> assertEquals("yes", verdicts.verdicts().getFirst().verdict()),
                () -> assertEquals("Still within role.", verdicts.verdicts().get(1).reason()),
                () -> assertEquals("One role violation was found.", reason.reason()));
    }

    @Test
    void rejectsMissingOrInvalidFieldsLikeDeepEval() {
        assertThrows(IllegalArgumentException.class, () -> RoleViolationSchemas.parseRoleViolations("{}"));
        assertThrows(IllegalArgumentException.class, () -> RoleViolationSchemas.parseRoleViolations("{\"role_violations\":\"one\"}"));
        assertThrows(IllegalArgumentException.class, () -> RoleViolationSchemas.parseRoleViolations("{\"role_violations\":[1]}"));
        assertThrows(IllegalArgumentException.class, () -> RoleViolationSchemas.parseVerdicts("{}"));
        assertThrows(IllegalArgumentException.class, () -> RoleViolationSchemas.parseVerdicts("{\"verdicts\":\"yes\"}"));
        assertThrows(IllegalArgumentException.class, () -> RoleViolationSchemas.parseVerdicts("{\"verdicts\":[{\"reason\":\"ok\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> RoleViolationSchemas.parseVerdicts("{\"verdicts\":[{\"verdict\":\"yes\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> RoleViolationSchemas.parseVerdicts("{\"verdicts\":[{\"verdict\":1,\"reason\":\"ok\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> RoleViolationSchemas.parseVerdicts("{\"verdicts\":[{\"verdict\":\"yes\",\"reason\":1}]}"));
        assertThrows(IllegalArgumentException.class, () -> RoleViolationSchemas.parseScoreReason("{}"));
        assertThrows(IllegalArgumentException.class, () -> RoleViolationSchemas.parseScoreReason("{\"reason\":1}"));
    }
}
