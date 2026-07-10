create table resource_records (
    id uuid primary key,
    project_id uuid not null,
    resource_type varchar(80) not null,
    legacy_id varchar(200),
    payload jsonb not null default '{}'::jsonb,
    created_by uuid not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    unique (project_id, resource_type, legacy_id)
);

create index resource_records_project_type_created_idx
    on resource_records(project_id, resource_type, created_at);
create index resource_records_payload_gin_idx on resource_records using gin(payload);
