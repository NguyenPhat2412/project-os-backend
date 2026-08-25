-- Standalone organization-module test schema compatibility. The monolith uses
-- the equivalent public-schema table from V8. No seed or demo rows are added.
CREATE TABLE IF NOT EXISTS organization_positions (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    department_id uuid REFERENCES departments(id) ON DELETE SET NULL,
    code varchar(80) NOT NULL,
    title varchar(200) NOT NULL,
    job_level varchar(80),
    standard_salary numeric(19,2),
    status varchar(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    description text,
    created_by uuid,
    updated_by uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT organization_positions_code_uq UNIQUE (organization_id, code)
);
