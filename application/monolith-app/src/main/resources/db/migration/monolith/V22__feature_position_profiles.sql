create table if not exists public.organization_feature_position_profiles (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references public.organizations(id) on delete cascade,
    name varchar(150) not null,
    code varchar(80) not null,
    department varchar(150),
    description varchar(4000),
    icon_bg varchar(16) not null default 'blue',
    allowed_feature_keys jsonb not null default '[]'::jsonb,
    created_by uuid,
    updated_by uuid,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint organization_feature_position_profiles_icon_bg_ck check (icon_bg in ('blue', 'red', 'yellow')),
    constraint organization_feature_position_profiles_keys_array_ck check (jsonb_typeof(allowed_feature_keys) = 'array'),
    constraint organization_feature_position_profiles_org_code_uq unique (organization_id, code)
);

create index if not exists organization_feature_position_profiles_org_updated_idx
    on public.organization_feature_position_profiles (organization_id, updated_at desc);
