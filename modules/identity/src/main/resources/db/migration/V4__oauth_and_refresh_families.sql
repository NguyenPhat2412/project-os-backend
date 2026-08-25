create table oauth_identities (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    provider text not null,
    provider_subject text not null,
    created_at timestamptz not null,
    unique (provider, provider_subject)
);
create index oauth_identities_user_idx on oauth_identities(user_id);

alter table refresh_tokens add column family_id uuid;
update refresh_tokens set family_id = id where family_id is null;
alter table refresh_tokens alter column family_id set not null;
create index refresh_tokens_family_idx on refresh_tokens(family_id) where revoked_at is null;
