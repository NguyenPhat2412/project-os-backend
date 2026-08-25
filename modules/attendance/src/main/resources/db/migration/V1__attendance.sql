create table shifts (
    id uuid primary key,
    organization_id uuid not null,
    name text not null,
    start_time time not null,
    end_time time not null,
    break_minutes integer not null default 0 check (break_minutes >= 0 and break_minutes <= 720),
    active boolean not null default true,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    unique (organization_id, name),
    check (start_time <> end_time)
);

create table work_schedules (
    id uuid primary key,
    organization_id uuid not null,
    name text not null,
    active boolean not null default true,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    unique (organization_id, name)
);

create table schedule_slots (
    id uuid primary key,
    schedule_id uuid not null references work_schedules(id) on delete cascade,
    shift_id uuid not null references shifts(id) on delete restrict,
    day_of_week smallint not null check (day_of_week between 1 and 7),
    unique (schedule_id, day_of_week)
);

create table schedule_assignments (
    id uuid primary key,
    organization_id uuid not null,
    employee_id uuid not null,
    schedule_id uuid not null references work_schedules(id) on delete restrict,
    effective_from date not null,
    effective_to date,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    check (effective_to is null or effective_to >= effective_from),
    unique (employee_id, effective_from)
);

create table attendance_records (
    id uuid primary key,
    organization_id uuid not null,
    employee_id uuid not null,
    shift_id uuid not null references shifts(id) on delete restrict,
    work_date date not null,
    shift_name text not null,
    scheduled_start_at timestamptz not null,
    scheduled_end_at timestamptz not null,
    check_in_at timestamptz,
    check_out_at timestamptz,
    break_minutes integer not null default 0,
    status text not null check (status in ('OPEN', 'COMPLETED')),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    unique (employee_id, work_date),
    check (check_out_at is null or check_in_at is null or check_out_at >= check_in_at)
);

create table attendance_adjustments (
    id uuid primary key,
    organization_id uuid not null,
    employee_id uuid not null,
    work_date date not null,
    requested_check_in_at timestamptz,
    requested_check_out_at timestamptz,
    reason text not null,
    status text not null check (status in ('PENDING', 'APPROVED', 'REJECTED')),
    reviewer_id uuid,
    reviewed_at timestamptz,
    decision_note text,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    check (requested_check_in_at is not null or requested_check_out_at is not null),
    check (requested_check_out_at is null or requested_check_in_at is null or requested_check_out_at >= requested_check_in_at)
);

create table leave_requests (
    id uuid primary key,
    organization_id uuid not null,
    employee_id uuid not null,
    start_date date not null,
    end_date date not null,
    reason text not null,
    status text not null check (status in ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    reviewer_id uuid,
    reviewed_at timestamptz,
    decision_note text,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    check (end_date >= start_date)
);

create index schedule_assignments_employee_period_idx on schedule_assignments(organization_id, employee_id, effective_from, effective_to);
create index attendance_records_organization_date_idx on attendance_records(organization_id, work_date);
create index attendance_records_employee_date_idx on attendance_records(employee_id, work_date);
create index attendance_adjustments_pending_idx on attendance_adjustments(organization_id, status, work_date) where status = 'PENDING';
create index leave_requests_pending_idx on leave_requests(organization_id, status, start_date) where status = 'PENDING';
