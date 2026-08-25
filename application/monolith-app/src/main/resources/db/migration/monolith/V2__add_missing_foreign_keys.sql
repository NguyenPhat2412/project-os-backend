-- Add relational ownership constraints after the V1 baseline.
-- NOT VALID keeps the migration safe for existing data; each constraint is
-- validated explicitly so orphan rows fail the migration rather than being
-- silently accepted.

alter table public.departments
    add constraint departments_organization_fk
    foreign key (organization_id) references public.organizations(id) on delete restrict not valid;
alter table public.employees
    add constraint employees_organization_fk
    foreign key (organization_id) references public.organizations(id) on delete restrict not valid,
    add constraint employees_department_fk
    foreign key (department_id) references public.departments(id) on delete set null not valid,
    add constraint employees_supervisor_fk
    foreign key (supervisor_id) references public.employees(id) on delete set null not valid,
    add constraint employees_user_fk
    foreign key (user_id) references public.users(id) on delete set null not valid;
alter table public.organization_memberships
    add constraint organization_memberships_organization_fk
    foreign key (organization_id) references public.organizations(id) on delete restrict not valid,
    add constraint organization_memberships_user_fk
    foreign key (user_id) references public.users(id) on delete restrict not valid;
alter table public.permission_groups
    add constraint permission_groups_organization_fk
    foreign key (organization_id) references public.organizations(id) on delete restrict not valid;
alter table public.permission_group_members
    add constraint permission_group_members_user_fk
    foreign key (user_id) references public.users(id) on delete restrict not valid;
alter table public.organization_audit_logs
    add constraint organization_audit_logs_organization_fk
    foreign key (organization_id) references public.organizations(id) on delete restrict not valid,
    add constraint organization_audit_logs_actor_fk
    foreign key (actor_id) references public.users(id) on delete restrict not valid;

alter table public.projects
    add constraint projects_organization_fk
    foreign key (organization_id) references public.organizations(id) on delete restrict not valid,
    add constraint projects_owner_fk
    foreign key (owner_id) references public.users(id) on delete restrict not valid;
alter table public.resource_records
    add constraint resource_records_project_fk
    foreign key (project_id) references public.projects(id) on delete cascade not valid;

alter table public.shifts
    add constraint shifts_organization_fk
    foreign key (organization_id) references public.organizations(id) on delete restrict not valid;
alter table public.work_schedules
    add constraint work_schedules_organization_fk
    foreign key (organization_id) references public.organizations(id) on delete restrict not valid;
alter table public.schedule_slots
    add constraint schedule_slots_schedule_fk
    foreign key (schedule_id) references public.work_schedules(id) on delete cascade not valid,
    add constraint schedule_slots_shift_fk
    foreign key (shift_id) references public.shifts(id) on delete restrict not valid;
alter table public.schedule_assignments
    add constraint schedule_assignments_organization_fk
    foreign key (organization_id) references public.organizations(id) on delete restrict not valid,
    add constraint schedule_assignments_employee_fk
    foreign key (employee_id) references public.employees(id) on delete restrict not valid,
    add constraint schedule_assignments_schedule_fk
    foreign key (schedule_id) references public.work_schedules(id) on delete restrict not valid;
alter table public.attendance_records
    add constraint attendance_records_organization_fk
    foreign key (organization_id) references public.organizations(id) on delete restrict not valid,
    add constraint attendance_records_employee_fk
    foreign key (employee_id) references public.employees(id) on delete restrict not valid,
    add constraint attendance_records_shift_fk
    foreign key (shift_id) references public.shifts(id) on delete restrict not valid;
alter table public.attendance_adjustments
    add constraint attendance_adjustments_employee_fk
    foreign key (employee_id) references public.employees(id) on delete restrict not valid,
    add constraint attendance_adjustments_reviewer_fk
    foreign key (reviewer_id) references public.users(id) on delete set null not valid;
alter table public.leave_requests
    add constraint leave_requests_employee_fk
    foreign key (employee_id) references public.employees(id) on delete restrict not valid,
    add constraint leave_requests_reviewer_fk
    foreign key (reviewer_id) references public.users(id) on delete set null not valid;

alter table public.departments validate constraint departments_organization_fk;
alter table public.employees validate constraint employees_organization_fk;
alter table public.employees validate constraint employees_department_fk;
alter table public.employees validate constraint employees_supervisor_fk;
alter table public.employees validate constraint employees_user_fk;
alter table public.organization_memberships validate constraint organization_memberships_organization_fk;
alter table public.organization_memberships validate constraint organization_memberships_user_fk;
alter table public.permission_groups validate constraint permission_groups_organization_fk;
alter table public.permission_group_members validate constraint permission_group_members_user_fk;
alter table public.organization_audit_logs validate constraint organization_audit_logs_organization_fk;
alter table public.organization_audit_logs validate constraint organization_audit_logs_actor_fk;
alter table public.projects validate constraint projects_organization_fk;
alter table public.projects validate constraint projects_owner_fk;
alter table public.resource_records validate constraint resource_records_project_fk;
alter table public.shifts validate constraint shifts_organization_fk;
alter table public.work_schedules validate constraint work_schedules_organization_fk;
alter table public.schedule_slots validate constraint schedule_slots_schedule_fk;
alter table public.schedule_slots validate constraint schedule_slots_shift_fk;
alter table public.schedule_assignments validate constraint schedule_assignments_organization_fk;
alter table public.schedule_assignments validate constraint schedule_assignments_employee_fk;
alter table public.schedule_assignments validate constraint schedule_assignments_schedule_fk;
alter table public.attendance_records validate constraint attendance_records_organization_fk;
alter table public.attendance_records validate constraint attendance_records_employee_fk;
alter table public.attendance_records validate constraint attendance_records_shift_fk;
alter table public.attendance_adjustments validate constraint attendance_adjustments_employee_fk;
alter table public.attendance_adjustments validate constraint attendance_adjustments_reviewer_fk;
alter table public.leave_requests validate constraint leave_requests_employee_fk;
alter table public.leave_requests validate constraint leave_requests_reviewer_fk;
