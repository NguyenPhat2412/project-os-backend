package com.projectos.backend.operations.application;

/** Backend-owned classification for contract expiry warnings. */
public final class ContractWarningPolicy {
    private ContractWarningPolicy() {}

    public static String urgency(int daysRemaining) {
        if (daysRemaining < 0) return "overdue";
        if (daysRemaining <= 15) return "critical";
        if (daysRemaining <= 30) return "warning";
        return "upcoming";
    }
}
