SET search_path = public;

-- PostgreSQL does not create child-side indexes for foreign keys. These
-- indexes keep tenant joins and parent-row updates/deletes predictable as HRM
-- data grows.
CREATE INDEX IF NOT EXISTS asset_assignments_employee_fk_idx
    ON asset_assignments (employee_id);
CREATE INDEX IF NOT EXISTS asset_assignments_handover_fk_idx
    ON asset_assignments (handover_id);
CREATE INDEX IF NOT EXISTS asset_handover_orders_employee_fk_idx
    ON asset_handover_orders (employee_id);
CREATE INDEX IF NOT EXISTS attendance_adjustments_reviewer_fk_idx
    ON attendance_adjustments (reviewer_id);
CREATE INDEX IF NOT EXISTS attendance_records_organization_fk_idx
    ON attendance_records (organization_id);
CREATE INDEX IF NOT EXISTS company_email_accounts_created_by_fk_idx
    ON company_email_accounts (created_by);
CREATE INDEX IF NOT EXISTS company_email_accounts_department_org_fk_idx
    ON company_email_accounts (department_id, organization_id);
CREATE INDEX IF NOT EXISTS company_email_accounts_employee_org_fk_idx
    ON company_email_accounts (assigned_employee_id, organization_id);
CREATE INDEX IF NOT EXISTS company_email_accounts_updated_by_fk_idx
    ON company_email_accounts (updated_by);
CREATE INDEX IF NOT EXISTS company_policies_updated_by_fk_idx
    ON company_policies (updated_by);
CREATE INDEX IF NOT EXISTS company_regulations_created_by_fk_idx
    ON company_regulations (created_by);
CREATE INDEX IF NOT EXISTS company_regulations_updated_by_fk_idx
    ON company_regulations (updated_by);
CREATE INDEX IF NOT EXISTS company_shared_resources_department_fk_idx
    ON company_shared_resources (owner_department_id);
CREATE INDEX IF NOT EXISTS departments_parent_fk_idx
    ON departments (parent_id);
CREATE INDEX IF NOT EXISTS employee_compensations_employee_org_fk_idx
    ON employee_compensations (employee_id, organization_id);
CREATE INDEX IF NOT EXISTS employee_compensations_updated_by_fk_idx
    ON employee_compensations (updated_by);
CREATE INDEX IF NOT EXISTS employee_score_events_employee_org_fk_idx
    ON employee_score_events (employee_id, organization_id);
CREATE INDEX IF NOT EXISTS employee_score_events_rule_fk_idx
    ON employee_score_events (rule_id);
CREATE INDEX IF NOT EXISTS employee_score_events_rule_org_fk_idx
    ON employee_score_events (rule_id, organization_id);
CREATE INDEX IF NOT EXISTS employees_department_org_fk_idx
    ON employees (department_id, organization_id);
CREATE INDEX IF NOT EXISTS employees_supervisor_fk_idx
    ON employees (supervisor_id);
CREATE INDEX IF NOT EXISTS leave_requests_reviewer_fk_idx
    ON leave_requests (reviewer_id);
CREATE INDEX IF NOT EXISTS oauth_identities_user_fk_idx
    ON oauth_identities (user_id);
CREATE INDEX IF NOT EXISTS offboarding_records_receiver_fk_idx
    ON offboarding_records (handover_receiver_id);
CREATE INDEX IF NOT EXISTS onboarding_invitations_department_fk_idx
    ON onboarding_invitations (department_id);
CREATE INDEX IF NOT EXISTS onboarding_invitations_position_fk_idx
    ON onboarding_invitations (position_id);
CREATE INDEX IF NOT EXISTS onboarding_requests_employee_fk_idx
    ON onboarding_requests (employee_id);
CREATE INDEX IF NOT EXISTS organization_audit_logs_actor_fk_idx
    ON organization_audit_logs (actor_id);
CREATE INDEX IF NOT EXISTS organization_audit_logs_organization_fk_idx
    ON organization_audit_logs (organization_id);
CREATE INDEX IF NOT EXISTS organization_memberships_organization_fk_idx
    ON organization_memberships (organization_id);
CREATE INDEX IF NOT EXISTS organization_positions_created_by_fk_idx
    ON organization_positions (created_by);
CREATE INDEX IF NOT EXISTS organization_positions_department_org_fk_idx
    ON organization_positions (department_id, organization_id);
CREATE INDEX IF NOT EXISTS organization_positions_updated_by_fk_idx
    ON organization_positions (updated_by);
CREATE INDEX IF NOT EXISTS organization_settings_updated_by_fk_idx
    ON organization_settings (updated_by);
CREATE INDEX IF NOT EXISTS organizations_created_by_fk_idx
    ON organizations (created_by);
CREATE INDEX IF NOT EXISTS refresh_tokens_user_fk_idx
    ON refresh_tokens (user_id);
CREATE INDEX IF NOT EXISTS report_definitions_created_by_fk_idx
    ON report_definitions (created_by);
CREATE INDEX IF NOT EXISTS report_definitions_department_org_fk_idx
    ON report_definitions (department_id, organization_id);
CREATE INDEX IF NOT EXISTS report_definitions_updated_by_fk_idx
    ON report_definitions (updated_by);
CREATE INDEX IF NOT EXISTS resource_records_created_by_fk_idx
    ON resource_records (created_by);
CREATE INDEX IF NOT EXISTS schedule_slots_schedule_fk_idx
    ON schedule_slots (schedule_id);
CREATE INDEX IF NOT EXISTS schedule_slots_shift_fk_idx
    ON schedule_slots (shift_id);
CREATE INDEX IF NOT EXISTS training_courses_created_by_fk_idx
    ON training_courses (created_by);
CREATE INDEX IF NOT EXISTS training_courses_updated_by_fk_idx
    ON training_courses (updated_by);

SET search_path = public;
