CREATE TABLE IF NOT EXISTS public.email_templates (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES public.organizations(id) ON DELETE CASCADE,
    code varchar(80) NOT NULL,
    title varchar(160) NOT NULL,
    subject varchar(255) NOT NULL,
    body_html text NOT NULL,
    allowed_variables jsonb NOT NULL DEFAULT '[]'::jsonb,
    status varchar(20) NOT NULL DEFAULT 'ACTIVE',
    created_by uuid NOT NULL REFERENCES public.users(id),
    updated_by uuid NOT NULL REFERENCES public.users(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT email_templates_status_ck CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT email_templates_code_uq UNIQUE (organization_id, code)
);

CREATE TABLE IF NOT EXISTS public.email_campaigns (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES public.organizations(id) ON DELETE CASCADE,
    created_by uuid NOT NULL REFERENCES public.users(id),
    subject varchar(255) NOT NULL,
    body_html text NOT NULL,
    template_id uuid REFERENCES public.email_templates(id) ON DELETE SET NULL,
    audience_filter jsonb NOT NULL DEFAULT '{}'::jsonb,
    preview_hash varchar(64) NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'DRAFT',
    total_recipients integer NOT NULL DEFAULT 0,
    sent_count integer NOT NULL DEFAULT 0,
    failed_count integer NOT NULL DEFAULT 0,
    idempotency_key varchar(180) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    queued_at timestamptz,
    completed_at timestamptz,
    CONSTRAINT email_campaigns_status_ck CHECK (status IN ('DRAFT','QUEUED','SENDING','COMPLETED','PARTIAL','FAILED','CANCELLED')),
    CONSTRAINT email_campaigns_idempotency_uq UNIQUE (organization_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS public.email_campaign_recipients (
    id uuid PRIMARY KEY,
    campaign_id uuid NOT NULL REFERENCES public.email_campaigns(id) ON DELETE CASCADE,
    organization_id uuid NOT NULL REFERENCES public.organizations(id) ON DELETE CASCADE,
    employee_id uuid NOT NULL REFERENCES public.employees(id),
    employee_name_snapshot varchar(255) NOT NULL,
    employee_code_snapshot varchar(50),
    email_snapshot varchar(255) NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'PENDING',
    attempts integer NOT NULL DEFAULT 0,
    friendly_error varchar(500),
    sent_at timestamptz,
    CONSTRAINT email_campaign_recipients_status_ck CHECK (status IN ('PENDING','SENDING','SENT','FAILED','SKIPPED')),
    CONSTRAINT email_campaign_recipients_uq UNIQUE (campaign_id, employee_id)
);

CREATE TABLE IF NOT EXISTS public.email_delivery_attempts (
    id uuid PRIMARY KEY,
    recipient_id uuid NOT NULL REFERENCES public.email_campaign_recipients(id) ON DELETE CASCADE,
    organization_id uuid NOT NULL REFERENCES public.organizations(id) ON DELETE CASCADE,
    status varchar(20) NOT NULL,
    provider_message_id varchar(255),
    trace_id varchar(120),
    sanitized_error varchar(500),
    attempted_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT email_delivery_attempts_status_ck CHECK (status IN ('SENT','FAILED','RETRYING'))
);

CREATE INDEX IF NOT EXISTS email_templates_org_status_idx ON public.email_templates (organization_id, status);
CREATE INDEX IF NOT EXISTS email_campaigns_org_status_created_idx ON public.email_campaigns (organization_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS email_campaign_recipients_campaign_status_idx ON public.email_campaign_recipients (campaign_id, status);
CREATE INDEX IF NOT EXISTS email_delivery_attempts_recipient_time_idx ON public.email_delivery_attempts (recipient_id, attempted_at DESC);
