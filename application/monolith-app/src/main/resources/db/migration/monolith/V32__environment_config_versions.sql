create table if not exists environment_config_versions (
    id uuid primary key,
    config_path text not null,
    checksum varchar(128) not null,
    changed_keys jsonb not null,
    snapshot_path text not null,
    status varchar(32) not null,
    reload_required boolean not null default true,
    created_by uuid not null,
    created_at timestamptz not null default now(),
    notes text,
    constraint environment_config_versions_status_check check (status in ('APPLIED', 'ROLLED_BACK'))
);

create index if not exists environment_config_versions_created_idx
    on environment_config_versions (created_at desc);

create index if not exists environment_config_versions_status_idx
    on environment_config_versions (status, created_at desc);
