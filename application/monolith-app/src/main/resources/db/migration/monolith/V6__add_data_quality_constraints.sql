SET search_path = public;

ALTER TABLE company_policies
    ADD CONSTRAINT company_policies_morning_range_check
        CHECK (morning_start < morning_end),
    ADD CONSTRAINT company_policies_afternoon_range_check
        CHECK (afternoon_start < afternoon_end);

ALTER TABLE attendance_records
    ADD CONSTRAINT attendance_records_non_negative_values_check
        CHECK (coalesce(break_minutes, 0) >= 0
            AND coalesce(late_minutes, 0) >= 0
            AND coalesce(early_minutes, 0) >= 0
            AND coalesce(total_work_hours, 0) >= 0
            AND coalesce(overtime_hours, 0) >= 0);

ALTER TABLE employee_compensations
    ADD CONSTRAINT employee_compensations_non_negative_amount_check
        CHECK (monthly_amount >= 0);
