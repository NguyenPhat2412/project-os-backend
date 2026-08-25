create table if not exists organization_settings (
    organization_id uuid primary key references organizations(id) on delete cascade,
    settings jsonb not null,
    updated_by uuid not null,
    updated_at timestamptz not null
);
