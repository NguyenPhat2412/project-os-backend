create table organizations (
    id uuid primary key,
    name text not null,
    slug text not null unique,
    timezone text not null,
    status text not null check (status in ('ACTIVE', 'DISABLED')),
    created_by uuid not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table departments (
    id uuid primary key,
    organization_id uuid not null references organizations(id) on delete cascade,
    parent_id uuid references departments(id) on delete restrict,
    name text not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    unique (organization_id, name)
);

create table employees (
    id uuid primary key,
    organization_id uuid not null references organizations(id) on delete cascade,
    department_id uuid references departments(id) on delete set null,
    supervisor_id uuid references employees(id) on delete set null,
    user_id uuid,
    full_name text not null,
    email text not null,
    title text,
    status text not null check (status in ('ACTIVE', 'INACTIVE')),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    unique (organization_id, email),
    unique (organization_id, user_id)
);

create table organization_memberships (
    id uuid primary key,
    organization_id uuid not null references organizations(id) on delete cascade,
    user_id uuid not null,
    role text not null check (role in ('OWNER', 'ADMIN', 'MEMBER')),
    status text not null check (status in ('ACTIVE', 'DISABLED')),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    unique (organization_id, user_id)
);

create index departments_organization_idx on departments(organization_id);
create index employees_organization_status_idx on employees(organization_id, status);
create index employees_supervisor_idx on employees(supervisor_id) where supervisor_id is not null;
create index organization_memberships_user_idx on organization_memberships(user_id, status);
