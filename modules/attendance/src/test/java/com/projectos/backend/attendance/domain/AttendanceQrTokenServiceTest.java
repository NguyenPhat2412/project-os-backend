package com.projectos.backend.attendance.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AttendanceQrTokenServiceTest {

    private AttendanceQrTokenService qrTokenService;
    private final UUID orgId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        qrTokenService = new AttendanceQrTokenService(60, "test-secret-that-is-at-least-32-bytes-long");
    }

    @Test
    void testPersonalQrTokenIssueAndMatch() {
        var token = qrTokenService.issue(orgId, employeeId, "EMP-001");
        assertNotNull(token);
        assertNotNull(token.value());
        assertTrue(token.expiresInSeconds() > 0);

        // Matching correct org and employee
        assertTrue(qrTokenService.matches(token.value(), orgId, employeeId));

        // Reject different employee
        assertFalse(qrTokenService.matches(token.value(), orgId, UUID.randomUUID()));

        // Reject different org
        assertFalse(qrTokenService.matches(token.value(), UUID.randomUUID(), employeeId));
    }

    @Test
    void testSiteQrTokenIssueAndMatch() {
        var siteToken = qrTokenService.issueSite(orgId);
        assertNotNull(siteToken);
        assertNotNull(siteToken.value());
        assertEquals(60, siteToken.expiresInSeconds());

        // Site token matches for the org
        assertTrue(qrTokenService.matchesSite(siteToken.value(), orgId));
        assertTrue(qrTokenService.matches(siteToken.value(), orgId, employeeId));

        // Rejects different org
        assertFalse(qrTokenService.matchesSite(siteToken.value(), UUID.randomUUID()));
        assertFalse(qrTokenService.matches(siteToken.value(), UUID.randomUUID(), employeeId));
    }

    @Test
    void testInvalidTokenValues() {
        assertFalse(qrTokenService.matches(null, orgId, employeeId));
        assertFalse(qrTokenService.matches("", orgId, employeeId));
        assertFalse(qrTokenService.matches("invalid.token.structure", orgId, employeeId));
        assertFalse(qrTokenService.matchesSite(null, orgId));
        assertFalse(qrTokenService.matchesSite("fake-token", orgId));
    }
}
