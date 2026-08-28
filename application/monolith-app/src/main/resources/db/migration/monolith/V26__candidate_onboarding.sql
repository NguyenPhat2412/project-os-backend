create table public.onboarding_invitations (
    id uuid primary key,
    organization_id uuid not null references public.organizations(id),
    token_hash varchar(64) not null unique,
    invite_email varchar(255),
    department_id uuid,
    position_id uuid,
    target_role varchar(40) not null default 'ROLE_EMPLOYEE',
    status varchar(20) not null default 'ACTIVE',
    expires_at timestamptz not null,
    used_at timestamptz,
    revoked_at timestamptz,
    created_by uuid not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint onboarding_invitation_role_ck check (target_role in ('ROLE_EMPLOYEE', 'ROLE_DEPT_LEAD')),
    constraint onboarding_invitation_status_ck check (status in ('ACTIVE', 'USED', 'REVOKED', 'EXPIRED')),
    constraint onboarding_invitation_expiry_ck check (expires_at > created_at)
);

create table public.onboarding_requests (
    id uuid primary key,
    organization_id uuid not null references public.organizations(id),
    invitation_id uuid not null unique references public.onboarding_invitations(id),
    reference_code varchar(40) not null unique,
    status varchar(20) not null default 'PENDING',
    full_name varchar(255) not null,
    birth_date date not null,
    gender varchar(20),
    citizen_id varchar(50) not null,
    id_issue_date date,
    id_issue_place varchar(255),
    birth_place varchar(255),
    marital_status varchar(50),
    ethnicity varchar(80),
    religion varchar(80),
    phone varchar(50) not null,
    email varchar(255) not null,
    permanent_address text,
    current_address text,
    bank_name varchar(160),
    bank_account_number varchar(80),
    bank_account_holder varchar(255),
    tax_code varchar(50),
    social_insurance_number varchar(80),
    emergency_contact_name varchar(255),
    emergency_contact_relationship varchar(100),
    emergency_contact_phone varchar(50),
    education_level varchar(160),
    major_field varchar(160),
    personal_notes text,
    consent_at timestamptz not null,
    submitted_at timestamptz not null default now(),
    reviewed_at timestamptz,
    reviewed_by uuid,
    rejection_reason varchar(1000),
    employee_id uuid references public.employees(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint onboarding_request_status_ck check (status in ('PENDING', 'APPROVED', 'REJECTED')),
    constraint onboarding_request_gender_ck check (gender is null or gender in ('Nam', 'Nữ', 'Khác')),
    constraint onboarding_request_consent_ck check (consent_at <= submitted_at + interval '1 minute')
);

create index onboarding_invitations_org_status_idx on public.onboarding_invitations (organization_id, status, expires_at);
create index onboarding_invitations_department_idx on public.onboarding_invitations (organization_id, department_id);
create index onboarding_requests_org_status_idx on public.onboarding_requests (organization_id, status, submitted_at desc);
create index onboarding_requests_invitation_idx on public.onboarding_requests (invitation_id);
create index onboarding_requests_identity_idx on public.onboarding_requests (organization_id, citizen_id, email);

alter table public.onboarding_invitations
    add constraint onboarding_invitation_department_fk
        foreign key (department_id) references public.departments(id),
    add constraint onboarding_invitation_position_fk
        foreign key (position_id) references public.organization_positions(id);

create or replace function public.expire_onboarding_invitations()
returns void
language sql
as $$
    update public.onboarding_invitations
       set status = 'EXPIRED', updated_at = now()
     where status = 'ACTIVE' and expires_at <= now();
$$;
