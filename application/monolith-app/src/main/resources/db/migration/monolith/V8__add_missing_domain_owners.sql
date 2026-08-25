SET search_path = public;

-- These tables are empty domain owners. No seed or demo rows are inserted.
CREATE TABLE IF NOT EXISTS organization_positions (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    department_id uuid,
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

CREATE TABLE IF NOT EXISTS training_courses (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    course_code varchar(80) NOT NULL,
    name varchar(200) NOT NULL,
    category varchar(100),
    instructor varchar(200),
    start_date timestamptz,
    end_date timestamptz,
    location varchar(200),
    sessions_count integer,
    attendees_count integer,
    cost numeric(19,2),
    status varchar(20) NOT NULL DEFAULT 'PLANNED' CHECK (status IN ('PLANNED', 'IN_PROGRESS', 'COMPLETED')),
    notes text,
    created_by uuid,
    updated_by uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT training_courses_code_uq UNIQUE (organization_id, course_code),
    CONSTRAINT training_courses_dates_ck CHECK (end_date IS NULL OR start_date IS NULL OR end_date >= start_date),
    CONSTRAINT training_courses_nonnegative_ck CHECK (sessions_count IS NULL OR sessions_count >= 0)
);

CREATE TABLE IF NOT EXISTS company_regulations (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    code varchar(80) NOT NULL,
    title varchar(250) NOT NULL,
    category varchar(100),
    description text,
    penalties text,
    effective_date date,
    status varchar(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('ACTIVE', 'DRAFT', 'INACTIVE')),
    created_by uuid,
    updated_by uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT company_regulations_code_uq UNIQUE (organization_id, code)
);

CREATE TABLE IF NOT EXISTS company_email_accounts (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    email_address varchar(254) NOT NULL,
    display_name varchar(200) NOT NULL,
    assigned_employee_id uuid,
    department_id uuid,
    mailbox_type varchar(20) NOT NULL DEFAULT 'PERSONAL' CHECK (mailbox_type IN ('PERSONAL', 'SHARED')),
    storage_quota_mb integer NOT NULL DEFAULT 0 CHECK (storage_quota_mb >= 0),
    status varchar(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED', 'ARCHIVED')),
    aliases text[] NOT NULL DEFAULT '{}',
    forward_to varchar(254),
    notes text,
    created_by uuid,
    updated_by uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT company_email_accounts_address_uq UNIQUE (organization_id, email_address)
);

CREATE TABLE IF NOT EXISTS report_definitions (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    name varchar(200) NOT NULL,
    category varchar(100),
    period varchar(80),
    start_date date,
    end_date date,
    department_id uuid,
    employee_filter text,
    notes text,
    created_by uuid,
    updated_by uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT report_definitions_dates_ck CHECK (end_date IS NULL OR start_date IS NULL OR end_date >= start_date)
);

CREATE INDEX IF NOT EXISTS idx_organization_positions_org_status ON organization_positions (organization_id, status);
CREATE INDEX IF NOT EXISTS idx_training_courses_org_status ON training_courses (organization_id, status);
CREATE INDEX IF NOT EXISTS idx_company_regulations_org_status ON company_regulations (organization_id, status);
CREATE INDEX IF NOT EXISTS idx_company_email_accounts_org_status ON company_email_accounts (organization_id, status);
CREATE INDEX IF NOT EXISTS idx_report_definitions_org_category ON report_definitions (organization_id, category);

ALTER TABLE organization_positions
    ADD CONSTRAINT organization_positions_department_same_organization_fk
    FOREIGN KEY (department_id, organization_id)
    REFERENCES departments(id, organization_id)
    ON DELETE SET NULL (department_id);

ALTER TABLE company_email_accounts
    ADD CONSTRAINT company_email_accounts_employee_same_organization_fk
    FOREIGN KEY (assigned_employee_id, organization_id)
    REFERENCES employees(id, organization_id)
    ON DELETE SET NULL (assigned_employee_id);

ALTER TABLE company_email_accounts
    ADD CONSTRAINT company_email_accounts_department_same_organization_fk
    FOREIGN KEY (department_id, organization_id)
    REFERENCES departments(id, organization_id)
    ON DELETE SET NULL (department_id);

ALTER TABLE report_definitions
    ADD CONSTRAINT report_definitions_department_same_organization_fk
    FOREIGN KEY (department_id, organization_id)
    REFERENCES departments(id, organization_id)
    ON DELETE SET NULL (department_id);
