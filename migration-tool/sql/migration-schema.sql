create schema if not exists migration;

create table if not exists migration.legacy_mappings (
    source_collection text not null,
    legacy_id text not null,
    target_type text not null,
    target_uuid uuid not null,
    migrated_at timestamptz not null default now(),
    primary key (source_collection, legacy_id)
);

create table if not exists migration.runs (
    id uuid primary key,
    started_at timestamptz not null default now(),
    finished_at timestamptz,
    status text not null,
    report jsonb not null default '{}'::jsonb
);
