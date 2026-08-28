create table public.notification_categories (
    id uuid primary key,
    organization_id uuid not null references public.organizations(id) on delete cascade,
    code varchar(80) not null,
    name varchar(160) not null,
    is_active boolean not null default true,
    display_order integer not null default 0,
    created_by uuid not null,
    updated_by uuid not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint notification_categories_code_unique unique (organization_id, code),
    constraint notification_categories_code_check check (code ~ '^[a-z0-9][a-z0-9_-]{1,79}$'),
    constraint notification_categories_name_check check (length(btrim(name)) between 1 and 160),
    constraint notification_categories_display_order_check check (display_order >= 0)
);

create index notification_categories_active_idx
    on public.notification_categories (organization_id, is_active, display_order, name);
