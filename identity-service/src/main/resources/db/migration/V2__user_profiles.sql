create table user_profiles (
    user_id uuid primary key references users(id) on delete cascade,
    phone text,
    department text,
    title text,
    address text,
    timezone text,
    bio text,
    skills jsonb not null default '[]'::jsonb,
    notification_preferences jsonb not null default '{"email":true,"desktop":true,"slack":false}'::jsonb,
    notification_settings jsonb not null default '{}'::jsonb,
    appearance_preferences jsonb not null default '{}'::jsonb,
    created_at timestamptz not null,
    updated_at timestamptz not null
);
