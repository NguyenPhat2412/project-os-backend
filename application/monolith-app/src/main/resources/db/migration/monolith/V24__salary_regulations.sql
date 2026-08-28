create table if not exists salary_regulations (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organizations(id) on delete cascade,
    rule_code varchar(80) not null,
    name varchar(200) not null,
    salary_type varchar(100) not null,
    grade_step varchar(80),
    coefficient numeric(12,4) not null default 0 check (coefficient >= 0),
    min_amount numeric(19,2) not null default 0 check (min_amount >= 0),
    max_amount numeric(19,2) not null default 0 check (max_amount >= min_amount),
    base_salary numeric(19,2) not null default 0 check (base_salary >= 0),
    title_salary numeric(19,2) not null default 0 check (title_salary >= 0),
    performance_salary numeric(19,2) not null default 0 check (performance_salary >= 0),
    concurrent_allowance numeric(19,2) not null default 0 check (concurrent_allowance >= 0),
    gasoline_allowance numeric(19,2) not null default 0 check (gasoline_allowance >= 0),
    other_allowance numeric(19,2) not null default 0 check (other_allowance >= 0),
    total_salary numeric(19,2) not null default 0 check (total_salary >= 0),
    effective_date date not null,
    status varchar(20) not null default 'DRAFT' check (status in ('DRAFT', 'ACTIVE', 'EXPIRED')),
    notes text,
    created_by uuid not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint salary_regulations_org_code_unique unique (organization_id, rule_code)
);

create index if not exists salary_regulations_org_status_idx on salary_regulations(organization_id, status, effective_date desc);
create index if not exists salary_regulations_org_type_idx on salary_regulations(organization_id, salary_type);
