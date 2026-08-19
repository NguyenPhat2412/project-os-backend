-- =====================================================================
-- HRM FULL ENTERPRISE DATABASE SCHEMA
-- Compatible with PostgreSQL 14+ / Supabase / Neon / Spring Boot JPA
-- =====================================================================

-- 1. POSITIONS & JOB TITLES (Vị trí công tác & Lịch sử bổ nhiệm)
create table if not exists positions (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organizations(id) on delete cascade,
    department_id uuid references departments(id) on delete set null,
    position_code text not null,
    title text not null,
    job_level text, -- Cấp bậc: C-Level, Trưởng phòng, Chuyên viên, Nhân viên, Công nhân
    standard_monthly_salary numeric(19,2) default 0,
    headcount_quota int default 1,
    status text not null default 'ACTIVE' check (status in ('ACTIVE', 'INACTIVE')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (organization_id, position_code)
);

create table if not exists employee_position_history (
    id uuid primary key default gen_random_uuid(),
    employee_id uuid not null references employees(id) on delete cascade,
    position_id uuid not null references positions(id) on delete cascade,
    department_id uuid not null references departments(id) on delete cascade,
    start_date date not null,
    end_date date,
    decision_number text,
    is_current boolean not null default true,
    notes text,
    created_at timestamptz not null default now()
);

-- 2. EXTENDED EMPLOYEE PROFILES (Hồ sơ nhân sự chi tiết 18 trường)
create table if not exists employee_profiles (
    employee_id uuid primary key references employees(id) on delete cascade,
    employee_code text not null unique,
    citizen_id text, -- CCCD / CMND
    citizen_id_issue_date date,
    citizen_id_issue_place text,
    birth_date date,
    age int,
    age_group text, -- '<18', '18-25', '25-30', '30-35', '35-40', '40-50', '50+'
    gender text check (gender in ('Nam', 'Nữ', 'Khác')),
    birth_place text,
    marital_status text, -- 'Đã có gia đình', 'Ly hôn', 'Độc thân'
    ethnicity text default 'Kinh',
    religion text default 'Không',
    seniority_years int default 0,
    education_level text, -- 'Đại học', 'Thạc sĩ', 'Tiến sĩ', 'Cao đẳng', 'Trung cấp', 'Khác'
    permanent_address text,
    permanent_district_city text,
    current_address text,
    phone text,
    social_insurance_number text,
    personal_tax_code text,
    bank_account_number text,
    bank_name text,
    bank_branch text,
    avatar_url text,
    start_working_date date,
    contract_status text default 'Đang làm' check (contract_status in ('Đang làm', 'Đã nghỉ', 'Nghỉ thai sản')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- 3. LABOR CONTRACTS (Quản lý hợp đồng lao động & Cảnh báo)
create table if not exists labor_contracts (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organizations(id) on delete cascade,
    employee_id uuid not null references employees(id) on delete cascade,
    contract_code text not null,
    contract_type text not null, -- '2.Thử việc 2 tháng', '3.Có thời hạn 1 năm', '5.Có thời hạn 3 năm', '6.Không thời hạn'
    sign_date date not null,
    effective_date date not null,
    expire_date date,
    base_salary numeric(19,2) not null default 0,
    allowances numeric(19,2) not null default 0,
    performance_bonus numeric(19,2) default 0,
    sign_count int default 1,
    signer_name text,
    signer_title text,
    status text not null default 'ACTIVE' check (status in ('ACTIVE', 'EXPIRING_SOON', 'EXPIRED', 'TERMINATED')),
    warning_days_remaining int,
    attachment_url text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (organization_id, contract_code)
);

-- 4. ANNUAL LEAVE & LEAVE REQUESTS (Phép năm, Nghỉ phép & Cấp phép)
create table if not exists annual_leave_quotas (
    id uuid primary key default gen_random_uuid(),
    employee_id uuid not null references employees(id) on delete cascade,
    quota_year int not null,
    base_leave_days numeric(4,1) not null default 12.0,
    seniority_bonus_days numeric(4,1) not null default 0.0,
    hazardous_bonus_days numeric(4,1) not null default 0.0,
    carried_over_days numeric(4,1) not null default 0.0,
    total_quota_days numeric(4,1) not null default 12.0,
    used_leave_days numeric(4,1) not null default 0.0,
    remaining_leave_days numeric(4,1) not null default 12.0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (employee_id, quota_year)
);

create table if not exists leave_requests (
    id uuid primary key default gen_random_uuid(),
    employee_id uuid not null references employees(id) on delete cascade,
    leave_type text not null, -- 'Nghỉ phép năm', 'Nghỉ ốm đau', 'Nghỉ việc riêng', 'Nghỉ không lương'
    start_date date not null,
    end_date date not null,
    total_days numeric(4,1) not null,
    reason text not null,
    handover_to uuid references employees(id),
    status text not null default 'PENDING' check (status in ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    approved_by uuid references employees(id),
    approval_date timestamptz,
    approval_notes text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- 5. COMPANY REGULATIONS & REWARDS/DISCIPLINE (Nội quy, Thưởng & Kỷ luật)
create table if not exists company_regulations_catalog (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organizations(id) on delete cascade,
    code text not null unique,
    type text not null check (type in ('THUONG', 'PHAT')),
    title text not null,
    clause_number text,
    description text not null,
    base_amount numeric(19,2) default 0,
    points_impact int default 0,
    effective_date date not null,
    status text not null default 'ACTIVE'
);

create table if not exists regulation_records (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organizations(id) on delete cascade,
    employee_id uuid not null references employees(id) on delete cascade,
    catalog_id uuid references company_regulations_catalog(id),
    record_type text not null check (record_type in ('THUONG', 'PHAT')),
    title text not null,
    decision_number text,
    event_date date not null,
    amount numeric(19,2) default 0,
    reason text not null,
    issued_by uuid references employees(id),
    notes text,
    created_at timestamptz not null default now()
);

-- 6. TRAINING PROGRAMS & ATTENDEES (Đào tạo & Nhân sự đào tạo)
create table if not exists training_courses (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organizations(id) on delete cascade,
    course_code text not null unique,
    name text not null,
    category text not null, -- 'Hội nhập', 'Kỹ thuật', 'An toàn BHLĐ', 'Kỹ năng mềm', 'Nâng cao'
    instructor text not null,
    location text not null,
    start_date date not null,
    end_date date not null,
    month_group text, -- '2025/08', '2025/09', '2026/08'
    total_hours int default 8,
    cost numeric(19,2) default 0,
    max_attendees int default 30,
    status text not null default 'PLANNED' check (status in ('PLANNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists training_attendees (
    id uuid primary key default gen_random_uuid(),
    course_id uuid not null references training_courses(id) on delete cascade,
    employee_id uuid not null references employees(id) on delete cascade,
    attendance_rate numeric(5,2) default 100.0,
    pre_test_score numeric(4,1),
    final_test_score numeric(4,1),
    evaluation_result text check (evaluation_result in ('Xuất sắc', 'Đạt yêu cầu', 'Không đạt', 'Chưa thi')),
    certificate_url text,
    notes text,
    created_at timestamptz not null default now()
);

-- 7. DOCUMENTS & SOP KNOWLEDGE (Tài liệu & Quy trình SOP)
create table if not exists company_documents (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organizations(id) on delete cascade,
    document_code text not null unique,
    title text not null,
    category text not null, -- 'Bản Mô tả Công việc', 'Quy trình SOP', 'Biểu mẫu Nhân sự', 'Chính sách Doanh nghiệp'
    department_id uuid references departments(id),
    version text not null default 'v1.0',
    effective_date date not null,
    expiry_date date,
    file_type text default 'PDF',
    file_size_bytes bigint,
    file_url text not null,
    approved_by text,
    status text not null default 'EFFECTIVE' check (status in ('PENDING_APPROVAL', 'EFFECTIVE', 'EXPIRED', 'ARCHIVED')),
    download_count int default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- 8. MATERNITY BENEFITS (Chế độ thai sản & Nuôi con nhỏ)
create table if not exists maternity_records (
    id uuid primary key default gen_random_uuid(),
    employee_id uuid not null references employees(id) on delete cascade,
    month_group text, -- '2025/10', '2025/11', '2026/09'
    expected_due_date text,
    pregnancy_start_date date,
    month7_date date,
    month8_date date,
    month9_date date,
    actual_leave_date date,
    actual_birth_date date,
    number_of_children int default 1,
    delivery_type text default 'Sinh thường' check (delivery_type in ('Sinh thường', 'Sinh mổ', 'Sinh đôi')),
    return_to_work_date date,
    child_under_12m_reduction_hour boolean default true,
    social_insurance_benefit_amount numeric(19,2) default 0,
    status text not null default 'UPCOMING' check (status in ('UPCOMING', 'ON_LEAVE', 'RETURNED_NURSING', 'COMPLETED')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- 9. WORKPLACE ACCIDENTS (Tai nạn lao động & Bồi thường)
create table if not exists workplace_accidents (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organizations(id) on delete cascade,
    employee_id uuid not null references employees(id) on delete cascade,
    month_group text, -- '2025/08', '2025/06', '2026/08'
    accident_time timestamptz not null,
    accident_location text not null,
    cause text not null,
    injured_area text,
    diagnosis text,
    initial_treatment text,
    treatment_place text,
    total_treatment_cost numeric(19,2) default 0,
    compensation_cost numeric(19,2) default 0,
    pay_type text not null default 'PAID' check (pay_type in ('PAID', 'UNPAID')),
    status text not null default 'RESOLVED' check (status in ('RESOLVED', 'TREATING', 'INVESTIGATING')),
    sick_leave_certificate_url text,
    created_by uuid references employees(id),
    created_at timestamptz not null default now()
);

-- 10. COMPANY RESOURCES & ASSETS (Tài nguyên, Thiết bị & Xe cộ)
create table if not exists company_resources (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organizations(id) on delete cascade,
    resource_code text not null unique,
    name text not null,
    category text not null, -- 'it_device', 'vehicle', 'room', 'office_equipment', 'software_license'
    category_label text not null,
    serial_number text,
    specs text,
    purchase_date date,
    purchase_price numeric(19,2) default 0,
    warranty_until date,
    supplier text,
    location text not null,
    current_assignee_id uuid references employees(id),
    assigned_date date,
    status text not null default 'AVAILABLE' check (status in ('AVAILABLE', 'IN_USE', 'MAINTENANCE')),
    notes text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists resource_handovers (
    id uuid primary key default gen_random_uuid(),
    resource_id uuid not null references company_resources(id) on delete cascade,
    employee_id uuid not null references employees(id) on delete cascade,
    handover_date date not null,
    return_date date,
    purpose text not null,
    status text not null default 'ACTIVE' check (status in ('ACTIVE', 'RETURNED')),
    created_at timestamptz not null default now()
);

-- 11. COMPANY PROFILE & BRANCHES (Hồ sơ doanh nghiệp & Chi nhánh)
create table if not exists company_profiles (
    organization_id uuid primary key references organizations(id) on delete cascade,
    company_name_vi text not null,
    company_name_en text,
    short_name text,
    tax_code text not null,
    founded_date date,
    legal_representative text,
    legal_representative_title text,
    charter_capital text,
    business_type text,
    industry text,
    hotline text,
    email text,
    website text,
    headquarters_address text not null,
    total_employees int default 83,
    departments_count int default 6,
    iso_certifications text[],
    updated_at timestamptz not null default now()
);

create table if not exists company_branches (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organizations(id) on delete cascade,
    name text not null,
    branch_type text not null, -- 'Trụ sở chính', 'Chi nhánh', 'Nhà máy / Xưởng sản xuất'
    address text not null,
    phone text,
    email text,
    manager_name text,
    employees_count int default 0,
    created_at timestamptz not null default now()
);

create table if not exists company_bank_accounts (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organizations(id) on delete cascade,
    bank_name text not null,
    account_number text not null,
    account_holder text not null,
    branch text,
    swift_code text,
    is_primary boolean default false,
    created_at timestamptz not null default now()
);
