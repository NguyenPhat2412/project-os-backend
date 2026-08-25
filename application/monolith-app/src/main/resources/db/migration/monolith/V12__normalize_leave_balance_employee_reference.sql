SET search_path = public;

ALTER TABLE enterprise_leave_balances
    ADD COLUMN IF NOT EXISTS employee_uuid uuid;

UPDATE enterprise_leave_balances b
SET employee_uuid = e.id
FROM employees e
WHERE b.employee_uuid IS NULL
  AND e.code = b.employee_code;

ALTER TABLE enterprise_leave_balances
    ADD CONSTRAINT enterprise_leave_balances_employee_uuid_fk
    FOREIGN KEY (employee_uuid) REFERENCES employees(id)
    ON DELETE RESTRICT NOT VALID;

CREATE INDEX IF NOT EXISTS enterprise_leave_balances_employee_uuid_idx
    ON enterprise_leave_balances (employee_uuid);

ALTER TABLE enterprise_leave_balances
    VALIDATE CONSTRAINT enterprise_leave_balances_employee_uuid_fk;

SET search_path = public;
