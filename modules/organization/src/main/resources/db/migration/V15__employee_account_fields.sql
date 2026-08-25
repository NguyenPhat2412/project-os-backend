alter table employees add column if not exists code text;
alter table employees add column if not exists phone text;
alter table employees add column if not exists notes text;
create unique index if not exists employees_organization_code_unique
    on employees (organization_id, lower(code))
    where code is not null;
