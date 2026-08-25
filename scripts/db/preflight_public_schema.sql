-- Read-only production preflight for the canonical public schema.
-- Expected failure counts are zero. Run with:
--   psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f scripts/db/preflight_public_schema.sql

\pset pager off
\pset format aligned

\echo '== Flyway version =='
select version, description, success
from public.flyway_schema_history
order by installed_rank;

\echo '== Public table inventory =='
select table_name
from information_schema.tables
where table_schema = 'public' and table_type = 'BASE TABLE'
order by table_name;

\echo '== Schema totals =='
select
    (select count(*) from information_schema.tables where table_schema = 'public' and table_type = 'BASE TABLE') as public_tables,
    (select count(*) from pg_constraint c join pg_namespace n on n.oid = c.connamespace
     where n.nspname = 'public' and c.contype = 'p') as primary_keys,
    (select count(*) from pg_constraint c join pg_namespace n on n.oid = c.connamespace
     where n.nspname = 'public' and c.contype = 'f' and c.convalidated) as validated_foreign_keys;

\echo '== Tables without exactly one primary key =='
select c.relname as table_name, count(p.oid) as primary_key_count
from pg_class c
join pg_namespace n on n.oid = c.relnamespace
left join pg_constraint p on p.conrelid = c.oid and p.contype = 'p'
where n.nspname = 'public' and c.relkind = 'r'
group by c.relname
having count(p.oid) <> 1
order by c.relname;

\echo '== Unvalidated foreign keys =='
select conrelid::regclass as table_name, conname
from pg_constraint
where contype = 'f' and not convalidated
order by conrelid::regclass::text, conname;

\echo '== Duplicate foreign keys with identical key pairs =='
select conrelid::regclass as table_name,
       confrelid::regclass as referenced_table,
       array_agg(conname order by conname) as constraints
from pg_constraint
where contype = 'f'
group by conrelid, confrelid, conkey, confkey
having count(*) > 1
order by 1, 2;

\echo '== Core orphan checks =='
select 'employees.organization_id' as relation, count(*) as orphan_rows
from employees e left join organizations o on o.id = e.organization_id
where o.id is null
union all select 'employees.department_id', count(*)
from employees e left join departments d on d.id = e.department_id
where e.department_id is not null and d.id is null
union all select 'employees.user_id', count(*)
from employees e left join users u on u.id = e.user_id
where e.user_id is not null and u.id is null
union all select 'departments.organization_id', count(*)
from departments d left join organizations o on o.id = d.organization_id
where o.id is null
union all select 'projects.organization_id', count(*)
from projects p left join organizations o on o.id = p.organization_id
where p.organization_id is not null and o.id is null
union all select 'projects.owner_id', count(*)
from projects p left join users u on u.id = p.owner_id
where u.id is null
union all select 'resource_records.project_id', count(*)
from resource_records r left join projects p on p.id = r.project_id
where p.id is null
union all select 'attendance_records.employee_id', count(*)
from attendance_records a left join employees e on e.id = a.employee_id
where e.id is null
union all select 'leave_requests.employee_id', count(*)
from leave_requests l left join employees e on e.id = l.employee_id
where l.employee_id is not null and e.id is null
order by relation;

\echo '== Cross-organization reference checks =='
select 'employees.department_id' as relation, count(*) as mismatch_rows
from employees e join departments d on d.id = e.department_id
where d.organization_id <> e.organization_id
union all select 'attendance_records.employee_id', count(*)
from attendance_records a join employees e on e.id = a.employee_id
where e.organization_id <> a.organization_id
union all select 'schedule_assignments.employee_id', count(*)
from schedule_assignments s join employees e on e.id = s.employee_id
where e.organization_id <> s.organization_id
union all select 'enterprise_teams.department_uuid', count(*)
from enterprise_teams t
join departments d on d.id = t.department_uuid
where t.organization_uuid is not null and d.organization_id <> t.organization_uuid
order by relation;

\echo '== Unresolved legacy references =='
select 'enterprise_contracts.employee_id' as relation, count(*) as unresolved_rows
from enterprise_contracts
where employee_id is not null and employee_uuid is null
union all select 'enterprise_kpi_evaluations.employee_id', count(*)
from enterprise_kpi_evaluations
where employee_id is not null and employee_uuid is null
union all select 'enterprise_teams.organization_id', count(*)
from enterprise_teams
where organization_id is not null and organization_uuid is null
union all select 'enterprise_teams.department_id', count(*)
from enterprise_teams
where department_id is not null and department_uuid is null
order by relation;

\echo '== Duplicate business identifiers =='
select 'organizations.slug' as relation, count(*) as duplicate_groups
from (select slug from organizations group by slug having count(*) > 1) duplicates
union all select 'users.email', count(*)
from (select email from users group by email having count(*) > 1) duplicates
union all select 'attendance_records.employee_id_work_date', count(*)
from (select employee_id, work_date from attendance_records group by employee_id, work_date having count(*) > 1) duplicates
union all select 'projects.legacy_id', count(*)
from (select legacy_id from projects where legacy_id is not null group by legacy_id having count(*) > 1) duplicates
order by relation;

\echo '== V18 data-quality violations =='
select 'attendance_records.location_bounds' as relation, count(*) as violation_rows
from attendance_records
where (check_in_latitude is not null and check_in_latitude not between -90 and 90)
   or (check_in_longitude is not null and check_in_longitude not between -180 and 180)
   or (check_out_latitude is not null and check_out_latitude not between -90 and 90)
   or (check_out_longitude is not null and check_out_longitude not between -180 and 180)
union all select 'attendance_records.work_mode', count(*)
from attendance_records
where work_mode not in ('OFFICE', 'FIELD_TRIP', 'REMOTE')
union all select 'attendance_records.work_date', count(*)
from attendance_records
where work_date is null
union all select 'projects.organization_id', count(*)
from projects
where organization_id is null
union all select 'organization_branches.gps_bounds', count(*)
from organization_branches
where (gps_latitude is not null and gps_latitude not between -90 and 90)
   or (gps_longitude is not null and gps_longitude not between -180 and 180)
order by relation;
