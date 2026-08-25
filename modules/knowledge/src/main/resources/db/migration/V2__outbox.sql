create table outbox_events (
    id uuid primary key,
    event_type varchar(120) not null,
    payload jsonb not null,
    attempts integer not null default 0,
    next_attempt_at timestamptz not null,
    last_error varchar(500),
    created_at timestamptz not null,
    delivered_at timestamptz
);
create index outbox_events_pending_idx on outbox_events(next_attempt_at, created_at)
    where delivered_at is null;
