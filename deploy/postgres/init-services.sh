#!/bin/sh
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  --set=identity_password="$IDENTITY_DB_PASSWORD" \
  --set=project_password="$PROJECT_DB_PASSWORD" <<'EOSQL'
select format('create role identity_app login password %L', :'identity_password')
where not exists (select from pg_roles where rolname = 'identity_app') \gexec
select format('create role project_app login password %L', :'project_password')
where not exists (select from pg_roles where rolname = 'project_app') \gexec

create schema if not exists identity authorization identity_app;
create schema if not exists project authorization project_app;
grant connect on database project_os to identity_app, project_app;
revoke create on schema public from public;
EOSQL
