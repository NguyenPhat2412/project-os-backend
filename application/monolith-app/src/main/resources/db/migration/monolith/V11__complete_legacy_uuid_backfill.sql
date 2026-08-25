SET search_path = public;

-- Some legacy UUID values are valid PostgreSQL UUIDs but use synthetic
-- version/variant nibbles. Accept UUID syntax, not RFC metadata, when the
-- value has already been verified against the target table.
UPDATE enterprise_contracts
SET employee_uuid = employee_id::uuid
WHERE employee_uuid IS NULL
  AND employee_id ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$';

UPDATE enterprise_kpi_evaluations
SET employee_uuid = employee_id::uuid
WHERE employee_uuid IS NULL
  AND employee_id ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$';

UPDATE enterprise_teams
SET organization_uuid = organization_id::uuid
WHERE organization_uuid IS NULL
  AND organization_id ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$';

UPDATE enterprise_teams
SET department_uuid = department_id::uuid
WHERE department_uuid IS NULL
  AND department_id ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$';

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
