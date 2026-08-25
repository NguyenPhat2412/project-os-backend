alter table users add column status text not null default 'ACTIVE';
alter table users add constraint users_status_check check (status in ('ACTIVE', 'DISABLED'));
create index users_status_created_at_idx on users(status, created_at desc);
