create table organization_permissions (
    id uuid primary key,
    organization_id uuid not null references organizations(id) on delete cascade,
    permission_key text not null,
    role_key text not null,
    created_at timestamptz not null,
    unique (organization_id, permission_key, role_key)
);

create index organization_permissions_org_role_idx on organization_permissions(organization_id, role_key);
