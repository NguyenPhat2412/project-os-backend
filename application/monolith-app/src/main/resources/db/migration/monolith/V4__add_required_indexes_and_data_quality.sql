create extension if not exists btree_gist;

create index if not exists departments_organization_fk_idx
    on public.departments(organization_id);
create index if not exists employees_organization_fk_idx
    on public.employees(organization_id);
create index if not exists employees_department_fk_idx
    on public.employees(department_id);
create index if not exists employees_user_fk_idx
    on public.employees(user_id);
create index if not exists organization_memberships_user_fk_idx
    on public.organization_memberships(user_id);
create index if not exists permission_groups_organization_fk_idx
    on public.permission_groups(organization_id);
create index if not exists permission_group_members_user_fk_idx
    on public.permission_group_members(user_id);
create index if not exists projects_organization_fk_idx
    on public.projects(organization_id);
create index if not exists projects_owner_fk_idx
    on public.projects(owner_id);
create index if not exists resource_records_project_fk_idx
    on public.resource_records(project_id);
create index if not exists shifts_organization_fk_idx
    on public.shifts(organization_id);
create index if not exists work_schedules_organization_fk_idx
    on public.work_schedules(organization_id);
create index if not exists schedule_assignments_employee_fk_idx
    on public.schedule_assignments(employee_id);
create index if not exists schedule_assignments_schedule_fk_idx
    on public.schedule_assignments(schedule_id);
create index if not exists attendance_records_employee_fk_idx
    on public.attendance_records(employee_id);
create index if not exists attendance_records_shift_fk_idx
    on public.attendance_records(shift_id);
create index if not exists attendance_adjustments_employee_fk_idx
    on public.attendance_adjustments(employee_id);
create index if not exists leave_requests_employee_fk_idx
    on public.leave_requests(employee_id);

alter table public.attendance_records
    add constraint attendance_records_employee_date_uq unique (employee_id, work_date);
alter table public.schedule_assignments
    add constraint schedule_assignments_employee_start_uq unique (employee_id, effective_from);

alter table public.attendance_records
    add constraint attendance_records_time_order_ck
    check (check_out_at is null or check_in_at is null or check_out_at >= check_in_at)
    not valid;
alter table public.attendance_adjustments
    add constraint attendance_adjustments_requested_time_ck
    check (requested_check_in_at is not null or requested_check_out_at is not null)
    not valid;
alter table public.attendance_adjustments
    add constraint attendance_adjustments_time_order_ck
    check (requested_check_out_at is null or requested_check_in_at is null
           or requested_check_out_at >= requested_check_in_at)
    not valid;
alter table public.leave_requests
    add constraint leave_requests_date_order_ck
    check (start_date is null or end_date is null or end_date >= start_date)
    not valid;

alter table public.attendance_records validate constraint attendance_records_time_order_ck;
alter table public.attendance_adjustments validate constraint attendance_adjustments_requested_time_ck;
alter table public.attendance_adjustments validate constraint attendance_adjustments_time_order_ck;
alter table public.leave_requests validate constraint leave_requests_date_order_ck;

alter table public.schedule_assignments
    add constraint schedule_assignments_no_overlap_excl
    exclude using gist (
        organization_id with =,
        employee_id with =,
        daterange(
            effective_from,
            coalesce(effective_to + 1, 'infinity'::date),
            '[)'
        ) with &&
    );
