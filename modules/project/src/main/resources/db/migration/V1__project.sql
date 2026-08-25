create table projects (
    id uuid primary key,
    legacy_id text unique,
    name text not null,
    description text,
    status text not null check (status in ('ACTIVE', 'COMPLETED', 'ARCHIVED')),
    icon text not null,
    color text not null,
    current_sprint text,
    quarter text,
    start_date date,
    end_date date,
    tech_stack jsonb not null default '[]'::jsonb,
    team_size integer check (team_size is null or team_size >= 0),
    owner_id uuid not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    check (end_date is null or start_date is null or end_date >= start_date)
);

create index projects_owner_id_idx on projects(owner_id);
create index projects_status_updated_at_idx on projects(status, updated_at desc);
