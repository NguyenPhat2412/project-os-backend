SET search_path = public;

-- V17 closes the remaining integrity gaps without rewriting existing rows.
-- Every NOT VALID constraint is validated in this migration. If a restored
-- database contains invalid data, the migration must fail and be remediated
-- explicitly; no cleanup or synthetic data is performed here.

ALTER TABLE organizations
    ADD CONSTRAINT organizations_created_by_fk
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL NOT VALID;

ALTER TABLE company_policies
    ADD CONSTRAINT company_policies_updated_by_fk
    FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE RESTRICT NOT VALID;

ALTER TABLE employee_compensations
    ADD CONSTRAINT employee_compensations_updated_by_fk
    FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE RESTRICT NOT VALID;

ALTER TABLE resource_records
    ADD CONSTRAINT resource_records_created_by_fk
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT NOT VALID;

ALTER TABLE organization_positions
    ADD CONSTRAINT organization_positions_created_by_fk
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL NOT VALID,
    ADD CONSTRAINT organization_positions_updated_by_fk
    FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL NOT VALID;

ALTER TABLE training_courses
    ADD CONSTRAINT training_courses_created_by_fk
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL NOT VALID,
    ADD CONSTRAINT training_courses_updated_by_fk
    FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL NOT VALID;

ALTER TABLE company_regulations
    ADD CONSTRAINT company_regulations_created_by_fk
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL NOT VALID,
    ADD CONSTRAINT company_regulations_updated_by_fk
    FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL NOT VALID;

ALTER TABLE company_email_accounts
    ADD CONSTRAINT company_email_accounts_created_by_fk
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL NOT VALID,
    ADD CONSTRAINT company_email_accounts_updated_by_fk
    FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL NOT VALID;

ALTER TABLE report_definitions
    ADD CONSTRAINT report_definitions_created_by_fk
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL NOT VALID,
    ADD CONSTRAINT report_definitions_updated_by_fk
    FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL NOT VALID;

ALTER TABLE organization_settings
    ADD CONSTRAINT organization_settings_updated_by_fk
    FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE RESTRICT NOT VALID;

ALTER TABLE attendance_records
    ADD CONSTRAINT attendance_records_location_bounds_ck
    CHECK (
        (check_in_latitude IS NULL OR check_in_latitude BETWEEN -90 AND 90)
        AND (check_in_longitude IS NULL OR check_in_longitude BETWEEN -180 AND 180)
        AND (check_out_latitude IS NULL OR check_out_latitude BETWEEN -90 AND 90)
        AND (check_out_longitude IS NULL OR check_out_longitude BETWEEN -180 AND 180)
    ) NOT VALID,
    ADD CONSTRAINT attendance_records_location_nonnegative_ck
    CHECK (
        (check_in_accuracy_meters IS NULL OR check_in_accuracy_meters >= 0)
        AND (check_in_distance_meters IS NULL OR check_in_distance_meters >= 0)
        AND (check_out_accuracy_meters IS NULL OR check_out_accuracy_meters >= 0)
        AND (check_out_distance_meters IS NULL OR check_out_distance_meters >= 0)
    ) NOT VALID,
    ADD CONSTRAINT attendance_records_work_mode_ck
    CHECK (work_mode IN ('OFFICE', 'FIELD_TRIP', 'REMOTE')) NOT VALID,
    ADD CONSTRAINT attendance_records_work_date_required_ck
    CHECK (work_date IS NOT NULL) NOT VALID;

ALTER TABLE organization_branches
    ADD CONSTRAINT organization_branches_gps_bounds_ck
    CHECK (
        (gps_latitude IS NULL OR gps_latitude BETWEEN -90 AND 90)
        AND (gps_longitude IS NULL OR gps_longitude BETWEEN -180 AND 180)
    ) NOT VALID;

ALTER TABLE organization_positions
    ADD CONSTRAINT organization_positions_salary_nonnegative_ck
    CHECK (standard_salary IS NULL OR standard_salary >= 0) NOT VALID;

ALTER TABLE training_courses
    ADD CONSTRAINT training_courses_cost_attendees_nonnegative_ck
    CHECK (
        (attendees_count IS NULL OR attendees_count >= 0)
        AND (cost IS NULL OR cost >= 0)
    ) NOT VALID;

ALTER TABLE shifts
    ADD CONSTRAINT shifts_nonnegative_values_ck
    CHECK (
        break_minutes >= 0
        AND coalesce(standard_working_hours, 0) >= 0
        AND coalesce(flexible_start_minutes, 0) >= 0
        AND coalesce(early_leave_grace_minutes, 0) >= 0
        AND coalesce(allowed_radius_meters, 0) > 0
        AND coalesce(shift_allowance, 0) >= 0
        AND coalesce(ot_rate, 0) >= 0
    ) NOT VALID;

ALTER TABLE projects
    ADD CONSTRAINT projects_organization_required_ck
    CHECK (organization_id IS NOT NULL) NOT VALID,
    ADD CONSTRAINT projects_team_size_nonnegative_ck
    CHECK (team_size IS NULL OR team_size >= 0) NOT VALID;

