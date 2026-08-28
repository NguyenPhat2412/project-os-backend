package com.projectos.backend.operations.email;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class EmailCampaignServiceTest {
    private final JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    private final EmailCampaignService service = new EmailCampaignService(jdbc, new EmailTemplateSanitizer());

    @Test
    void previewExcludesInactiveAndMissingEmailEmployees() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
                Map.of("id", UUID.randomUUID(), "full_name", "A", "code", "E01", "email", "a@example.test", "status", "ACTIVE"),
                Map.of("id", UUID.randomUUID(), "full_name", "B", "code", "E02", "email", "", "status", "ACTIVE"),
                Map.of("id", UUID.randomUUID(), "full_name", "C", "code", "E03", "email", "c@example.test", "status", "INACTIVE")));

        var result = service.preview(UUID.randomUUID(), UUID.randomUUID(), true,
                new EmailCampaignContracts.PreviewRequest("Subject", "<p>Hello</p>", null, List.of(), null));

        assertEquals(1, result.validRecipients().size());
        assertEquals(2, result.excludedRecipients().size());
    }

    @Test
    void previewRejectsBlankSubject() {
        assertThrows(RuntimeException.class, () -> service.preview(UUID.randomUUID(), UUID.randomUUID(), true,
                new EmailCampaignContracts.PreviewRequest("", "body", null, List.of(), null)));
    }
}
