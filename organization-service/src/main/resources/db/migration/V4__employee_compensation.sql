create table employee_compensations (
    employee_id uuid primary key references employees(id) on delete cascade,
    organization_id uuid not null references organizations(id) on delete cascade,
    monthly_amount numeric(19,2) not null check (monthly_amount >= 0),
    updated_by uuid not null,
    updated_at timestamptz not null
);

create index employee_compensations_organization_idx on employee_compensations(organization_id);
