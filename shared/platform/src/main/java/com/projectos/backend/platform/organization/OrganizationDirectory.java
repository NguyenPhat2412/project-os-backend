package com.projectos.backend.platform.organization;

import java.util.Set;
import java.util.UUID;

/** In-process read port for organization-owned identity and access data. */
public interface OrganizationDirectory {
    Employee employeeByUser(UUID organizationId, UUID userId);

    Employee employee(UUID organizationId, UUID employeeId);

    Access access(UUID organizationId, UUID userId);

    String timezone(UUID organizationId);

    default AttendancePolicy attendancePolicy(UUID organizationId) {
        return new AttendancePolicy(false, 0, 0, 0, "");
    }

    default Set<EmployeeDetails> directReports(UUID organizationId, UUID supervisorId) {
        return Set.of();
    }

    default EmployeeDetails employeeDetails(UUID organizationId, UUID employeeId) {
        Employee employee = employee(organizationId, employeeId);
        return new EmployeeDetails(employee.id(), employee.organizationId(), employee.supervisorId(),
                employee.userId(), null, null, employee.status());
    }

    record Employee(UUID id, UUID organizationId, UUID supervisorId, UUID userId, String status, String code) {
        public Employee(UUID id, UUID organizationId, UUID supervisorId, UUID userId, String status) {
            this(id, organizationId, supervisorId, userId, status, null);
        }
    }

    record EmployeeDetails(UUID id, UUID organizationId, UUID supervisorId, UUID userId,
                           String fullName, String title, String status) {}

    record Access(String timezone, String role) {}

    record AttendancePolicy(boolean configured, double latitude, double longitude,
                            int radiusMeters, String officeName) {}
}
