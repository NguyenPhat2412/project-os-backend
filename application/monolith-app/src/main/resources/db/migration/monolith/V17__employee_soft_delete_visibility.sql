alter table public.employees
    add column if not exists is_deleted boolean not null default false;

alter table public.employees
    add column if not exists deleted_at timestamptz;

create index if not exists employees_visible_organization_idx
    on public.employees (organization_id)
    where is_deleted = false;
