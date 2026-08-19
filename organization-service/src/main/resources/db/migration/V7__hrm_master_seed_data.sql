-- =====================================================================
-- HRM MASTER SEED DATA (POPULATING REAL BACKEND DATABASE)
-- Contains 100% of data matching frontend AppSheet screens
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
    -- 1. SEED DEFAULT ORGANIZATION
    insert into organizations (id, name, slug, timezone, status, created_by, created_at, updated_at)
    values (
        v_org_id,
        'CÔNG TY CỔ PHẦN CÔNG NGHỆ VÀ SẢN XUẤT VIỆT NAM',
        'vn-tech-hrm',
        'Asia/Ho_Chi_Minh',
        'ACTIVE',
        v_emp_an,
        now(),
        now()
    ) on conflict (id) do update set name = excluded.name;

    -- 2. SEED DEPARTMENTS
    insert into departments (id, organization_id, name, created_at, updated_at)
    values 
        (v_dept_bgd, v_org_id, 'Ban Giám Đốc', now(), now()),
        (v_dept_hcns, v_org_id, 'Phòng Hành chính - Tổng hợp', now(), now()),
        (v_dept_ketoan, v_org_id, 'Phòng Kế toán - Tài chính', now(), now()),
        (v_dept_kinhdoanh, v_org_id, 'Phòng Kinh doanh', now(), now()),
        (v_dept_kythuat, v_org_id, 'Phòng Kỹ thuật & Sản xuất', now(), now()),
        (v_dept_marketing, v_org_id, 'Phòng Marketing', now(), now())
    on conflict (id) do nothing;

    -- 3. SEED POSITIONS
    insert into positions (id, organization_id, department_id, position_code, title, job_level, standard_monthly_salary, headcount_quota, status)
    values
        (gen_random_uuid(), v_org_id, v_dept_bgd, 'POS-BGD-01', 'Tổng Giám Đốc', 'C-Level', 80000000, 1, 'ACTIVE'),
        (gen_random_uuid(), v_org_id, v_dept_ketoan, 'POS-KT-01', 'Kế toán trưởng', 'Trưởng phòng', 35000000, 1, 'ACTIVE'),
        (gen_random_uuid(), v_org_id, v_dept_ketoan, 'POS-KT-02', 'Chuyên viên Kế toán', 'Chuyên viên', 18000000, 4, 'ACTIVE'),
        (gen_random_uuid(), v_org_id, v_dept_hcns, 'POS-HC-01', 'Chuyên viên Chế bản Label', 'Chuyên viên', 22000000, 2, 'ACTIVE'),
        (gen_random_uuid(), v_org_id, v_dept_hcns, 'POS-HC-02', 'Thủ Kho Nguyên vật liệu', 'Nhân viên', 15000000, 3, 'ACTIVE'),
        (gen_random_uuid(), v_org_id, v_dept_kinhdoanh, 'POS-KD-01', 'Trưởng phòng Kinh doanh', 'Trưởng phòng', 40000000, 1, 'ACTIVE'),
        (gen_random_uuid(), v_org_id, v_dept_kinhdoanh, 'POS-KD-02', 'Chuyên viên Bán lẻ', 'Chuyên viên', 16000000, 20, 'ACTIVE'),
        (gen_random_uuid(), v_org_id, v_dept_kythuat, 'POS-KT-01', 'Quản đốc Xưởng sản xuất', 'Trưởng phòng', 28000000, 1, 'ACTIVE'),
        (gen_random_uuid(), v_org_id, v_dept_kythuat, 'POS-KT-02', 'Công nhân Vận hành Máy', 'Công nhân', 12000000, 15, 'ACTIVE')
    on conflict do nothing;

    -- 4. SEED CORE EMPLOYEES
    insert into employees (id, organization_id, department_id, full_name, email, title, status, created_at, updated_at)
    values
        (v_emp_an, v_org_id, v_dept_bgd, 'Nguyễn Văn An', 'an.nguyen@hrm-vietnam.vn', 'Tổng Giám Đốc', 'ACTIVE', now(), now()),
        (v_emp_binh, v_org_id, v_dept_ketoan, 'Trần Thị Bình', 'binh.tran@hrm-vietnam.vn', 'Kế toán trưởng', 'ACTIVE', now(), now()),
        (v_emp_my, v_org_id, v_dept_hcns, 'Bùi Diễm My', 'my.bui@hrm-vietnam.vn', 'Chuyên viên HCNS', 'ACTIVE', now(), now()),
        (v_emp_bao, v_org_id, v_dept_kinhdoanh, 'Phan Gia Bảo', 'bao.phan@hrm-vietnam.vn', 'Trưởng phòng Kinh doanh', 'ACTIVE', now(), now()),
        (v_emp_giang, v_org_id, v_dept_kythuat, 'Hoàng Thu Giang', 'giang.hoang@hrm-vietnam.vn', 'Quản đốc Xưởng', 'ACTIVE', now(), now()),
        (v_emp_ly, v_org_id, v_dept_kinhdoanh, 'Lê Công Lý', 'ly.le@hrm-vietnam.vn', 'Chuyên viên Bán lẻ', 'ACTIVE', now(), now()),
        (v_emp_trang, v_org_id, v_dept_hcns, 'Ngô Thùy Trang', 'trang.ngo@hrm-vietnam.vn', 'Chuyên viên mua hàng', 'ACTIVE', now(), now()),
        (v_emp_anh, v_org_id, v_dept_kinhdoanh, 'Võ Phương Anh', 'anh.vo@hrm-vietnam.vn', 'Nhân viên kinh doanh', 'ACTIVE', now(), now()),
        (v_emp_vy, v_org_id, v_dept_kinhdoanh, 'Lý Hà Vy', 'vy.ly@hrm-vietnam.vn', 'Nhân viên', 'ACTIVE', now(), now()),
        (v_emp_hung, v_org_id, v_dept_hcns, 'Võ Tuấn Hùng', 'hung.vo@hrm-vietnam.vn', 'Thủ Kho', 'ACTIVE', now(), now()),
        (v_emp_chau, v_org_id, v_dept_kinhdoanh, 'Trần Bảo Châu', 'chau.tran@hrm-vietnam.vn', 'Thực tập sinh', 'ACTIVE', now(), now()),
        (v_emp_phong, v_org_id, v_dept_kinhdoanh, 'Võ Thanh Phong', 'phong.vo@hrm-vietnam.vn', 'Nhân viên', 'ACTIVE', now(), now()),
        (v_emp_thao, v_org_id, v_dept_ketoan, 'Ngô Phương Thảo', 'thao.ngo@hrm-vietnam.vn', 'Kế toán viên', 'ACTIVE', now(), now()),
        (v_emp_cuc, v_org_id, v_dept_ketoan, 'Nguyễn Thị Cúc', 'cuc.nguyen@hrm-vietnam.vn', 'Nhân viên kế toán', 'ACTIVE', now(), now()),
        (v_emp_minh, v_org_id, v_dept_kinhdoanh, 'Phạm Quang Minh', 'minh.pham@hrm-vietnam.vn', 'Nhân viên', 'ACTIVE', now(), now()),
        (v_emp_long, v_org_id, v_dept_kythuat, 'Đặng Văn Long', 'long.dang@hrm-vietnam.vn', 'Công nhân', 'ACTIVE', now(), now())
    on conflict (id) do nothing;

    -- 5. SEED DETAILED EMPLOYEE PROFILES (18 Fields)
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

    -- 6. SEED LABOR CONTRACTS (With 1-month automatic alert)
    insert into labor_contracts (
        organization_id, employee_id, contract_code, contract_type, sign_date, effective_date, expire_date,
        base_salary, allowances, status, warning_days_remaining
    )
    values
        (v_org_id, v_emp_binh, 'HD-2023-01', '6.Không thời hạn', '2023-01-01', '2023-01-01', null, 35000000, 5000000, 'ACTIVE', null),
        (v_org_id, v_emp_my, 'HD-2024-02', '5.Có thời hạn 3 năm', '2024-01-10', '2024-01-10', '2027-01-10', 22000000, 3000000, 'ACTIVE', 510),
        (v_org_id, v_emp_chau, 'HD-2026-TV01', '2.Thử việc 2 tháng', '2026-08-01', '2026-08-01', '2026-09-15', 8000000, 1000000, 'EXPIRING_SOON', 28),
        (v_org_id, v_emp_phong, 'HD-2025-1Y', '3.Có thời hạn 1 năm', '2025-09-01', '2025-09-01', '2026-09-01', 15000000, 2000000, 'EXPIRING_SOON', 14),
        (v_org_id, v_emp_anh, 'HD-2023-3Y', '5.Có thời hạn 3 năm', '2023-03-01', '2023-03-01', '2026-03-01', 16000000, 2500000, 'ACTIVE', null)
    on conflict do nothing;

    -- 7. SEED ANNUAL LEAVE QUOTAS
    insert into annual_leave_quotas (
        employee_id, quota_year, base_leave_days, seniority_bonus_days, hazardous_bonus_days, carried_over_days,
        total_quota_days, used_leave_days, remaining_leave_days
    )
    values
        (v_emp_binh, 2026, 12.0, 4.0, 0.0, 2.0, 18.0, 3.0, 15.0),
        (v_emp_binh, 2025, 12.0, 4.0, 0.0, 0.0, 16.0, 14.0, 2.0),
        (v_emp_my, 2026, 12.0, 2.0, 0.0, 1.5, 15.5, 2.0, 13.5),
        (v_emp_giang, 2026, 12.0, 2.0, 2.0, 0.0, 16.0, 1.0, 15.0),
        (v_emp_bao, 2026, 12.0, 1.0, 0.0, 0.0, 13.0, 4.0, 9.0)
    on conflict do nothing;

    -- 8. SEED COMPANY REGULATIONS CATALOG
    insert into company_regulations_catalog (organization_id, code, type, title, clause_number, description, base_amount, effective_date, status)
    values
        (v_org_id, 'NQ-01', 'THUONG', 'Thưởng chuyên cần tháng', 'Điều 12.1', 'Thưởng nhân sự đi làm đủ công và không đi muộn trong tháng', 500000, '2025-01-01', 'ACTIVE'),
        (v_org_id, 'NQ-02', 'THUONG', 'Thưởng vượt chỉ tiêu doanh số', 'Điều 14.2', 'Thưởng hoàn thành xuất sắc KPI doanh số quý', 3000000, '2025-01-01', 'ACTIVE'),
        (v_org_id, 'NQ-03', 'PHAT', 'Vi phạm quy định giờ giấc', 'Điều 8.2', 'Đi làm muộn quá 15 phút không báo trước', 100000, '2025-01-01', 'ACTIVE'),
        (v_org_id, 'NQ-04', 'PHAT', 'Không mang trang bị BHLĐ', 'Điều 20.3', 'Không đội mũ/kính bảo hộ khi vào khu vực vận hành máy', 200000, '2025-01-01', 'ACTIVE')
    on conflict do nothing;

    -- 9. SEED TRAINING COURSES
    insert into training_courses (
        organization_id, course_code, name, category, instructor, location, start_date, end_date,
        month_group, total_hours, cost, status
    )
    values
        (v_org_id, 'DT-2025-08', 'Huấn luyện An toàn & Vệ sinh Lao động 2025', 'An toàn BHLĐ', 'TS. Nguyễn Văn Hùng', 'Hội trường Tầng 18', '2025-08-15', '2025-08-17', '2025/08', 16, 25000000, 'COMPLETED'),
        (v_org_id, 'DT-2025-09', 'Nâng cao Kỹ năng Chăm sóc Khách hàng B2B', 'Kỹ năng mềm', 'ThS. Lê Minh Đức', 'Phòng họp VIP Diamond', '2025-09-10', '2025-09-12', '2025/09', 12, 18000000, 'COMPLETED'),
        (v_org_id, 'DT-2026-08', 'Đào tạo Vận hành Dây chuyền In dập tự động', 'Kỹ thuật', 'Kỹ sư Trương Quốc Khánh', 'Nhà máy Hải Phòng', '2026-08-20', '2026-08-25', '2026/08', 24, 30000000, 'PLANNED')
    on conflict do nothing;

    -- 10. SEED COMPANY PROFILE & BRANCHES
    insert into company_profiles (
        organization_id, company_name_vi, company_name_en, short_name, tax_code, founded_date,
        legal_representative, legal_representative_title, charter_capital, business_type, industry,
        hotline, email, website, headquarters_address, total_employees, departments_count, iso_certifications
    )
    values (
        v_org_id,
        'CÔNG TY CỔ PHẦN CÔNG NGHỆ VÀ SẢN XUẤT VIỆT NAM',
        'VIETNAM TECHNOLOGY & PRODUCTION JOINT STOCK COMPANY',
        'VN-TECH GROUP / HRM OS',
        '0108964521',
        '2015-08-15',
        'Nguyễn Văn An',
        'Tổng Giám Đốc',
        '50.000.000.000 VNĐ (Năm mươi tỷ đồng)',
        'Công ty Cổ phần',
        'Công nghệ thông tin, Sản xuất bao bì & Giải pháp Quản trị Doanh nghiệp',
        '1900 6868 - (024) 3789 6688',
        'contact@hrm-vietnam.vn',
        'https://hrm-os.vn',
        'Tầng 18, Tòa nhà Discovery Complex, 302 Cầu Giấy, P. Dịch Vọng, Q. Cầu Giấy, Hà Nội',
        83,
        6,
        array['ISO 9001:2015', 'ISO 27001:2022', 'OHSAS 18001']
    ) on conflict (organization_id) do nothing;

    -- 11. SEED COMPANY RESOURCES
    insert into company_resources (
        organization_id, resource_code, name, category, category_label, serial_number, specs,
        purchase_date, purchase_price, warranty_until, location, current_assignee_id, status
    )
    values
        (v_org_id, 'TN-LAP01', 'MacBook Pro 16" M3 Max 36GB / 1TB SSD', 'it_device', 'Thiết bị IT', 'C02G9988MD6R', 'Apple M3 Max 14-core, 36GB RAM, 1TB SSD', '2025-08-15', 68000000, '2027-08-15', 'Tầng 18 Discovery', v_emp_binh, 'IN_USE'),
        (v_org_id, 'TN-CAR01', 'Xe 7 chỗ Toyota Fortuner 2.8L Legender', 'vehicle', 'Xe & Phương tiện', '29A-889.99', 'Diesel 2.8L 4x4 AT, 7 chỗ ngồi', '2024-03-01', 1250000000, '2027-03-01', 'Gara Hầm B2 Discovery', v_emp_an, 'IN_USE'),
        (v_org_id, 'TN-MEET01', 'Phòng họp VIP Diamond (20 chỗ + LED 100")', 'room', 'Phòng họp', null, 'LED 100", Polycom 4K, 20 ghế da', '2025-01-01', 150000000, null, 'Tầng 18 Discovery', null, 'AVAILABLE'),
        (v_org_id, 'TN-PRN01', 'Máy in Photocopy Fuji Xerox ApeosPort 4570', 'office_equipment', 'Thiết bị VP', 'FX-AP-4570-01', 'In/Scan màu 45 trang/phút Khổ A3/A4', '2024-01-01', 65000000, '2027-01-01', 'Khu In ấn Tầng 18', null, 'AVAILABLE')
    on conflict do nothing;

    -- 12. SEED MATERNITY RECORDS
    insert into maternity_records (
        employee_id, month_group, expected_due_date, pregnancy_start_date, month7_date, month8_date,
        month9_date, actual_leave_date, actual_birth_date, number_of_children, delivery_type, return_to_work_date, status
    )
    values
        (v_emp_cuc, '2025/10', '10/2025', '2025-01-08', '2025-07-23', '2025-08-20', '2025-09-17', '2025-10-15', '2025-10-25', 1, 'Sinh thường', '2026-04-25', 'RETURNED_NURSING'),
        (v_emp_my, '2026/09', '09/2026', '2025-12-15', '2026-06-15', '2026-07-15', '2026-08-15', null, null, 1, 'Sinh thường', '2027-03-01', 'UPCOMING')
    on conflict do nothing;

    -- 13. SEED WORKPLACE ACCIDENTS
    insert into workplace_accidents (
        organization_id, employee_id, month_group, accident_time, accident_location, cause,
        injured_area, diagnosis, initial_treatment, treatment_place, total_treatment_cost, compensation_cost, pay_type, status
    )
    values
        (v_org_id, v_emp_binh, '2025/08', '2025-08-15 14:35:00+07', 'Phòng A', 'Sơ suất', 'Tay', 'Trầy xước phần mềm', 'Sơ cứu tại chỗ', 'Phòng y tế', 0, 0, 'UNPAID', 'RESOLVED'),
        (v_org_id, v_emp_minh, '2025/06', '2025-06-07 23:27:00+07', 'Phòng A', 'Sơ suất khi di chuyển hàng mẫu', 'Tay', 'Gãy tay', 'Đưa bệnh viện', 'Bệnh viện Đa khoa', 4200000, 4200000, 'PAID', 'RESOLVED'),
        (v_org_id, v_emp_long, '2026/08', '2026-08-10 10:15:00+07', 'Xưởng 2', 'Trơn trượt tại sàn thao tác máy in', 'Chân', 'Bong gân cổ chân phải', 'Sơ cứu và chuyển phòng khám', 'Phòng khám Đa khoa', 1500000, 1500000, 'PAID', 'TREATING')
    on conflict do nothing;

END $$;
