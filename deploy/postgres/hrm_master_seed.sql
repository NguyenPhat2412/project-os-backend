-- =====================================================================
-- MASTER HRM DATABASE INITIALIZATION & SEED SCRIPT
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
    unique (employee_id, quota_year)
);

create table if not exists training_courses (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organizations(id) on delete cascade,
    course_code text not null unique,
    name text not null,
    category text not null,
    instructor text not null,
    location text not null,
    start_date date not null,
    end_date date not null,
    month_group text,
    total_hours int default 8,
    cost numeric(19,2) default 0,
    status text not null default 'PLANNED'
);

create table if not exists workplace_accidents (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organizations(id) on delete cascade,
    employee_id uuid not null references employees(id) on delete cascade,
    month_group text,
    accident_time timestamptz not null,
    accident_location text not null,
    cause text not null,
    injured_area text,
    diagnosis text,
    total_treatment_cost numeric(19,2) default 0,
    compensation_cost numeric(19,2) default 0,
    pay_type text not null default 'PAID',
    status text not null default 'RESOLVED'
);

create table if not exists company_resources (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organizations(id) on delete cascade,
    resource_code text not null unique,
    name text not null,
    category text not null,
    category_label text not null,
    serial_number text,
    specs text,
    purchase_date date,
    purchase_price numeric(19,2) default 0,
    location text not null,
    status text not null default 'AVAILABLE'
);

-- =====================================================================
-- POPULATE SEED DATA
-- =====================================================================
DO $$
DECLARE
    v_org_id uuid := 'a0000000-0000-0000-0000-000000000001';
    v_dept_bgd uuid := 'b0000000-0000-0000-0000-000000000001';
    v_dept_hcns uuid := 'b0000000-0000-0000-0000-000000000002';
    v_dept_ketoan uuid := 'b0000000-0000-0000-0000-000000000003';
    v_dept_kinhdoanh uuid := 'b0000000-0000-0000-0000-000000000004';
    v_dept_kythuat uuid := 'b0000000-0000-0000-0000-000000000005';
    v_dept_marketing uuid := 'b0000000-0000-0000-0000-000000000006';

    v_emp_an uuid := 'e0000000-0000-0000-0000-000000000001';
    v_emp_binh uuid := 'e0000000-0000-0000-0000-000000000002';
    v_emp_my uuid := 'e0000000-0000-0000-0000-000000000003';
    v_emp_bao uuid := 'e0000000-0000-0000-0000-000000000004';
    v_emp_giang uuid := 'e0000000-0000-0000-0000-000000000005';
    v_emp_ly uuid := 'e0000000-0000-0000-0000-000000000006';
    v_emp_trang uuid := 'e0000000-0000-0000-0000-000000000007';
    v_emp_anh uuid := 'e0000000-0000-0000-0000-000000000008';
    v_emp_vy uuid := 'e0000000-0000-0000-0000-000000000009';
    v_emp_hung uuid := 'e0000000-0000-0000-0000-000000000010';
    v_emp_chau uuid := 'e0000000-0000-0000-0000-000000000011';
    v_emp_phong uuid := 'e0000000-0000-0000-0000-000000000012';
    v_emp_thao uuid := 'e0000000-0000-0000-0000-000000000013';
    v_emp_cuc uuid := 'e0000000-0000-0000-0000-000000000014';
    v_emp_minh uuid := 'e0000000-0000-0000-0000-000000000015';
    v_emp_long uuid := 'e0000000-0000-0000-0000-000000000016';
