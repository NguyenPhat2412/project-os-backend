-- Read-only preflight for the modular monolith public-schema migration.
-- Run against a backup or staging clone before applying V2+ migrations.

\set ON_ERROR_STOP on
\pset pager off
\pset tuples_only off

\echo '== database =='
select current_database() as database_name, current_user as database_user, now() as checked_at;

\echo '== table row estimates =='
select schemaname, relname as table_name, n_live_tup as estimated_rows
from pg_stat_user_tables
where schemaname = 'public'
order by relname;

\echo '== primary-key coverage =='
select c.relname as table_name,
       count(*) filter (where con.contype = 'p') as primary_key_count
from pg_class c
join pg_namespace n on n.oid = c.relnamespace
left join pg_constraint con on con.conrelid = c.oid
where c.relkind = 'r'
  and n.nspname = 'public'
group by c.relname
order by c.relname;

\echo '== foreign-key coverage =='
select conrelid::regclass as table_name,
       conname,
       pg_get_constraintdef(oid) as definition
from pg_constraint
where contype = 'f'
  and connamespace = 'public'::regnamespace
order by conrelid::regclass::text, conname;

\echo '== proposed-FK orphan checks =='
select 'projects.organization_id -> organizations.id' as relation, count(*) as orphan_rows
from public.projects p
left join public.organizations o on o.id = p.organization_id
where p.organization_id is not null and o.id is null
union all
select 'projects.owner_id -> users.id', count(*)
from public.projects p
left join public.users u on u.id = p.owner_id
where u.id is null
union all
select 'employees.organization_id -> organizations.id', count(*)
from public.employees e
left join public.organizations o on o.id = e.organization_id
where o.id is null
union all
select 'employees.department_id -> departments.id', count(*)
from public.employees e
left join public.departments d on d.id = e.department_id
where e.department_id is not null and d.id is null
union all
select 'employees.user_id -> users.id', count(*)
from public.employees e
left join public.users u on u.id = e.user_id
where e.user_id is not null and u.id is null
union all
select 'resource_records.project_id -> projects.id', count(*)
from public.resource_records r
left join public.projects p on p.id = r.project_id
where p.id is null
union all
select 'attendance_records.employee_id -> employees.id', count(*)
from public.attendance_records a
left join public.employees e on e.id = a.employee_id
where e.id is null
union all
select 'attendance_records.shift_id -> shifts.id', count(*)
from public.attendance_records a
left join public.shifts s on s.id = a.shift_id
where a.shift_id is not null and s.id is null
union all
select 'leave_requests.employee_id -> employees.id', count(*)
from public.leave_requests l
left join public.employees e on e.id = l.employee_id
where l.employee_id is not null and e.id is null;

\echo '== tenant mismatch checks =='
select 'employees.department_id organization mismatch' as relation, count(*) as invalid_rows
from public.employees e
join public.departments d on d.id = e.department_id
where e.department_id is not null
  and e.organization_id <> d.organization_id
union all
select 'attendance_records.employee_id organization mismatch', count(*)
from public.attendance_records a
join public.employees e on e.id = a.employee_id
where a.organization_id <> e.organization_id
union all
select 'schedule_assignments.employee_id organization mismatch', count(*)
from public.schedule_assignments s
join public.employees e on e.id = s.employee_id
where s.organization_id <> e.organization_id;

\echo '== duplicate keys =='
select 'attendance_records(employee_id, work_date)' as key_name, count(*) as duplicate_groups
from (
    select employee_id, work_date
    from public.attendance_records
    group by employee_id, work_date
    having count(*) > 1
) duplicates
union all
select 'schedule_assignments(employee_id, effective_from)', count(*)
from (
    select employee_id, effective_from
    from public.schedule_assignments
    group by employee_id, effective_from
    having count(*) > 1
) duplicates;

\echo '== identifier type review =='
select table_name, column_name, data_type
from information_schema.columns
where table_schema = 'public'
  and column_name in ('employee_id', 'organization_id', 'department_id', 'project_id', 'user_id')
order by table_name, column_name;
