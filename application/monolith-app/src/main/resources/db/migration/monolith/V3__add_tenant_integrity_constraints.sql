-- Prevent records from one organization from referencing a parent record
-- owned by another organization.

alter table public.departments
    add constraint departments_id_organization_uq unique (id, organization_id);
alter table public.employees
    add constraint employees_id_organization_uq unique (id, organization_id);
alter table public.projects
    add constraint projects_id_organization_uq unique (id, organization_id);
alter table public.shifts
    add constraint shifts_id_organization_uq unique (id, organization_id);
alter table public.work_schedules
    add constraint work_schedules_id_organization_uq unique (id, organization_id);

alter table public.employees
    add constraint employees_department_same_organization_fk
    foreign key (department_id, organization_id)
    references public.departments(id, organization_id)
    on delete set null
    not valid;
alter table public.schedule_assignments
    add constraint schedule_assignments_employee_same_organization_fk
    foreign key (employee_id, organization_id)
    references public.employees(id, organization_id)
    on delete restrict
    not valid;
alter table public.attendance_records
    add constraint attendance_records_employee_same_organization_fk
    foreign key (employee_id, organization_id)
    references public.employees(id, organization_id)
    on delete restrict
    not valid;
alter table public.attendance_adjustments
    add constraint attendance_adjustments_employee_same_organization_fk
    foreign key (employee_id, organization_id)
    references public.employees(id, organization_id)
    on delete restrict
    not valid;
alter table public.leave_requests
    add constraint leave_requests_employee_same_organization_fk
    foreign key (employee_id, organization_id)
    references public.employees(id, organization_id)
    on delete restrict
    not valid;

alter table public.employees validate constraint employees_department_same_organization_fk;
alter table public.schedule_assignments validate constraint schedule_assignments_employee_same_organization_fk;
alter table public.attendance_records validate constraint attendance_records_employee_same_organization_fk;
alter table public.attendance_adjustments validate constraint attendance_adjustments_employee_same_organization_fk;
alter table public.leave_requests validate constraint leave_requests_employee_same_organization_fk;
