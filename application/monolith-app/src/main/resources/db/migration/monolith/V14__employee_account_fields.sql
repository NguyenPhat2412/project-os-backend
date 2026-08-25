alter table public.employees add column if not exists code text;
alter table public.employees add column if not exists phone text;
alter table public.employees add column if not exists notes text;

create unique index if not exists employees_organization_code_unique
    on public.employees (organization_id, lower(code))
    where code is not null;
