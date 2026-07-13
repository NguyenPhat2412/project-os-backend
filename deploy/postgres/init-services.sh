#!/bin/sh
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  --set=identity_password="$IDENTITY_DB_PASSWORD" \
  --set=organization_password="$ORGANIZATION_DB_PASSWORD" \
  --set=attendance_password="$ATTENDANCE_DB_PASSWORD" \
  --set=project_password="$PROJECT_DB_PASSWORD" \
  --set=work_password="$WORK_DB_PASSWORD" \
  --set=operations_password="$OPERATIONS_DB_PASSWORD" \
  --set=knowledge_password="$KNOWLEDGE_DB_PASSWORD" \
  --set=activity_password="$ACTIVITY_DB_PASSWORD" <<'EOSQL'
select format('create role identity_app login password %L', :'identity_password')
where not exists (select from pg_roles where rolname = 'identity_app') \gexec
select format('create role organization_app login password %L', :'organization_password')
where not exists (select from pg_roles where rolname = 'organization_app') \gexec
select format('create role attendance_app login password %L', :'attendance_password')
where not exists (select from pg_roles where rolname = 'attendance_app') \gexec
select format('create role project_app login password %L', :'project_password')
where not exists (select from pg_roles where rolname = 'project_app') \gexec
select format('create role work_app login password %L', :'work_password')
where not exists (select from pg_roles where rolname = 'work_app') \gexec
select format('create role operations_app login password %L', :'operations_password')
where not exists (select from pg_roles where rolname = 'operations_app') \gexec
select format('create role knowledge_app login password %L', :'knowledge_password')
where not exists (select from pg_roles where rolname = 'knowledge_app') \gexec
select format('create role activity_app login password %L', :'activity_password')
where not exists (select from pg_roles where rolname = 'activity_app') \gexec

create schema if not exists identity authorization identity_app;
create schema if not exists organization authorization organization_app;
create schema if not exists attendance authorization attendance_app;
create schema if not exists project authorization project_app;
create schema if not exists work authorization work_app;
create schema if not exists operations authorization operations_app;
create schema if not exists knowledge authorization knowledge_app;
create schema if not exists activity authorization activity_app;
grant connect on database project_os to identity_app, organization_app, attendance_app, project_app, work_app, operations_app,
    knowledge_app, activity_app;
revoke create on schema public from public;
EOSQL
