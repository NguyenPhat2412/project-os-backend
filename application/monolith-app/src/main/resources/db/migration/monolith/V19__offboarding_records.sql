CREATE TABLE IF NOT EXISTS public.offboarding_records (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL REFERENCES public.organizations(id) ON DELETE CASCADE,
    employee_id uuid NOT NULL REFERENCES public.employees(id) ON DELETE RESTRICT,
    code varchar(80) NOT NULL,
    employee_code varchar(50) NOT NULL,
    employee_name varchar(255) NOT NULL,
    department varchar(255),
    position varchar(255),
    contract_code varchar(100),
    hire_date date,
    resignation_date date NOT NULL,
    last_working_date date NOT NULL,
    reason_type varchar(50) NOT NULL CHECK (reason_type IN (
        'PERSONAL_REASON', 'HEALTH_FAMILY', 'RELOCATION',
        'CONTRACT_EXPIRATION', 'MUTUAL_AGREEMENT', 'RETIREMENT', 'OTHER'
    )),
    reason_detail text NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN (
        'PENDING', 'HANDOVER', 'SETTLEMENT', 'COMPLETED', 'REJECTED'
    )),
    handover_receiver_id uuid REFERENCES public.employees(id) ON DELETE SET NULL,
    handover_receiver_name varchar(255),
    checklist jsonb NOT NULL DEFAULT '{"taskHandover":false,"assetsHandover":false,"financeSettlement":false,"accountRevocation":false}'::jsonb,
    unpaid_salary_amount numeric(19,2) NOT NULL DEFAULT 0 CHECK (unpaid_salary_amount >= 0),
    unused_leave_days numeric(8,2) NOT NULL DEFAULT 0 CHECK (unused_leave_days >= 0),
    unused_leave_compensation numeric(19,2) NOT NULL DEFAULT 0 CHECK (unused_leave_compensation >= 0),
    severance_pay numeric(19,2) NOT NULL DEFAULT 0 CHECK (severance_pay >= 0),
    total_settlement_amount numeric(19,2) NOT NULL DEFAULT 0 CHECK (total_settlement_amount >= 0),
    decision_number varchar(100),
    decision_date date,
    assets_notes text,
    notes text,
    created_by uuid,
    updated_by uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT offboarding_records_code_uq UNIQUE (organization_id, code),
    CONSTRAINT offboarding_records_dates_ck CHECK (last_working_date >= resignation_date)
);

CREATE INDEX IF NOT EXISTS offboarding_records_organization_status_idx
    ON public.offboarding_records (organization_id, status);

CREATE INDEX IF NOT EXISTS offboarding_records_organization_last_working_idx
    ON public.offboarding_records (organization_id, last_working_date);

CREATE INDEX IF NOT EXISTS offboarding_records_employee_idx
    ON public.offboarding_records (employee_id);
