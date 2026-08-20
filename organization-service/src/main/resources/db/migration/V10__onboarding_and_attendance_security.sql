-- =====================================================================
-- V10: ONBOARDING REQUESTS & ATTENDANCE SECURITY SCHEMA
-- Persistence layer for Self-service Onboarding & Dynamic QR Attendance
-- =====================================================================

-- 1. Onboarding Requests Table
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
    
    -- Banking & Tax
    tax_code text,
    social_insurance_number text,
    bank_name text,
    bank_account_number text,
    bank_account_holder text,
    
    -- Emergency
    emergency_contact_name text,
    emergency_contact_relationship text,
    emergency_contact_phone text,
    
    -- Education & Intro
    education_level text default 'Đại học',
    major_field text,
    personal_notes text,
    
    -- Security & Metadata
    token_used text,
    status text not null default 'PENDING' check (status in ('PENDING', 'APPROVED', 'REJECTED')),
    submitted_at timestamptz not null default now(),
    approved_at timestamptz,
    approved_by text,
    assigned_employee_code text,
    rejection_reason text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_onboarding_status on onboarding_requests(status);
create index if not exists idx_onboarding_code on onboarding_requests(code);

-- 2. Daily Attendance Records Table
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
    
    -- GPS Verification
    gps_latitude numeric(10,6),
    gps_longitude numeric(10,6),
    gps_distance_meters int default 0,
    gps_location_status text default 'IN_OFFICE',
    gps_location_summary text,
    
    -- Behavioral Analysis
    behavior_anomaly text,
    behavior_explanation text,
    action_required text,
    payroll_impact text,
    audit_note text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique(employee_code, work_date)
);

create index if not exists idx_attendance_date on attendance_records(work_date);
create index if not exists idx_attendance_emp on attendance_records(employee_code);

-- 3. Organization Branches Table
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