BEGIN
    -- Organization
    insert into organizations (id, name, slug, timezone, status)
    values (v_org_id, 'CÔNG TY CỔ PHẦN CÔNG NGHỆ VÀ SẢN XUẤT VIỆT NAM', 'vn-tech-hrm', 'Asia/Ho_Chi_Minh', 'ACTIVE')
    on conflict (id) do nothing;

    -- Departments
    insert into departments (id, organization_id, name)
    values 
        (v_dept_bgd, v_org_id, 'Ban Giám Đốc'),
        (v_dept_hcns, v_org_id, 'Phòng Hành chính - Tổng hợp'),
        (v_dept_ketoan, v_org_id, 'Phòng Kế toán - Tài chính'),
        (v_dept_kinhdoanh, v_org_id, 'Phòng Kinh doanh'),
        (v_dept_kythuat, v_org_id, 'Phòng Kỹ thuật & Sản xuất'),
        (v_dept_marketing, v_org_id, 'Phòng Marketing')
    on conflict (id) do nothing;

    -- Employees
    insert into employees (id, organization_id, department_id, full_name, email, title, status)
    values
        (v_emp_an, v_org_id, v_dept_bgd, 'Nguyễn Văn An', 'an.nguyen@hrm-vietnam.vn', 'Tổng Giám Đốc', 'ACTIVE'),
        (v_emp_binh, v_org_id, v_dept_ketoan, 'Trần Thị Bình', 'binh.tran@hrm-vietnam.vn', 'Kế toán trưởng', 'ACTIVE'),
        (v_emp_my, v_org_id, v_dept_hcns, 'Bùi Diễm My', 'my.bui@hrm-vietnam.vn', 'Chuyên viên HCNS', 'ACTIVE'),
        (v_emp_bao, v_org_id, v_dept_kinhdoanh, 'Phan Gia Bảo', 'bao.phan@hrm-vietnam.vn', 'Trưởng phòng Kinh doanh', 'ACTIVE'),
        (v_emp_giang, v_org_id, v_dept_kythuat, 'Hoàng Thu Giang', 'giang.hoang@hrm-vietnam.vn', 'Quản đốc Xưởng', 'ACTIVE'),
        (v_emp_ly, v_org_id, v_dept_kinhdoanh, 'Lê Công Lý', 'ly.le@hrm-vietnam.vn', 'Chuyên viên Bán lẻ', 'ACTIVE'),
        (v_emp_trang, v_org_id, v_dept_hcns, 'Ngô Thùy Trang', 'trang.ngo@hrm-vietnam.vn', 'Chuyên viên mua hàng', 'ACTIVE'),
        (v_emp_anh, v_org_id, v_dept_kinhdoanh, 'Võ Phương Anh', 'anh.vo@hrm-vietnam.vn', 'Nhân viên kinh doanh', 'ACTIVE'),
        (v_emp_vy, v_org_id, v_dept_kinhdoanh, 'Lý Hà Vy', 'vy.ly@hrm-vietnam.vn', 'Nhân viên', 'ACTIVE'),
        (v_emp_hung, v_org_id, v_dept_hcns, 'Võ Tuấn Hùng', 'hung.vo@hrm-vietnam.vn', 'Thủ Kho', 'ACTIVE'),
        (v_emp_chau, v_org_id, v_dept_kinhdoanh, 'Trần Bảo Châu', 'chau.tran@hrm-vietnam.vn', 'Thực tập sinh', 'ACTIVE'),
        (v_emp_phong, v_org_id, v_dept_kinhdoanh, 'Võ Thanh Phong', 'phong.vo@hrm-vietnam.vn', 'Nhân viên', 'ACTIVE'),
        (v_emp_thao, v_org_id, v_dept_ketoan, 'Ngô Phương Thảo', 'thao.ngo@hrm-vietnam.vn', 'Kế toán viên', 'ACTIVE'),
        (v_emp_cuc, v_org_id, v_dept_ketoan, 'Nguyễn Thị Cúc', 'cuc.nguyen@hrm-vietnam.vn', 'Nhân viên kế toán', 'ACTIVE'),
        (v_emp_minh, v_org_id, v_dept_kinhdoanh, 'Phạm Quang Minh', 'minh.pham@hrm-vietnam.vn', 'Nhân viên', 'ACTIVE'),
        (v_emp_long, v_org_id, v_dept_kythuat, 'Đặng Văn Long', 'long.dang@hrm-vietnam.vn', 'Công nhân', 'ACTIVE')
    on conflict (id) do nothing;

    -- Profiles
    insert into employee_profiles (
        employee_id, employee_code, citizen_id, birth_date, age, age_group, gender, birth_place,
        marital_status, ethnicity, religion, seniority_years, education_level, permanent_address,
        permanent_district_city, current_address, phone, social_insurance_number, contract_status
    )
    values
        (v_emp_ly, 'NV003', '048099001234', '2003-04-05', 22, '18-25', 'Nam', 'Đà Nẵng', 'Đã có gia đình', 'Kinh', 'Không', 0, 'Đại học', 'Số 78, Đường 2 Tháng 9, P. Bình Hiên', 'Đà Nẵng', 'K12/5, Đường Lê Duẩn, Đà Nẵng', '0967890123', '2003204403', 'Đang làm'),
        (v_emp_trang, 'NV018', '052088005678', '1988-03-11', 37, '35-40', 'Nữ', 'Bình Định', 'Đã có gia đình', 'Kinh', 'Không', 4, 'Đại học', 'Thôn An Hòa 1, Xã Phước An, Tuy Phước', 'Bình Định', 'Số 30, Đường Nguyễn Tất Thành, Quy Nhơn', '0988123456', '2003204418', 'Đang làm'),
        (v_emp_anh, 'NV029', '042091009012', '1991-11-17', 34, '30-35', 'Nữ', 'Hà Tĩnh', 'Đã có gia đình', 'Kinh', 'Không', 3, 'Cao đẳng', 'Thôn Trung Tiến, Xã Cẩm Lạc, Cẩm Xuyên', 'Hà Tĩnh', 'Số 15, Đường Phan Đình Phùng, Hà Tĩnh', '0978901234', '2003204429', 'Đang làm'),
        (v_emp_vy, 'NV046', '054089003456', '1989-10-21', 36, '35-40', 'Nữ', 'Phú Yên', 'Đã có gia đình', 'Kinh', 'Không', 2, 'Đại học', 'Thôn Hội Sơn, Xã An Hòa Hải, Tuy An', 'Phú Yên', 'Số 55, Đường Trần Hưng Đạo, Tuy Hòa', '0912345678', '2003204446', 'Đang làm'),
        (v_emp_hung, 'NV055', '034092007890', '1992-10-14', 33, '30-35', 'Nam', 'Thái Bình', 'Ly hôn', 'Kinh', 'Không', 6, 'Trung cấp', 'Thôn La Uyên, Xã Minh Quang, Vũ Thư', 'Thái Bình', 'Số 19, Đường Lê Quý Đôn, Thái Bình', '0989012345', '2003204455', 'Đang làm'),
        (v_emp_chau, 'NV062', '001008001122', '2008-03-04', 17, '<18', 'Nữ', 'Hà Nội', 'Đã có gia đình', 'Kinh', 'Không', 0, 'Khác', 'Số 10, Ngõ 120 Hoàng Quốc Việt, Cầu Giấy', 'Hà Nội', 'Chung cư Imperia Sky Garden, Minh Khai, Hà Nội', '0911223344', '2003204462', 'Đang làm'),
        (v_emp_phong, 'NV067', '074098005566', '1998-10-15', 27, '25-30', 'Nữ', 'Bình Dương', 'Độc thân', 'Kinh', 'Không', 0, 'Đại học', 'Số 30/5 KP. Bình Đường 2, P. An Bình, Dĩ An', 'Bình Dương', 'Khu dân cư Việt Sing, Thuận An, Bình Dương', '0933445566', '2003204467', 'Đang làm'),
        (v_emp_thao, 'NV088', '004084009988', '1984-03-15', 41, '40-50', 'Nữ', 'Cao Bằng', 'Đã có gia đình', 'Kinh', 'Không', 21, 'Thạc sĩ', 'Tổ 15, Phường Hợp Giang', 'Cao Bằng', 'Số 55, Phố Kim Đồng, TP. Cao Bằng', '0944556677', '2003204488', 'Đang làm'),
        (v_emp_bao, 'NV100', '056085002233', '1985-10-10', 40, '40-50', 'Nam', 'Khánh Hòa', 'Ly hôn', 'Kinh', 'Không', 7, 'Đại học', 'Thôn Suối Lau 2, Xã Suối Cát, Cam Lâm', 'Khánh Hòa', 'Số 18, Đường Trần Quang Khải, Nha Trang', '0934567890', '2003204499', 'Đang làm'),
        (v_emp_binh, 'hrm', '001079008899', '1979-02-07', 46, '40-50', 'Nữ', 'Hưng Yên', 'Đã có gia đình', 'Kinh', 'Không', 23, 'Thạc sĩ', 'Xã Dạ Trạch, Khoái Châu, Hưng Yên', 'Hưng Yên', 'Mulberry Lane, Mỗ Lao, Hà Đông, Hà Nội', '0988776655', '2003204455', 'Đang làm'),
        (v_emp_my, 'NV094', '001090007788', '1990-04-01', 35, '35-40', 'Nữ', 'Hà Nội', 'Đã có gia đình', 'Kinh', 'Không', 12, 'Đại học', 'Số 12 phố Trần Nhân Tông, Hai Bà Trưng', 'Hà Nội', 'Số 12 phố Trần Nhân Tông, Hai Bà Trưng, Hà Nội', '0912345678', '7912345678', 'Đang làm')
    on conflict (employee_id) do nothing;

    -- Contracts
    insert into labor_contracts (
        organization_id, employee_id, contract_code, contract_type, sign_date, effective_date, expire_date,
        base_salary, allowances, status, warning_days_remaining
    )
    values
        (v_org_id, v_emp_binh, 'HD-2023-01', '6.Không thời hạn', '2023-01-01', '2023-01-01', null, 35000000, 5000000, 'ACTIVE', null),
        (v_org_id, v_emp_my, 'HD-2024-02', '5.Có thời hạn 3 năm', '2024-01-10', '2024-01-10', '2027-01-10', 22000000, 3000000, 'ACTIVE', 510),
        (v_org_id, v_emp_chau, 'HD-2026-TV01', '2.Thử việc 2 tháng', '2026-08-01', '2026-08-01', '2026-09-15', 8000000, 1000000, 'EXPIRING_SOON', 28),
        (v_org_id, v_emp_phong, 'HD-2025-1Y', '3.Có thời hạn 1 năm', '2025-09-01', '2025-09-01', '2026-09-01', 15000000, 2000000, 'EXPIRING_SOON', 14)
    on conflict do nothing;

    -- Enterprise Profile
    insert into enterprise_profiles (
        organization_id, code, company_name_vi, company_name_en, short_name, tax_code, established_date,
        legal_representative, representative_title, charter_capital, business_type, industry, hotline,
        email, website, headquarters_address, status
    )
    values (
        v_org_id, 'VNTECH-HOLDING', 'CÔNG TY CỔ PHẦN CÔNG NGHỆ VÀ SẢN XUẤT VIỆT NAM',
        'VIETNAM TECHNOLOGY & PRODUCTION JOINT STOCK COMPANY', 'VN-TECH GROUP', '0108964521', '2015-08-15',
        'Nguyễn Văn An', 'Tổng Giám Đốc', 50000000000.00, 'Công ty Cổ phần',
        'Công nghệ thông tin & Sản xuất giải pháp số', '1900 6868', 'contact@vntech.vn',
        'https://vntech.vn', 'Tầng 12, Tòa nhà Discovery Complex, 302 Cầu Giấy, Hà Nội', 'ACTIVE'
    )
    on conflict (organization_id) do nothing;

    -- Company Facilities / Branches
    insert into company_facilities (
        organization_id, facility_code, facility_name, facility_type, address, province_city, phone, email, manager_name, employees_count, gps_latitude, gps_longitude, gps_radius_meters, status
    )
    values
        (v_org_id, 'HQ-HN', 'Trụ sở chính Discovery Complex Cầu Giấy', 'TRU_SO_CHINH', 'Tầng 12, Discovery Complex, 302 Cầu Giấy, Hà Nội', 'Hà Nội', '(024) 3789 6688', 'hanoi@vntech.vn', 'Nguyễn Văn An', 120, 21.033333, 105.850000, 200, 'ACTIVE'),
        (v_org_id, 'CN-HCM', 'Chi nhánh TP. Hồ Chí Minh (Văn phòng Miền Nam)', 'CHI_NHANH', 'Tầng 8, Tòa nhà Bitexco, Q.1, TP. Hồ Chí Minh', 'TP. Hồ Chí Minh', '(028) 3822 9988', 'hcm@vntech.vn', 'Trần Thị Bình', 65, 10.771900, 106.698300, 200, 'ACTIVE'),
        (v_org_id, 'NM-BN', 'Nhà máy Sản xuất & Đóng gói Thông minh Bắc Ninh', 'NHA_MAY_XUONG', 'KCN Yên Phong II-C, Huyện Yên Phong, Bắc Ninh', 'Bắc Ninh', '(0222) 3899 555', 'factory.bn@vntech.vn', 'Lê Hoàng Long', 210, 21.186100, 105.981900, 500, 'ACTIVE')
    on conflict do nothing;

    -- Bank Accounts
    insert into enterprise_bank_accounts (
        organization_id, bank_name, account_number, account_holder, branch_name, swift_code, is_primary
    )
    values
        (v_org_id, 'Ngân hàng TMCP Ngoại thương Việt Nam (Vietcombank)', '0011 004 888 999', 'CTCP CONG NGHE VA SAN XUAT VIET NAM', 'Chi nhánh Thăng Long, Hà Nội', 'BFTVVNVX', true),
        (v_org_id, 'Ngân hàng TMCP Kỹ thương Việt Nam (Techcombank)', '1903 5566 7788 99', 'CTCP CONG NGHE VA SAN XUAT VIET NAM', 'Chi nhánh Cầu Giấy, Hà Nội', 'VTCBVNVX', false)
    on conflict do nothing;

    -- Login Portal SSO
    insert into enterprise_login_portals (
        organization_id, login_subdomain, admin_email, auth_method, allowed_email_domains, enforce_2fa, session_timeout_minutes
    )
    values
        (v_org_id, 'vntech', 'admin@vntech.vn', 'GOOGLE_SSO', array['vntech.vn', 'tathanhan.com'], true, 60)
    on conflict do nothing;

