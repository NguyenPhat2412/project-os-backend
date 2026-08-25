create table company_policies (
    organization_id uuid primary key references organizations(id) on delete cascade,
    morning_start time not null,
    morning_end time not null,
    afternoon_start time not null,
    afternoon_end time not null,
    rules text not null,
    updated_by uuid not null,
    updated_at timestamptz not null
);
