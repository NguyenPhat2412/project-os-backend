SET search_path = public;

-- Organization permissions are used by OrganizationPermissionService and must
-- exist before Hibernate validation. IF NOT EXISTS keeps this safe for a fresh
-- V1 schema and for an existing database baselined at version 1.
CREATE TABLE IF NOT EXISTS organization_permissions (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL,
    permission_key varchar(255) NOT NULL,
    role_key varchar(255) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT organization_permissions_business_key
        UNIQUE (organization_id, permission_key, role_key)
);

CREATE INDEX IF NOT EXISTS idx_organization_permissions_organization
    ON organization_permissions (organization_id);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'organization_permissions_organization_fk'
          AND conrelid = 'public.organization_permissions'::regclass
    ) THEN
        ALTER TABLE organization_permissions
            ADD CONSTRAINT organization_permissions_organization_fk
            FOREIGN KEY (organization_id) REFERENCES organizations(id)
            ON DELETE CASCADE;
    END IF;
END $$;
