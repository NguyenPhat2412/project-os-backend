create table users (
    id uuid primary key,
    email text not null unique,
    password_hash text,
    display_name text not null,
    avatar_url text,
    role text not null check (role in ('ROOT_ADMIN', 'USER')),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table refresh_tokens (
    id uuid primary key,
    token_hash text not null unique,
    user_id uuid not null references users(id) on delete cascade,
    expires_at timestamptz not null,
    revoked_at timestamptz,
    created_at timestamptz not null
);

create index refresh_tokens_user_id_idx on refresh_tokens(user_id);
create index refresh_tokens_active_idx on refresh_tokens(expires_at) where revoked_at is null;
