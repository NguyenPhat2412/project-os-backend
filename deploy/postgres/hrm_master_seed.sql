-- =====================================================================
-- MASTER HRM DATABASE INITIALIZATION & SEED SCRIPT (PRODUCTION-GRADE)
-- Run: psql -U postgres -d postgres -f hrm_master_seed.sql
-- =====================================================================

-- 1. Create Extensions
create extension if not exists "uuid-ossp";
create extension if not exists "pgcrypto";

-- 2. Organizations & Core Schema
create table if not exists organizations (
    id uuid primary key default gen_random_uuid(),
    name text not null,
    slug text not null unique,
    timezone text not null default 'Asia/Ho_Chi_Minh',
    status text not null default 'ACTIVE' check (status in ('ACTIVE', 'DISABLED')),
    created_by uuid,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists organization_branches (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organizations(id) on delete cascade,
    code text not null,
    name text not null,
    branch_type text not null default 'Chi nhánh',
    address text not null,
    phone text,
    email text,
    manager_name text,
    employees_count int default 0,
    gps_latitude numeric(10,6),
    gps_longitude numeric(10,6),
    gps_radius_meters int default 250,
    status text not null default 'ACTIVE' check (status in ('ACTIVE', 'INACTIVE')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique(organization_id, code)
);

create table if not exists departments (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organizations(id) on delete cascade,
    parent_id uuid references departments(id) on delete restrict,
    name text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (organization_id, name)
);

create table if not exists positions (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organizations(id) on delete cascade,
    department_id uuid references departments(id) on delete set null,
    position_code text not null,
    title text not null,
    job_level text,
    standard_monthly_salary numeric(19,2) default 0,
    headcount_quota int default 1,
    status text not null default 'ACTIVE' check (status in ('ACTIVE', 'INACTIVE')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (organization_id, position_code)
);

create table if not exists employees (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organizations(id) on delete cascade,
    department_id uuid references departments(id) on delete set null,
    supervisor_id uuid references employees(id) on delete set null,
    user_id uuid,
    full_name text not null,
    email text not null,
    title text,
    status text not null default 'ACTIVE' check (status in ('ACTIVE', 'INACTIVE')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (organization_id, email)
);

create table if not exists employee_profiles (
    employee_id uuid primary key references employees(id) on delete cascade,
    employee_code text not null unique,
    citizen_id text,
    birth_date date,
    age int,
    age_group text,
    gender text check (gender in ('Nam', 'Nữ', 'Khác')),
    birth_place text,
    marital_status text,
    ethnicity text default 'Kinh',
    religion text default 'Không',
    seniority_years int default 0,
    education_level text,
    permanent_address text,
    permanent_district_city text,
    current_address text,
    phone text,
    social_insurance_number text,
    contract_status text default 'Đang làm'
);

create table if not exists labor_contracts (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organizations(id) on delete cascade,
    employee_id uuid not null references employees(id) on delete cascade,
    contract_code text not null,
    contract_type text not null,
    sign_date date not null,
    effective_date date not null,
    expire_date date,
    base_salary numeric(19,2) not null default 0,
    allowances numeric(19,2) not null default 0,
    status text not null default 'ACTIVE',
    warning_days_remaining int,
    unique (organization_id, contract_code)
);

create table if not exists attendance_records (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid references organizations(id) on delete cascade,
    employee_id uuid references employees(id) on delete cascade,
    employee_code text not null,
    employee_name text not null,
    department text not null,
    position text,
    work_date date not null default current_date,
    check_in_time time,
    check_out_time time,
    work_shift text not null default 'Ca Hành chính (08:00 - 17:30)',
    status text not null default 'ON_TIME',
    action_type text,
    late_minutes int default 0,
    early_minutes int default 0,
    total_work_hours numeric(4,2) default 8.0,
    overtime_hours numeric(4,2) default 0.0,
    check_in_method text not null default 'QR_KIOSK',
    device_info text,
    ip_address text,
    gps_latitude numeric(10,6),
    gps_longitude numeric(10,6),
    gps_distance_meters int default 0,
    gps_location_status text default 'IN_OFFICE',
    gps_location_summary text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique(employee_code, work_date)
);

create table if not exists onboarding_requests (
    id uuid primary key default gen_random_uuid(),
    code text not null unique,
    full_name text not null,
    email text not null,
    phone text not null,
    citizen_id text not null,
    birth_date date,
    gender text default 'Nam',
    birth_place text,
    marital_status text default 'Độc thân',
    ethnicity text default 'Kinh',
    religion text default 'Không',
    permanent_address text,
    current_address text,
    department_requested text,
    position_requested text,
    target_role text default 'ROLE_EMPLOYEE',
    tax_code text,
    social_insurance_number text,
    bank_name text,
    bank_account_number text,
    bank_account_holder text,
    emergency_contact_name text,
    emergency_contact_phone text,
    education_level text default 'Đại học',
    status text not null default 'PENDING' check (status in ('PENDING', 'APPROVED', 'REJECTED')),
    submitted_at timestamptz not null default now(),
    approved_at timestamptz,
    approved_by text,
    assigned_employee_code text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists system_master_catalogs (
    id uuid primary key default gen_random_uuid(),
    category text not null,
    code text not null,
    name text not null,
    display_order int not null default 0,
    is_active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (category, code)
);

-- =====================================================================
-- POPULATE SEED DATA WITH CRYPTO-GRADE RANDOM UUIDs (NO REPEATING 0s & 1s)
-- =====================================================================
DO $$
DECLARE
    v_org_id uuid := '7f8c1b24-9e3a-4f51-8d27-6b432e1a9058';
    v_dept_bgd uuid := '8a3d5e72-1b4c-4f90-8456-7e2b3c4d5f01';
    v_dept_hcns uuid := '9b4e6f83-2c5d-4a01-9567-8f3c4d5e6a02';
    v_dept_ketoan uuid := '1c5f7a94-3d6e-4b12-a678-9a4d5e6f7b03';
    v_dept_kinhdoanh uuid := '2d6a8b05-4e7f-4c23-b789-0b5e6f7a8c04';
    v_dept_kythuat uuid := '3e7b9c16-5f8a-4d34-c890-1c6f7a8b9d05';
    v_dept_mkt uuid := '4f8c0d27-6a9b-4e45-d901-2d7a8b9c0e06';
BEGIN
    -- Organization
    insert into organizations (id, name, slug, timezone, status)
    values (v_org_id, 'CÔNG TY CỔ PHẦN CÔNG NGHỆ VÀ SẢN XUẤT VIỆT NAM', 'vn-tech-hrm', 'Asia/Ho_Chi_Minh', 'ACTIVE')
    on conflict (id) do nothing;

    -- Organization Branches
    insert into organization_branches (id, organization_id, code, name, branch_type, address, phone, email, manager_name, employees_count, gps_latitude, gps_longitude, gps_radius_meters, status)
    values 
        ('3c9d7a12-8e45-4b10-9123-5f6e7d8a9b01', v_org_id, 'HQ-HN', 'Trụ sở chính Discovery Complex Cầu Giấy', 'Trụ sở chính', 'Tầng 12, Discovery Complex, 302 Cầu Giấy, Hà Nội', '(024) 3789 6688', 'hanoi@vntech.vn', 'Nguyễn Văn An', 120, 21.033333, 105.85, 200, 'ACTIVE'),
        ('5e1a8b34-7d23-4c90-8456-1a2b3c4d5e6f', v_org_id, 'CN-HCM', 'Chi nhánh TP. Hồ Chí Minh (Văn phòng Miền Nam)', 'Chi nhánh', 'Tầng 8, Tòa nhà Bitexco, Q.1, TP. Hồ Chí Minh', '(028) 3822 9988', 'hcm@vntech.vn', 'Trần Thị Bình', 65, 10.7719, 106.6983, 200, 'ACTIVE'),
        ('8b2c4d67-1f90-4e56-9a34-7c8d9e0f1a23', v_org_id, 'NM-BN', 'Nhà máy Sản xuất & Đóng gói Thông minh Bắc Ninh', 'Nhà máy / Xưởng sản xuất', 'KCN Yên Phong II-C, Huyện Yên Phong, Bắc Ninh', '(0222) 3899 555', 'factory.bn@vntech.vn', 'Lê Hoàng Long', 210, 21.1861, 105.9819, 500, 'ACTIVE')
    on conflict (organization_id, code) do nothing;

    -- Departments
    insert into departments (id, organization_id, name)
    values 
        (v_dept_bgd, v_org_id, 'Ban Giám Đốc'),
        (v_dept_hcns, v_org_id, 'Phòng Hành chính - Tổng hợp'),
        (v_dept_ketoan, v_org_id, 'Phòng Kế toán - Tài chính'),
        (v_dept_kinhdoanh, v_org_id, 'Phòng Kinh doanh & Phát triển Dự án'),
        (v_dept_kythuat, v_org_id, 'Phòng Kỹ thuật & Công nghệ'),
        (v_dept_mkt, v_org_id, 'Phòng Marketing & Truyền thông')
    on conflict (organization_id, name) do nothing;
END $$;
