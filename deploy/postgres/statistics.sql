-- ProjectOS PostgreSQL health and size report (read-only).
select current_database() as database_name, version() as postgres_version;

select schemaname,
       relname as table_name,
       n_live_tup as estimated_rows,
       pg_size_pretty(pg_total_relation_size(format('%I.%I', schemaname, relname)::regclass)) as total_size,
       pg_size_pretty(pg_indexes_size(format('%I.%I', schemaname, relname)::regclass)) as index_size
from pg_stat_user_tables
where schemaname in ('identity', 'project', 'work', 'operations', 'knowledge', 'activity')
order by schemaname, relname;

select datname,
       numbackends as active_connections,
       round(100 * blks_hit::numeric / nullif(blks_hit + blks_read, 0), 2) as cache_hit_percent,
       xact_commit,
       xact_rollback
from pg_stat_database
where datname = current_database();

select schemaname,
       relname as table_name,
       indexrelname as index_name,
       idx_scan
from pg_stat_user_indexes
where schemaname in ('identity', 'project', 'work', 'operations', 'knowledge', 'activity')
order by schemaname, relname, indexrelname;
