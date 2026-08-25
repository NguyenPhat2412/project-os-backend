SET search_path = public;

-- Empty owner table for organization branches. No demo or fallback rows are inserted.
CREATE TABLE IF NOT EXISTS organization_branches (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    code varchar(80) NOT NULL,
    name varchar(200) NOT NULL,
    branch_type varchar(100) NOT NULL DEFAULT 'BRANCH',
    address text NOT NULL,
    phone varchar(50),
    email varchar(254),
    manager_name varchar(200),
    employees_count integer NOT NULL DEFAULT 0 CHECK (employees_count >= 0),
    gps_latitude numeric(10, 6),
    gps_longitude numeric(10, 6),
    gps_radius_meters integer NOT NULL DEFAULT 250 CHECK (gps_radius_meters > 0),
    status varchar(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT organization_branches_code_uq UNIQUE (organization_id, code)
);

CREATE INDEX IF NOT EXISTS idx_organization_branches_org_status
    ON organization_branches (organization_id, status);
