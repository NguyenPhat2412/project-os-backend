#!/bin/sh
set -eu

# The monolith owns one database role and one canonical public schema.
# PostgreSQL's entrypoint runs this script against POSTGRES_DB during first initialization.
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'EOSQL'
create extension if not exists pg_trgm;
create extension if not exists "uuid-ossp";
create extension if not exists pgcrypto;
revoke create on schema public from public;
EOSQL
