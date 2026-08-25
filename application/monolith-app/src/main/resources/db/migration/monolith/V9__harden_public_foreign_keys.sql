SET search_path = public;

-- Remove duplicate or redundant constraints introduced by the legacy baseline.
-- Tenant-safe composite constraints below remain the source of truth for
-- organization-owned relationships.
ALTER TABLE attendance_adjustments DROP CONSTRAINT IF EXISTS attendance_adjustments_employee_fk;
ALTER TABLE attendance_records DROP CONSTRAINT IF EXISTS attendance_records_employee_fk;
ALTER TABLE employees DROP CONSTRAINT IF EXISTS employees_department_fk;
ALTER TABLE leave_requests DROP CONSTRAINT IF EXISTS leave_requests_employee_fk;
ALTER TABLE schedule_assignments DROP CONSTRAINT IF EXISTS schedule_assignments_employee_fk;
ALTER TABLE organization_permissions DROP CONSTRAINT IF EXISTS organization_permissions_organization_id_fkey;

ALTER TABLE permission_groups
    ADD CONSTRAINT permission_groups_id_organization_uq
    UNIQUE (id, organization_id);

ALTER TABLE departments
    ADD CONSTRAINT departments_parent_fk
    FOREIGN KEY (parent_id) REFERENCES departments(id)
    ON DELETE SET NULL NOT VALID;

ALTER TABLE company_policies
    ADD CONSTRAINT company_policies_organization_fk
    FOREIGN KEY (organization_id) REFERENCES organizations(id)
    ON DELETE RESTRICT NOT VALID;

ALTER TABLE employee_compensations
    ADD CONSTRAINT employee_compensations_employee_same_organization_fk
    FOREIGN KEY (employee_id, organization_id)
    REFERENCES employees(id, organization_id)
    ON DELETE RESTRICT NOT VALID;

ALTER TABLE permission_group_members
    ADD CONSTRAINT permission_group_members_group_same_organization_fk
    FOREIGN KEY (group_id, organization_id)
    REFERENCES permission_groups(id, organization_id)
    ON DELETE CASCADE NOT VALID;

ALTER TABLE user_profiles
    ADD CONSTRAINT user_profiles_user_fk
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE NOT VALID;

ALTER TABLE activity_events
    ADD CONSTRAINT activity_events_actor_fk
    FOREIGN KEY (actor_id) REFERENCES users(id)
    ON DELETE RESTRICT NOT VALID,
    ADD CONSTRAINT activity_events_organization_fk
    FOREIGN KEY (organization_id) REFERENCES organizations(id)
    ON DELETE RESTRICT NOT VALID,
    ADD CONSTRAINT activity_events_project_fk
    FOREIGN KEY (project_id) REFERENCES projects(id)
    ON DELETE RESTRICT NOT VALID;

ALTER TABLE oauth_identities
    DROP CONSTRAINT IF EXISTS fkcwhpmr8ej1s107ds2ex7afd65,
    ADD CONSTRAINT oauth_identities_user_fk
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE NOT VALID;

ALTER TABLE refresh_tokens
    DROP CONSTRAINT IF EXISTS fk1lih5y2npsf8u5o3vhdb9y0os,
    ADD CONSTRAINT refresh_tokens_user_fk
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE NOT VALID;

CREATE INDEX IF NOT EXISTS activity_events_actor_fk_idx
    ON activity_events (actor_id);
CREATE INDEX IF NOT EXISTS activity_events_organization_fk_idx
    ON activity_events (organization_id);
CREATE INDEX IF NOT EXISTS activity_events_project_fk_idx
    ON activity_events (project_id);
CREATE INDEX IF NOT EXISTS employee_compensations_employee_fk_idx
    ON employee_compensations (employee_id);
CREATE INDEX IF NOT EXISTS permission_group_members_group_fk_idx
    ON permission_group_members (group_id);
CREATE INDEX IF NOT EXISTS user_profiles_user_fk_idx
    ON user_profiles (user_id);

ALTER TABLE departments VALIDATE CONSTRAINT departments_parent_fk;
ALTER TABLE company_policies VALIDATE CONSTRAINT company_policies_organization_fk;
ALTER TABLE employee_compensations VALIDATE CONSTRAINT employee_compensations_employee_same_organization_fk;
ALTER TABLE permission_group_members VALIDATE CONSTRAINT permission_group_members_group_same_organization_fk;
ALTER TABLE user_profiles VALIDATE CONSTRAINT user_profiles_user_fk;
ALTER TABLE activity_events VALIDATE CONSTRAINT activity_events_actor_fk;
ALTER TABLE activity_events VALIDATE CONSTRAINT activity_events_organization_fk;
ALTER TABLE activity_events VALIDATE CONSTRAINT activity_events_project_fk;
ALTER TABLE oauth_identities VALIDATE CONSTRAINT oauth_identities_user_fk;
ALTER TABLE refresh_tokens VALIDATE CONSTRAINT refresh_tokens_user_fk;
