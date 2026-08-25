create table activity_events (
    id uuid primary key,
    event_id uuid not null unique,
    organization_id uuid not null,
    project_id uuid not null,
    actor_id uuid not null,
    resource varchar(80) not null,
    action varchar(40) not null,
    subject varchar(255) not null,
    occurred_at timestamptz not null,
    created_at timestamptz not null
);

create index activity_events_scope_idx
    on activity_events (organization_id, project_id, actor_id, occurred_at desc);
