-- =====================================================================
-- V8: ENTERPRISE PROFILES, FACILITIES / BRANCHES, BANK ACCOUNTS & LOGIN DOMAINS
-- Comprehensive backend persistence schema for Multi-Enterprise Architecture
-- =====================================================================

-- 1. Enterprise Profile Details (Extends organizations)
create table if not exists enterprise_profiles (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organizations(id) on delete cascade unique,
    code text not null unique,
    company_name_vi text not null,
    company_name_en text,
    short_name text,
    tax_code text not null unique,
    established_date date,
    legal_representative text not null,
    representative_title text not null default 'Tổng Giám Đốc',
    charter_capital numeric(19, 2) default 0,
    business_type text not null default 'Công ty Cổ phần',
    industry text,
    hotline text,
    email text not null,
    website text,
    headquarters_address text not null,
    status text not null default 'ACTIVE' check (status in ('ACTIVE', 'PENDING', 'INACTIVE')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- 2. Enterprise Facilities / Branches / Factories
create table if not exists company_facilities (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organizations(id) on delete cascade,
    facility_code text not null,
    facility_name text not null,
    facility_type text not null check (facility_type in ('TRU_SO_CHINH', 'CHI_NHANH', 'NHA_MAY_XUONG', 'TRUNG_TAM_RD', 'VAN_PHONG_DAI_DIEN')),
    address text not null,
    province_city text,
    phone text,
    email text,
    manager_name text,
    employees_count int not null default 0,
    gps_latitude numeric(10, 6) default 21.033333,
    gps_longitude numeric(10, 6) default 105.850000,
    gps_radius_meters int not null default 200,
    status text not null default 'ACTIVE' check (status in ('ACTIVE', 'INACTIVE')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (organization_id, facility_code)
);

-- 3. Corporate Bank Accounts
create table if not exists enterprise_bank_accounts (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organizations(id) on delete cascade,
    bank_name text not null,
    account_number text not null,
    account_holder text not null,
    branch_name text,
    swift_code text,
    is_primary boolean not null default false,
    created_at timestamptz not null default now(),
    unique (organization_id, account_number)
);

-- 4. Enterprise Login & Authentication Portal Configurations
create table if not exists enterprise_login_portals (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organizations(id) on delete cascade unique,
    login_subdomain text not null unique,
    admin_email text not null,
    auth_method text not null default 'PASSWORD' check (auth_method in ('PASSWORD', 'GOOGLE_SSO', 'AZURE_AD', 'HYBRID')),
    allowed_email_domains text[] not null default '{}',
    enforce_2fa boolean not null default false,
    session_timeout_minutes int not null default 60,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- Indexes for high throughput multi-tenant lookup
create index if not exists idx_enterprise_profiles_org on enterprise_profiles(organization_id);
create index if not exists idx_company_facilities_org on company_facilities(organization_id, status);
create index if not exists idx_enterprise_bank_accounts_org on enterprise_bank_accounts(organization_id);
create index if not exists idx_enterprise_login_portals_subdomain on enterprise_login_portals(login_subdomain);
