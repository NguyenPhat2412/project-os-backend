SET search_path = public;

-- Preserve legacy text/business-code columns. New UUID references are filled
-- only from verified UUID values or explicit rows in the mapping table.
ALTER TABLE enterprise_contracts
    ADD COLUMN IF NOT EXISTS employee_uuid uuid;
ALTER TABLE enterprise_kpi_evaluations
    ADD COLUMN IF NOT EXISTS employee_uuid uuid;
ALTER TABLE enterprise_teams
    ADD COLUMN IF NOT EXISTS organization_uuid uuid,
    ADD COLUMN IF NOT EXISTS department_uuid uuid;

UPDATE enterprise_contracts
SET employee_uuid = CASE
    WHEN employee_id ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
        THEN employee_id::uuid
    ELSE employee_uuid
END
WHERE employee_uuid IS NULL AND employee_id IS NOT NULL;

UPDATE enterprise_kpi_evaluations
SET employee_uuid = CASE
    WHEN employee_id ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
        THEN employee_id::uuid
    ELSE employee_uuid
END
WHERE employee_uuid IS NULL AND employee_id IS NOT NULL;

UPDATE enterprise_teams
SET organization_uuid = CASE
    WHEN organization_id ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
        THEN organization_id::uuid
    ELSE organization_uuid
END,
    department_uuid = CASE
    WHEN department_id ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
        THEN department_id::uuid
    ELSE department_uuid
END
WHERE organization_uuid IS NULL OR department_uuid IS NULL;

-- Explicit mappings are the only permitted way to resolve non-UUID legacy
-- values. Unresolved values remain in the original text columns and are
-- reported by the preflight script instead of being guessed.
UPDATE enterprise_contracts e
SET employee_uuid = m.target_id
FROM enterprise_identifier_mappings m
WHERE e.employee_uuid IS NULL
  AND e.employee_id IS NOT NULL
  AND m.source_table = 'enterprise_contracts'
  AND m.source_column = 'employee_id'
  AND m.source_value = e.employee_id
  AND m.target_entity = 'employees';

UPDATE enterprise_kpi_evaluations e
SET employee_uuid = m.target_id
FROM enterprise_identifier_mappings m
WHERE e.employee_uuid IS NULL
  AND e.employee_id IS NOT NULL
  AND m.source_table = 'enterprise_kpi_evaluations'
  AND m.source_column = 'employee_id'
  AND m.source_value = e.employee_id
  AND m.target_entity = 'employees';

UPDATE enterprise_teams t
SET organization_uuid = m.target_id
FROM enterprise_identifier_mappings m
WHERE t.organization_uuid IS NULL
  AND t.organization_id IS NOT NULL
  AND m.source_table = 'enterprise_teams'
  AND m.source_column = 'organization_id'
  AND m.source_value = t.organization_id
  AND m.target_entity = 'organizations';

UPDATE enterprise_teams t
SET department_uuid = m.target_id
FROM enterprise_identifier_mappings m
WHERE t.department_uuid IS NULL
  AND t.department_id IS NOT NULL
  AND m.source_table = 'enterprise_teams'
  AND m.source_column = 'department_id'
  AND m.source_value = t.department_id
  AND m.target_entity = 'departments';

ALTER TABLE enterprise_contracts
    ADD CONSTRAINT enterprise_contracts_employee_uuid_fk
    FOREIGN KEY (employee_uuid) REFERENCES employees(id)
    ON DELETE RESTRICT NOT VALID;
ALTER TABLE enterprise_kpi_evaluations
    ADD CONSTRAINT enterprise_kpi_evaluations_employee_uuid_fk
    FOREIGN KEY (employee_uuid) REFERENCES employees(id)
    ON DELETE RESTRICT NOT VALID;
ALTER TABLE enterprise_teams
    ADD CONSTRAINT enterprise_teams_organization_uuid_fk
    FOREIGN KEY (organization_uuid) REFERENCES organizations(id)
    ON DELETE RESTRICT NOT VALID,
    ADD CONSTRAINT enterprise_teams_department_uuid_same_organization_fk
    FOREIGN KEY (department_uuid, organization_uuid)
    REFERENCES departments(id, organization_id)
    ON DELETE RESTRICT NOT VALID;

CREATE INDEX IF NOT EXISTS enterprise_contracts_employee_uuid_idx
    ON enterprise_contracts (employee_uuid);
CREATE INDEX IF NOT EXISTS enterprise_kpi_evaluations_employee_uuid_idx
    ON enterprise_kpi_evaluations (employee_uuid);
CREATE INDEX IF NOT EXISTS enterprise_teams_organization_uuid_idx
    ON enterprise_teams (organization_uuid);
CREATE INDEX IF NOT EXISTS enterprise_teams_department_uuid_idx
    ON enterprise_teams (department_uuid);

ALTER TABLE enterprise_contracts VALIDATE CONSTRAINT enterprise_contracts_employee_uuid_fk;
ALTER TABLE enterprise_kpi_evaluations VALIDATE CONSTRAINT enterprise_kpi_evaluations_employee_uuid_fk;
ALTER TABLE enterprise_teams VALIDATE CONSTRAINT enterprise_teams_organization_uuid_fk;
ALTER TABLE enterprise_teams VALIDATE CONSTRAINT enterprise_teams_department_uuid_same_organization_fk;