END $$;

-- =====================================================================
-- SYSTEM MASTER CATALOGS (Backend Database Master Persistence)
-- =====================================================================
create table if not exists system_master_catalogs (
    id uuid primary key default gen_random_uuid(),
    category text not null,
    code text not null,
    name text not null,
    display_order int not null default 0,
    is_active boolean not null default true,
    metadata jsonb default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (category, code)
);

insert into system_master_catalogs (category, code, name, display_order) values
('PROVINCE', 'HN', 'Hà Nội', 1),
('PROVINCE', 'HCM', 'TP. Hồ Chí Minh', 2),
('PROVINCE', 'DN', 'Đà Nẵng', 3),
('PROVINCE', 'HP', 'Hải Phòng', 4),
('PROVINCE', 'CT', 'Cần Thơ', 5),
('PROVINCE', 'NA', 'Nghệ An', 6),
('PROVINCE', 'TH', 'Thanh Hóa', 7),
('PROVINCE', 'BD', 'Bình Định', 8),
('PROVINCE', 'QN', 'Quảng Nam', 9),
('PROVINCE', 'QNI', 'Quảng Ninh', 10),
('PROVINCE', 'TB', 'Thái Bình', 11),
('PROVINCE', 'HD', 'Hải Dương', 12),
('PROVINCE', 'ND', 'Nam Định', 13),
('PROVINCE', 'NB', 'Ninh Bình', 14),
('PROVINCE', 'BN', 'Bắc Ninh', 15),
('PROVINCE', 'VP', 'Vĩnh Phúc', 16),
('PROVINCE', 'PT', 'Phú Thọ', 17),
('PROVINCE', 'LD', 'Lâm Đồng', 18),
('PROVINCE', 'DL', 'Đắk Lắk', 19),
('PROVINCE', 'DNONG', 'Đắk Nông', 20),
('PROVINCE', 'GL', 'Gia Lai', 21),
('PROVINCE', 'KH', 'Khánh Hòa', 22),
('PROVINCE', 'BDG', 'Bình Dương', 23),
('PROVINCE', 'DNAI', 'Đồng Nai', 24),
('PROVINCE', 'BRVT', 'Bà Rịa - Vũng Tàu', 25),
('PROVINCE', 'CM', 'Cà Mau', 26),
('PROVINCE', 'DB', 'Điện Biên', 27),
('PROVINCE', 'LC', 'Lai Châu', 28),
('PROVINCE', 'CB', 'Cao Bằng', 29),
('PROVINCE', 'HNA', 'Hà Nam', 30),
('ETHNICITY', 'KINH', 'Kinh', 1),
('ETHNICITY', 'TAY', 'Tày', 2),
('ETHNICITY', 'THAI', 'Thái', 3),
('ETHNICITY', 'MUONG', 'Mường', 4),
('ETHNICITY', 'HMONG', 'H''Mông', 5),
('ETHNICITY', 'DAO', 'Dao', 6),
('ETHNICITY', 'NUNG', 'Nùng', 7),
('ETHNICITY', 'GIARAI', 'Gia Rai', 8),
('ETHNICITY', 'EDE', 'Ê Đê', 9),
('ETHNICITY', 'BANA', 'Ba Na', 10),
('ETHNICITY', 'SANCHAY', 'Sán Chay', 11),
('ETHNICITY', 'CHAM', 'Chăm', 12),
('ETHNICITY', 'KHAC', 'Khác', 99),
('RELIGION', 'NONE', 'Không', 1),
('RELIGION', 'BUDDHISM', 'Phật giáo', 2),
('RELIGION', 'CATHOLICISM', 'Công giáo', 3),
('RELIGION', 'PROTESTANTISM', 'Tin Lành', 4),
('RELIGION', 'CAODAI', 'Cao Đài', 5),
('RELIGION', 'HOAHAO', 'Hòa Hảo', 6),
('RELIGION', 'ISLAM', 'Hồi giáo', 7),
('RELIGION', 'KHAC', 'Khác', 99),
('DIVISION_GROUP', 'TECH_ENG', 'Khối Kỹ thuật & Công nghệ', 1),
('DIVISION_GROUP', 'SALES_MKT', 'Khối Kinh doanh & Marketing', 2),
('DIVISION_GROUP', 'PRODUCTION', 'Khối Sản xuất & Vận hành Nhà máy', 3),
('DIVISION_GROUP', 'ADMIN_HR', 'Khối Hành chính - Quản trị Nhân sự', 4),
('DIVISION_GROUP', 'FIN_ACC', 'Khối Tài chính & Kế toán Doanh nghiệp', 5),
('DIVISION_GROUP', 'RD_CENTER', 'Khối Nghiên cứu & Phát triển (R&D)', 6),
('DIVISION_GROUP', 'QA_QC', 'Khối Đảm bảo Chất lượng & Tiêu chuẩn ISO', 7)
on conflict (category, code) do nothing;


