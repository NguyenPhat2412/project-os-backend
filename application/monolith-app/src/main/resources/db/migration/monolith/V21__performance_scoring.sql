CREATE TABLE IF NOT EXISTS public.performance_scoring_rules (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL REFERENCES public.organizations(id) ON DELETE CASCADE,
    rule_code varchar(80) NOT NULL,
    name varchar(200) NOT NULL,
    description text,
    category varchar(80) NOT NULL,
    points integer NOT NULL,
    is_active boolean NOT NULL DEFAULT true,
    created_by uuid,
    updated_by uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT performance_scoring_rules_code_uq UNIQUE (organization_id, rule_code),
    CONSTRAINT performance_scoring_rules_id_org_uq UNIQUE (id, organization_id),
    CONSTRAINT performance_scoring_rules_points_ck CHECK (points BETWEEN -1000000 AND 1000000)
);

CREATE TABLE IF NOT EXISTS public.employee_score_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL REFERENCES public.organizations(id) ON DELETE CASCADE,
    employee_id uuid NOT NULL,
    rule_id uuid REFERENCES public.performance_scoring_rules(id) ON DELETE SET NULL,
    source varchar(80) NOT NULL,
    event_key varchar(180),
    points integer NOT NULL,
    reason varchar(1000) NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT employee_score_events_employee_org_fk
        FOREIGN KEY (employee_id, organization_id)
        REFERENCES public.employees (id, organization_id)
        ON DELETE RESTRICT,
    CONSTRAINT employee_score_events_rule_org_fk
        FOREIGN KEY (rule_id, organization_id)
        REFERENCES public.performance_scoring_rules (id, organization_id)
        ON DELETE RESTRICT,
    CONSTRAINT employee_score_events_points_ck CHECK (points BETWEEN -1000000 AND 1000000)
);

CREATE INDEX IF NOT EXISTS performance_scoring_rules_active_idx
    ON public.performance_scoring_rules (organization_id, is_active, rule_code);

CREATE INDEX IF NOT EXISTS employee_score_events_organization_occurred_idx
    ON public.employee_score_events (organization_id, occurred_at DESC);

CREATE INDEX IF NOT EXISTS employee_score_events_employee_occurred_idx
    ON public.employee_score_events (organization_id, employee_id, occurred_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS employee_score_events_organization_event_key_uq
    ON public.employee_score_events (organization_id, event_key)
    WHERE event_key IS NOT NULL;
