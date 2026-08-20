-- =====================================================================
-- V12: TEAMS, SQUAD & WORKGROUPS HIERARCHY SCHEMA
-- Persistent structure for Teams/Squads within Departments & Organizations
-- Semantic URL slug support for SEO, routing & Account project settings
-- =====================================================================

create table if not exists organization_teams (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organizations(id) on delete cascade,
    department_id uuid references departments(id) on delete set null,
    code text not null,
    name text not null,
    slug text not null,
    leader_id uuid references employees(id) on delete set null,
    leader_name text,
    members_count int default 0,
    description text,
    status text not null default 'ACTIVE' check (status in ('ACTIVE', 'INACTIVE')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique(organization_id, code)
);

create index if not exists idx_teams_dept on organization_teams(department_id);
create index if not exists idx_teams_slug on organization_teams(slug);

-- Add username_slug and team_id to employees
alter table employees add column if not exists username_slug text;
alter table employees add column if not exists team_id uuid references organization_teams(id) on delete set null;
alter table employees add column if not exists team_name text;

-- Add slug to departments
alter table departments add column if not exists slug text;
