SET search_path = public;

-- Composite foreign keys must be indexed in the same leading-column order as
-- the constraint. V27's tenant-first lookup indexes remain useful for scoped
-- reads; these child-first indexes complete FK enforcement support.
CREATE INDEX IF NOT EXISTS enterprise_contracts_employee_org_fk_idx
    ON enterprise_contracts (employee_uuid, organization_uuid);
CREATE INDEX IF NOT EXISTS enterprise_kpi_evaluations_employee_org_fk_idx
    ON enterprise_kpi_evaluations (employee_uuid, organization_uuid);
CREATE INDEX IF NOT EXISTS enterprise_leave_balances_employee_org_fk_idx
    ON enterprise_leave_balances (employee_uuid, organization_uuid);
CREATE INDEX IF NOT EXISTS enterprise_teams_department_org_fk_idx
    ON enterprise_teams (department_uuid, organization_uuid);

SET search_path = public;
