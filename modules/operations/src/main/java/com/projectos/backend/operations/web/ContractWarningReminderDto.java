package com.projectos.backend.operations.web;

import java.util.UUID;

/** Stable response for queuing a contract-warning notification. */
public record ContractWarningReminderDto(
        UUID id,
        String contractId,
        String status
) {
}
