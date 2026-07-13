package vn.uytinmang.projectos.attendance.domain;

import java.time.LocalDate;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ShiftRepository extends JpaRepository<Shift, UUID> { Page<Shift> findByOrganizationId(UUID organizationId, Pageable pageable); Optional<Shift> findByIdAndOrganizationId(UUID id, UUID organizationId); }
interface WorkScheduleRepository extends JpaRepository<WorkSchedule, UUID> { Page<WorkSchedule> findByOrganizationId(UUID organizationId, Pageable pageable); Optional<WorkSchedule> findByIdAndOrganizationId(UUID id, UUID organizationId); }
interface ScheduleSlotRepository extends JpaRepository<ScheduleSlot, UUID> { List<ScheduleSlot> findByScheduleId(UUID scheduleId); Optional<ScheduleSlot> findByScheduleIdAndDayOfWeek(UUID scheduleId, short dayOfWeek); long countByShiftId(UUID shiftId); void deleteByScheduleId(UUID scheduleId); }
interface ScheduleAssignmentRepository extends JpaRepository<ScheduleAssignment, UUID> {
    Page<ScheduleAssignment> findByOrganizationId(UUID organizationId, Pageable pageable);
    long countByScheduleId(UUID scheduleId);
    Optional<ScheduleAssignment> findByIdAndOrganizationId(UUID id, UUID organizationId);
    @Query("select a from ScheduleAssignment a where a.organizationId=:organizationId and a.employeeId=:employeeId and a.effectiveFrom <= :date and (a.effectiveTo is null or a.effectiveTo >= :date) order by a.effectiveFrom desc")
    List<ScheduleAssignment> findActive(@Param("organizationId") UUID organizationId,@Param("employeeId") UUID employeeId,@Param("date") LocalDate date);
    @Query("select a from ScheduleAssignment a where a.organizationId=:organizationId and a.employeeId=:employeeId and (:excludeId is null or a.id <> :excludeId) and (:toDate is null or a.effectiveFrom <= :toDate) and (a.effectiveTo is null or a.effectiveTo >= :fromDate)")
    List<ScheduleAssignment> findOverlaps(@Param("organizationId") UUID organizationId,@Param("employeeId") UUID employeeId,@Param("fromDate") LocalDate fromDate,@Param("toDate") LocalDate toDate,@Param("excludeId") UUID excludeId);
}
interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, UUID> {
    Optional<AttendanceRecord> findByEmployeeIdAndWorkDate(UUID employeeId, LocalDate workDate);
    Optional<AttendanceRecord> findFirstByEmployeeIdAndStatusOrderByWorkDateDesc(UUID employeeId, AttendanceRecord.Status status);
    @Query("select r from AttendanceRecord r where r.organizationId=:organizationId and (:employeeId is null or r.employeeId=:employeeId) and r.workDate between :from and :to order by r.workDate desc")
    Page<AttendanceRecord> findRange(@Param("organizationId") UUID organizationId,@Param("employeeId") UUID employeeId,@Param("from") LocalDate from,@Param("to") LocalDate to, Pageable pageable);
}
interface AttendanceAdjustmentRepository extends JpaRepository<AttendanceAdjustment, UUID> { Page<AttendanceAdjustment> findByOrganizationId(UUID organizationId, Pageable pageable); Page<AttendanceAdjustment> findByOrganizationIdAndEmployeeId(UUID organizationId, UUID employeeId, Pageable pageable); Optional<AttendanceAdjustment> findByIdAndOrganizationId(UUID id, UUID organizationId); }
interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID> { Page<LeaveRequest> findByOrganizationId(UUID organizationId, Pageable pageable); Page<LeaveRequest> findByOrganizationIdAndEmployeeId(UUID organizationId, UUID employeeId, Pageable pageable); Optional<LeaveRequest> findByIdAndOrganizationId(UUID id, UUID organizationId); }
