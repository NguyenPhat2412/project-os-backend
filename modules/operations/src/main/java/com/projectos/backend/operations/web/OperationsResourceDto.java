package com.projectos.backend.operations.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Stable, typed public representation for schema-backed Operations records.
 * Resource-specific fields are explicit so the controller never exposes a
 * JDBC map or an entity directly.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record OperationsResourceDto(
        String id,
        UUID organizationId,
        String code,
        String name,
        String title,
        String description,
        String category,
        String status,
        String notes,
        String slug,
        UUID employeeId,
        UUID employeeUuid,
        String employeeCode,
        String employeeName,
        UUID departmentId,
        UUID departmentUuid,
        String department,
        String contractCode,
        String contractType,
        String effectiveDate,
        String signDate,
        String expireDate,
        BigDecimal baseSalary,
        BigDecimal allowances,
        BigDecimal performanceBonus,
        String courseCode,
        String instructor,
        String startDate,
        String endDate,
        String location,
        Integer sessions,
        Integer attendeesCount,
        BigDecimal cost,
        String emailAddress,
        String displayName,
        UUID assignedEmployeeId,
        String mailboxType,
        Integer quotaTotalMb,
        String forwardTo,
        List<String> aliases,
        String period,
        String employees,
        UUID leaderId,
        Integer membersCount,
        Integer displayOrder,
        Boolean isActive,
        String branchType,
        String address,
        String phone,
        String email,
        String managerName,
        Integer employeesCount,
        String resignationDate,
        String lastWorkingDate,
        String reasonType,
        String reasonDetail,
        String handoverReceiverName,
        String assetsNotes,
        JsonNode checklist,
        BigDecimal unpaidSalaryAmount,
        BigDecimal unusedLeaveDays,
        BigDecimal unusedLeaveCompensation,
        BigDecimal severancePay,
        BigDecimal totalSettlementAmount,
        String decisionNumber,
        String decisionDate,
        BigDecimal gpsLatitude,
        BigDecimal gpsLongitude,
        Integer gpsRadiusMeters,
        Integer year,
        BigDecimal standardQuota,
        BigDecimal seniorityBonus,
        BigDecimal carriedOver,
        BigDecimal totalEntitled,
        BigDecimal usedDays,
        BigDecimal remainingDays,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy,
        Integer warningDaysRemaining,
        String urgency
) {
}
