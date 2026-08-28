package com.projectos.backend.organization.onboarding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OnboardingTokenServiceTest {
    private final OnboardingTokenService tokens = new OnboardingTokenService();

    @Test
    void digestsAreStableAndAdministratorRolesAreRejected() {
        assertEquals(tokens.digest("candidate-token"), tokens.digest("candidate-token"));
        assertFalse(tokens.isAllowedTargetRole("ROLE_ADMIN"));
        assertFalse(tokens.isAllowedTargetRole("ROLE_SUPER_ADMIN"));
        assertTrue(tokens.isAllowedTargetRole("ROLE_EMPLOYEE"));
        assertTrue(tokens.isAllowedTargetRole("ROLE_DEPT_LEAD"));
    }
}
