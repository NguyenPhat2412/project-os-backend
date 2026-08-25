create table permission_groups (
    id uuid primary key,
    organization_id uuid not null references organizations(id) on delete cascade,
    name text not null,
    description text,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    unique (organization_id, name)
);

create table permission_group_modules (
    group_id uuid not null references permission_groups(id) on delete cascade,
    module_key text not null,
    primary key (group_id, module_key)
);

create table permission_group_members (
    id uuid primary key,
    organization_id uuid not null references organizations(id) on delete cascade,
    group_id uuid not null references permission_groups(id) on delete cascade,
    user_id uuid not null,
    created_at timestamptz not null,
    unique (group_id, user_id)
);

create table organization_audit_logs (
    id uuid primary key,
    organization_id uuid not null references organizations(id) on delete cascade,
    actor_id uuid not null,
    event_type text not null,
    entity_type text not null,
    entity_id uuid,
    before_state jsonb,
    after_state jsonb,
    reason text,
    created_at timestamptz not null
);

create index permission_groups_organization_idx on permission_groups(organization_id);
create index permission_group_members_user_idx on permission_group_members(organization_id, user_id);
create index organization_audit_logs_org_created_idx on organization_audit_logs(organization_id, created_at desc);
