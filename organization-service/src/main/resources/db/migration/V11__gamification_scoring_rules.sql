-- =====================================================================
-- V11: GAMIFICATION & SCORING RULES CONFIGURATION SCHEMA
-- Dynamic, configurable scoring rules for attendance, training, and KPI
-- 0 Hardcoded values - Fully adjustable by Admin in Database
-- =====================================================================

create table if not exists gamification_scoring_rules (
    id uuid primary key default gen_random_uuid(),
    rule_code text not null unique,
    name text not null,
    points int not null default 10,
    category text not null default 'ATTENDANCE', -- 'ATTENDANCE', 'TRAINING', 'PERFORMANCE', 'CONTRIBUTION'
    description text,
    is_active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists employee_gamification_logs (
    id uuid primary key default gen_random_uuid(),
    employee_code text not null,
    employee_name text not null,
    rule_code text not null,
    rule_name text not null,
    points int not null,
    reason text,
    created_at timestamptz not null default now()
);

create index if not exists idx_gamification_emp on employee_gamification_logs(employee_code);

-- Seed default initial configurable rules
insert into gamification_scoring_rules (rule_code, name, points, category, description) values
('ON_TIME_CHECKIN', 'Chấm công đúng giờ', 10, 'ATTENDANCE', 'Đi làm đúng giờ chuẩn theo ca làm việc'),
('EARLY_CHECKIN', 'Đi làm sớm tích cực', 15, 'ATTENDANCE', 'Đến văn phòng sớm hơn 15 phút trước giờ làm'),
('LATE_CHECKIN', 'Đi muộn nhẹ có lý do', 5, 'ATTENDANCE', 'Đi muộn dưới 15 phút nhưng vẫn hoàn thành công việc'),
('PERFECT_WEEK', 'Chuyên cần tuần xuất sắc', 50, 'ATTENDANCE', 'Làm việc liên tục cả tuần không đi muộn ngày nào'),
('COMPLETE_TRAINING', 'Hoàn thành khóa đào tạo', 30, 'TRAINING', 'Tham gia và thi đạt chứng chỉ khóa đào tạo nội bộ'),
('TASK_COMPLETED_EARLY', 'Hoàn thành KPI trước hạn', 20, 'PERFORMANCE', 'Đạt chỉ tiêu công việc trước thời hạn giao'),
('SENIORITY_MILESTONE', 'Thâm niên cống hiến hàng năm', 100, 'CONTRIBUTION', 'Gắn bó cống hiến đủ mỗi năm làm việc')
on conflict (rule_code) do update set
    name = excluded.name,
    points = excluded.points,
    category = excluded.category,
    description = excluded.description;
