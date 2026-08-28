SET search_path = public;

-- Keep legacy business-code columns intact. These UUID tenant columns make the
-- existing employee links enforce the same organization boundary as the
-- canonical domain tables.
ALTER TABLE enterprise_contracts
    ADD COLUMN IF NOT EXISTS organization_uuid uuid;
ALTER TABLE enterprise_kpi_evaluations
    ADD COLUMN IF NOT EXISTS organization_uuid uuid;
ALTER TABLE enterprise_leave_balances
    ADD COLUMN IF NOT EXISTS organization_uuid uuid;

-- This is a deterministic backfill from an already validated employee UUID FK;
-- it does not guess from names or business codes.
UPDATE enterprise_contracts c
SET organization_uuid = e.organization_id
FROM employees e
WHERE c.organization_uuid IS NULL
  AND c.employee_uuid = e.id;

UPDATE enterprise_kpi_evaluations k
SET organization_uuid = e.organization_id
FROM employees e
WHERE k.organization_uuid IS NULL
  AND k.employee_uuid = e.id;

UPDATE enterprise_leave_balances b
SET organization_uuid = e.organization_id
FROM employees e
WHERE b.organization_uuid IS NULL
  AND b.employee_uuid = e.id;

ALTER TABLE enterprise_contracts
    ADD CONSTRAINT enterprise_contracts_employee_org_fk
    FOREIGN KEY (employee_uuid, organization_uuid)
    REFERENCES employees (id, organization_id)
    ON DELETE RESTRICT NOT VALID;

ALTER TABLE enterprise_kpi_evaluations
    ADD CONSTRAINT enterprise_kpi_evaluations_employee_org_fk
    FOREIGN KEY (employee_uuid, organization_uuid)
    REFERENCES employees (id, organization_id)
    ON DELETE RESTRICT NOT VALID;

ALTER TABLE enterprise_leave_balances
    ADD CONSTRAINT enterprise_leave_balances_employee_org_fk
    FOREIGN KEY (employee_uuid, organization_uuid)
    REFERENCES employees (id, organization_id)
    ON DELETE RESTRICT NOT VALID;

ALTER TABLE enterprise_contracts
    ADD CONSTRAINT enterprise_contracts_employee_org_pair_ck
    CHECK ((employee_uuid IS NULL) = (organization_uuid IS NULL)) NOT VALID;
ALTER TABLE enterprise_kpi_evaluations
    ADD CONSTRAINT enterprise_kpi_evaluations_employee_org_pair_ck
    CHECK ((employee_uuid IS NULL) = (organization_uuid IS NULL)) NOT VALID;
ALTER TABLE enterprise_leave_balances
    ADD CONSTRAINT enterprise_leave_balances_employee_org_pair_ck
    CHECK ((employee_uuid IS NULL) = (organization_uuid IS NULL)) NOT VALID;

CREATE INDEX IF NOT EXISTS enterprise_contracts_employee_org_idx
    ON enterprise_contracts (organization_uuid, employee_uuid);
CREATE INDEX IF NOT EXISTS enterprise_kpi_evaluations_employee_org_idx
    ON enterprise_kpi_evaluations (organization_uuid, employee_uuid);
CREATE INDEX IF NOT EXISTS enterprise_leave_balances_employee_org_idx
    ON enterprise_leave_balances (organization_uuid, employee_uuid);

ALTER TABLE enterprise_contracts
    VALIDATE CONSTRAINT enterprise_contracts_employee_org_fk;
ALTER TABLE enterprise_kpi_evaluations
    VALIDATE CONSTRAINT enterprise_kpi_evaluations_employee_org_fk;
ALTER TABLE enterprise_leave_balances
    VALIDATE CONSTRAINT enterprise_leave_balances_employee_org_fk;
ALTER TABLE enterprise_contracts
    VALIDATE CONSTRAINT enterprise_contracts_employee_org_pair_ck;
ALTER TABLE enterprise_kpi_evaluations
    VALIDATE CONSTRAINT enterprise_kpi_evaluations_employee_org_pair_ck;
ALTER TABLE enterprise_leave_balances
    VALIDATE CONSTRAINT enterprise_leave_balances_employee_org_pair_ck;

COMMENT ON COLUMN enterprise_contracts.organization_uuid IS
    'Canonical organization UUID derived from employee_uuid; legacy organization codes remain unchanged.';
COMMENT ON COLUMN enterprise_kpi_evaluations.organization_uuid IS
    'Canonical organization UUID derived from employee_uuid; legacy organization codes remain unchanged.';
COMMENT ON COLUMN enterprise_leave_balances.organization_uuid IS
    'Canonical organization UUID derived from employee_uuid; legacy organization codes remain unchanged.';

SET search_path = public;