ALTER TABLE leave_requests
    ADD CONSTRAINT leave_requests_total_days_nonnegative_ck
    CHECK (total_days IS NULL OR total_days >= 0) NOT VALID;

ALTER TABLE outbox_events
    ADD CONSTRAINT outbox_events_attempts_nonnegative_ck
    CHECK (attempts >= 0) NOT VALID;

ALTER TABLE attendance_records VALIDATE CONSTRAINT attendance_records_location_bounds_ck;
ALTER TABLE attendance_records VALIDATE CONSTRAINT attendance_records_location_nonnegative_ck;
ALTER TABLE attendance_records VALIDATE CONSTRAINT attendance_records_work_mode_ck;
ALTER TABLE attendance_records VALIDATE CONSTRAINT attendance_records_work_date_required_ck;
ALTER TABLE organization_branches VALIDATE CONSTRAINT organization_branches_gps_bounds_ck;
ALTER TABLE organization_positions VALIDATE CONSTRAINT organization_positions_salary_nonnegative_ck;
ALTER TABLE training_courses VALIDATE CONSTRAINT training_courses_cost_attendees_nonnegative_ck;
ALTER TABLE shifts VALIDATE CONSTRAINT shifts_nonnegative_values_ck;
ALTER TABLE projects VALIDATE CONSTRAINT projects_organization_required_ck;
ALTER TABLE projects VALIDATE CONSTRAINT projects_team_size_nonnegative_ck;
ALTER TABLE leave_requests VALIDATE CONSTRAINT leave_requests_total_days_nonnegative_ck;
ALTER TABLE outbox_events VALIDATE CONSTRAINT outbox_events_attempts_nonnegative_ck;

ALTER TABLE organizations VALIDATE CONSTRAINT organizations_created_by_fk;
ALTER TABLE company_policies VALIDATE CONSTRAINT company_policies_updated_by_fk;
ALTER TABLE employee_compensations VALIDATE CONSTRAINT employee_compensations_updated_by_fk;
ALTER TABLE resource_records VALIDATE CONSTRAINT resource_records_created_by_fk;
ALTER TABLE organization_positions VALIDATE CONSTRAINT organization_positions_created_by_fk;
ALTER TABLE organization_positions VALIDATE CONSTRAINT organization_positions_updated_by_fk;
ALTER TABLE training_courses VALIDATE CONSTRAINT training_courses_created_by_fk;
ALTER TABLE training_courses VALIDATE CONSTRAINT training_courses_updated_by_fk;
ALTER TABLE company_regulations VALIDATE CONSTRAINT company_regulations_created_by_fk;
ALTER TABLE company_regulations VALIDATE CONSTRAINT company_regulations_updated_by_fk;
ALTER TABLE company_email_accounts VALIDATE CONSTRAINT company_email_accounts_created_by_fk;
ALTER TABLE company_email_accounts VALIDATE CONSTRAINT company_email_accounts_updated_by_fk;
ALTER TABLE report_definitions VALIDATE CONSTRAINT report_definitions_created_by_fk;
ALTER TABLE report_definitions VALIDATE CONSTRAINT report_definitions_updated_by_fk;
ALTER TABLE organization_settings VALIDATE CONSTRAINT organization_settings_updated_by_fk;

CREATE INDEX IF NOT EXISTS attendance_records_employee_organization_idx
    ON attendance_records (employee_id, organization_id);
CREATE INDEX IF NOT EXISTS attendance_adjustments_employee_organization_idx
    ON attendance_adjustments (employee_id, organization_id);
CREATE INDEX IF NOT EXISTS leave_requests_employee_organization_idx
    ON leave_requests (employee_id, organization_id);
CREATE INDEX IF NOT EXISTS schedule_assignments_employee_organization_idx
    ON schedule_assignments (employee_id, organization_id);
CREATE INDEX IF NOT EXISTS permission_group_members_group_organization_idx
    ON permission_group_members (group_id, organization_id);

CREATE OR REPLACE FUNCTION public.reject_append_only_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'append_only_table_mutation: %.%', TG_TABLE_SCHEMA, TG_TABLE_NAME
        USING ERRCODE = 'restrict_violation';
END;
$$;

DROP TRIGGER IF EXISTS activity_events_append_only_trigger ON activity_events;
CREATE TRIGGER activity_events_append_only_trigger
    BEFORE UPDATE OR DELETE ON activity_events
    FOR EACH ROW EXECUTE FUNCTION public.reject_append_only_mutation();

DROP TRIGGER IF EXISTS organization_audit_logs_append_only_trigger ON organization_audit_logs;
CREATE TRIGGER organization_audit_logs_append_only_trigger
    BEFORE UPDATE OR DELETE ON organization_audit_logs
    FOR EACH ROW EXECUTE FUNCTION public.reject_append_only_mutation();
