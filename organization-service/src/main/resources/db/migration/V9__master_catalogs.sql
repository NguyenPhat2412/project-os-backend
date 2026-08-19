-- =====================================================================
-- V9: SYSTEM MASTER CATALOGS & ADMINISTRATIVE MASTER DATA
-- Backend persistence for Provinces, Ethnicities, Religions, Division Groups
-- Fully configurable by Admin in database, 0 hardcoding on frontend
-- =====================================================================

create table if not exists system_master_catalogs (
    id uuid primary key default gen_random_uuid(),
    category text not null, -- 'PROVINCE', 'ETHNICITY', 'RELIGION', 'DIVISION_GROUP', 'EDUCATION_LEVEL', 'EMPLOYMENT_TYPE'
    code text not null,
    name text not null,
    display_order int not null default 0,
    is_active boolean not null default true,
    metadata jsonb default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (category, code)
);

create index if not exists idx_master_catalogs_cat_active on system_master_catalogs(category, is_active, display_order);

-- Seed initial standardized Vietnam Master Data in Backend Database
insert into system_master_catalogs (category, code, name, display_order) values
-- Provinces
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

-- Ethnicities (54 Dân tộc Việt Nam)
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

-- Religions
('RELIGION', 'NONE', 'Không', 1),
('RELIGION', 'BUDDHISM', 'Phật giáo', 2),
('RELIGION', 'CATHOLICISM', 'Công giáo', 3),
('RELIGION', 'PROTESTANTISM', 'Tin Lành', 4),
('RELIGION', 'CAODAI', 'Cao Đài', 5),
('RELIGION', 'HOAHAO', 'Hòa Hảo', 6),
('RELIGION', 'ISLAM', 'Hồi giáo', 7),
('RELIGION', 'KHAC', 'Khác', 99),

-- Division Groups (Khối chuyên môn phòng ban)
('DIVISION_GROUP', 'TECH_ENG', 'Khối Kỹ thuật & Công nghệ', 1),
('DIVISION_GROUP', 'SALES_MKT', 'Khối Kinh doanh & Marketing', 2),
('DIVISION_GROUP', 'PRODUCTION', 'Khối Sản xuất & Vận hành Nhà máy', 3),
('DIVISION_GROUP', 'ADMIN_HR', 'Khối Hành chính - Quản trị Nhân sự', 4),
('DIVISION_GROUP', 'FIN_ACC', 'Khối Tài chính & Kế toán Doanh nghiệp', 5),
('DIVISION_GROUP', 'RD_CENTER', 'Khối Nghiên cứu & Phát triển (R&D)', 6),
('DIVISION_GROUP', 'QA_QC', 'Khối Đảm bảo Chất lượng & Tiêu chuẩn ISO', 7)
on conflict (category, code) do nothing;
