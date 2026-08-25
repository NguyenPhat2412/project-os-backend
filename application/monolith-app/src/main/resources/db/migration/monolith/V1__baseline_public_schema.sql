-- Canonical public-schema baseline captured from the verified monolith database.
-- Existing non-empty databases are baselined at version 1; fresh databases execute this DDL.
SET search_path = public;

--
-- PostgreSQL database dump
--


-- Dumped from database version 17.11
-- Dumped by pg_dump version 17.11

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: public; Type: SCHEMA; Schema: -; Owner: -
--

--
-- Name: activity_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.activity_events (
    id uuid NOT NULL,
    action character varying(255) NOT NULL,
    actor_id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    event_id uuid NOT NULL,
    occurred_at timestamp(6) with time zone NOT NULL,
    organization_id uuid NOT NULL,
    project_id uuid NOT NULL,
    resource character varying(255) NOT NULL,
    subject character varying(255) NOT NULL
);


--
-- Name: attendance_adjustments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.attendance_adjustments (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    decision_note character varying(255),
    employee_id uuid NOT NULL,
    organization_id uuid NOT NULL,
    reason character varying(255) NOT NULL,
    requested_check_in_at timestamp(6) with time zone,
    requested_check_out_at timestamp(6) with time zone,
    reviewed_at timestamp(6) with time zone,
    reviewer_id uuid,
    status character varying(255) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    work_date date NOT NULL,
    CONSTRAINT attendance_adjustments_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[])))
);


--
-- Name: attendance_records; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.attendance_records (
    id uuid NOT NULL,
    break_minutes integer DEFAULT 0,
    check_in_at timestamp(6) with time zone,
    check_out_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone DEFAULT now() NOT NULL,
    employee_id uuid NOT NULL,
    organization_id uuid NOT NULL,
    scheduled_end_at timestamp(6) with time zone,
    scheduled_start_at timestamp(6) with time zone,
    shift_id uuid,
    shift_name character varying(255),
    status character varying(255) NOT NULL,
    updated_at timestamp(6) with time zone DEFAULT now() NOT NULL,
    work_date date DEFAULT CURRENT_DATE,
    employee_code character varying(50),
    employee_name character varying(255),
    department character varying(255),
    date character varying(50),
    check_in_time character varying(50),
    check_out_time character varying(50),
    work_shift character varying(255),
    late_minutes integer DEFAULT 0,
    early_minutes integer DEFAULT 0,
    total_work_hours numeric(6,2) DEFAULT 8.0,
    overtime_hours numeric(6,2) DEFAULT 0,
    check_in_method character varying(50),
    gps_location_summary text,
    details jsonb DEFAULT '{}'::jsonb
);


--
-- Name: company_policies; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.company_policies (
    organization_id uuid NOT NULL,
    afternoon_end time(0) without time zone NOT NULL,
    afternoon_start time(0) without time zone NOT NULL,
    morning_end time(0) without time zone NOT NULL,
    morning_start time(0) without time zone NOT NULL,
    rules text NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    updated_by uuid NOT NULL
);


--
-- Name: departments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.departments (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone DEFAULT now() NOT NULL,
    name character varying(255) NOT NULL,
    organization_id uuid NOT NULL,
    parent_id uuid,
    updated_at timestamp(6) with time zone DEFAULT now() NOT NULL,
    code character varying(50),
    manager_name character varying(255),
    manager_title character varying(255),
    employee_count integer DEFAULT 0,
    phone character varying(50),
    email character varying(255),
    location text,
    budget_monthly numeric(15,2) DEFAULT 0,
    status character varying(50) DEFAULT 'ACTIVE'::character varying
);


--
-- Name: employee_compensations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.employee_compensations (
    employee_id uuid NOT NULL,
    monthly_amount numeric(19,2) NOT NULL,
    organization_id uuid NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    updated_by uuid NOT NULL
);


--
-- Name: employees; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.employees (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone DEFAULT now() NOT NULL,
    department_id uuid,
    email character varying(255) NOT NULL,
    full_name character varying(255) NOT NULL,
    organization_id uuid NOT NULL,
    status character varying(255) NOT NULL,
    supervisor_id uuid,
    title character varying(255),
    updated_at timestamp(6) with time zone DEFAULT now() NOT NULL,
    user_id uuid,
    code character varying(50),
    name character varying(255),
    phone character varying(50),
    citizen_id character varying(50),
    birth_date character varying(50),
    gender character varying(20),
    current_address text,
    permanent_address text,
    department character varying(255),
    "position" character varying(255),
    base_salary numeric(15,2),
    allowances numeric(15,2),
    contract_type character varying(100),
    join_date character varying(50),
    official_date character varying(50),
    bank_account_number character varying(50),
    bank_name character varying(100),
    bank_branch character varying(100),
    avatar_url text,
    is_deleted boolean DEFAULT false,
    deleted_at timestamp with time zone,
    details jsonb DEFAULT '{}'::jsonb,
    age integer DEFAULT 30,
    age_group character varying(50),
    birth_place character varying(100),
    marital_status character varying(50),
    ethnicity character varying(50) DEFAULT 'Kinh'::character varying,
    religion character varying(50) DEFAULT 'Không'::character varying,
    seniority_years integer DEFAULT 1,
    education character varying(255)
);


--
-- Name: enterprise_activity_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.enterprise_activity_logs (
    id character varying(100) NOT NULL,
    actor_name character varying(255) NOT NULL,
    actor_code character varying(50),
    action character varying(255) NOT NULL,
    target character varying(255) NOT NULL,
    details text,
    "timestamp" character varying(100),
    category character varying(50),
    status character varying(50) DEFAULT 'success'::character varying,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: enterprise_announcements; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.enterprise_announcements (
    id character varying(100) NOT NULL,
    title character varying(255) NOT NULL,
    category character varying(50) DEFAULT 'URGENT'::character varying NOT NULL,
    category_label character varying(100),
    content text NOT NULL,
    publish_date character varying(50),
    author character varying(255),
    is_pinned boolean DEFAULT false,
    comments_count integer DEFAULT 0,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: enterprise_company_profile; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.enterprise_company_profile (
    id character varying(100) DEFAULT 'main_company_profile'::character varying NOT NULL,
    company_name character varying(255) NOT NULL,
    international_name character varying(255),
    short_name character varying(100),
    tax_code character varying(50),
    established_date character varying(50),
    headquarters text,
    legal_representative character varying(255),
    representative_title character varying(255),
    phone character varying(50),
    email character varying(255),
    website character varying(255),
    bank_account character varying(50),
    bank_name character varying(100),
    business_license character varying(50),
    registered_capital character varying(100),
    total_employees integer DEFAULT 395,
    branches jsonb DEFAULT '[]'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: enterprise_contracts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.enterprise_contracts (
    id character varying(100) NOT NULL,
    employee_id character varying(100),
    employee_code character varying(50) NOT NULL,
    employee_name character varying(255) NOT NULL,
    department character varying(255),
    "position" character varying(255),
    contract_code character varying(100) NOT NULL,
    contract_type character varying(100) NOT NULL,
    sign_date character varying(50),
    effective_date character varying(50),
    expire_date character varying(50),
    base_salary numeric(15,2) DEFAULT 0,
    allowances numeric(15,2) DEFAULT 0,
    performance_bonus numeric(15,2) DEFAULT 0,
    status character varying(50) DEFAULT 'ACTIVE'::character varying,
    warning_days_remaining integer,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: enterprise_discussions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.enterprise_discussions (
    id character varying(100) NOT NULL,
    author_name character varying(255) NOT NULL,
    author_role character varying(255),
    author_avatar text,
    content text NOT NULL,
    "timestamp" character varying(100),
    likes integer DEFAULT 0,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: enterprise_kpi_evaluations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.enterprise_kpi_evaluations (
    id character varying(100) NOT NULL,
    employee_id character varying(100),
    employee_code character varying(50) NOT NULL,
    employee_name character varying(255) NOT NULL,
    department character varying(255),
    period character varying(100) NOT NULL,
    target_title character varying(255) NOT NULL,
    weight_percent numeric(5,2) DEFAULT 100.0,
    target_metric character varying(255),
    actual_metric character varying(255),
    score_percent numeric(5,2) DEFAULT 100.0,
    ranking character varying(50) DEFAULT 'EXCELLENT'::character varying,
    ranking_label character varying(100) DEFAULT 'Xuất sắc'::character varying,
    evaluator_name character varying(255),
    evaluation_date character varying(50),
    notes text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: enterprise_leave_balances; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.enterprise_leave_balances (
    id character varying(100) NOT NULL,
    employee_code character varying(50) NOT NULL,
    employee_name character varying(255) NOT NULL,
    department character varying(255),
    standard_quota numeric(4,1) DEFAULT 12.0,
    seniority_bonus numeric(4,1) DEFAULT 0.0,
    carried_over numeric(4,1) DEFAULT 0.0,
    total_entitled numeric(4,1) DEFAULT 12.0,
    used_days numeric(4,1) DEFAULT 0.0,
    remaining_days numeric(4,1) DEFAULT 12.0,
    year integer DEFAULT 2026,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: enterprise_master_catalogs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.enterprise_master_catalogs (
    id character varying(100) NOT NULL,
    category character varying(100) NOT NULL,
    code character varying(100) NOT NULL,
    name character varying(255) NOT NULL,
    display_order integer DEFAULT 1,
    is_active boolean DEFAULT true
);


--
-- Name: enterprise_teams; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.enterprise_teams (
    id character varying(100) NOT NULL,
    organization_id character varying(100),
    department_id character varying(100),
    department_name character varying(255),
    code character varying(50) NOT NULL,
    name character varying(255) NOT NULL,
    slug character varying(255) NOT NULL,
    leader_id character varying(100),
    leader_name character varying(255),
    members_count integer DEFAULT 0,
    description text,
    status character varying(50) DEFAULT 'ACTIVE'::character varying,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: enterprise_user_profiles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.enterprise_user_profiles (
    uid character varying(255) NOT NULL,
    email character varying(255) NOT NULL,
    display_name character varying(255) NOT NULL,
    photo_url text,
    phone character varying(50),
    department character varying(255),
    title character varying(255),
    address text,
    timezone character varying(50) DEFAULT 'Asia/Ho_Chi_Minh'::character varying,
    bio text,
    skills jsonb DEFAULT '[]'::jsonb,
    notification_prefs jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: leave_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.leave_requests (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone DEFAULT now() NOT NULL,
    decision_note character varying(255),
    employee_id uuid,
    end_date date,
    organization_id uuid NOT NULL,
    reason character varying(255),
    reviewed_at timestamp(6) with time zone,
    reviewer_id uuid,
    start_date date,
    status character varying(255) NOT NULL,
    updated_at timestamp(6) with time zone DEFAULT now() NOT NULL,
    legacy_id character varying(100),
    employee_code character varying(50),
    employee_name character varying(255),
    department character varying(255),
    leave_type character varying(50) DEFAULT 'ANNUAL'::character varying,
    leave_type_label character varying(100),
    total_days numeric(4,1) DEFAULT 1.0,
    approver_name character varying(255),
    CONSTRAINT leave_requests_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: oauth_identities; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.oauth_identities (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    provider character varying(255) NOT NULL,
    provider_subject character varying(255) NOT NULL,
    user_id uuid NOT NULL
);


--
-- Name: organization_audit_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.organization_audit_logs (
    id uuid NOT NULL,
    actor_id uuid NOT NULL,
    after_state jsonb,
    before_state jsonb,
    created_at timestamp(6) with time zone NOT NULL,
    entity_id uuid,
    entity_type character varying(255) NOT NULL,
    event_type character varying(255) NOT NULL,
    organization_id uuid NOT NULL,
    reason character varying(255)
);


--
-- Name: organization_memberships; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.organization_memberships (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    organization_id uuid NOT NULL,
    role character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    user_id uuid NOT NULL,
    CONSTRAINT organization_memberships_role_check CHECK (((role)::text = ANY ((ARRAY['OWNER'::character varying, 'ADMIN'::character varying, 'HR'::character varying, 'DEPARTMENT_MANAGER'::character varying, 'EMPLOYEE'::character varying, 'MEMBER'::character varying])::text[]))),
    CONSTRAINT organization_memberships_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'DISABLED'::character varying])::text[])))
);


--
-- Name: organizations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.organizations (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    name character varying(255) NOT NULL,
    slug character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    timezone character varying(255) NOT NULL,
    updated_at timestamp(6) with time zone DEFAULT now() NOT NULL,
    code character varying(50),
    name_vi text,
    name_en text,
    short_name character varying(100),
    tax_code character varying(50),
    legal_representative character varying(255),
    representative_title character varying(255),
    headquarters_address text,
    hotline character varying(50),
    email character varying(255),
    website character varying(255),
    details jsonb DEFAULT '{}'::jsonb,
    CONSTRAINT organizations_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'DISABLED'::character varying])::text[])))
);


--
-- Name: outbox_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.outbox_events (
    id uuid NOT NULL,
    attempts integer NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    delivered_at timestamp(6) with time zone,
    event_type character varying(120) NOT NULL,
    last_error character varying(500),
    next_attempt_at timestamp(6) with time zone NOT NULL,
    payload jsonb NOT NULL
);


--
-- Name: permission_group_members; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.permission_group_members (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    group_id uuid NOT NULL,
    organization_id uuid NOT NULL,
    user_id uuid NOT NULL
);


--
-- Name: permission_group_modules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.permission_group_modules (
    group_id uuid NOT NULL,
    module_key character varying(255) NOT NULL
);


--
-- Name: permission_groups; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.permission_groups (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    description character varying(255),
    name character varying(255) NOT NULL,
    organization_id uuid NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL
);


--
-- Name: projects; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.projects (
    id uuid NOT NULL,
    color character varying(255) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    current_sprint character varying(255),
    description text,
    end_date date,
    icon character varying(255) NOT NULL,
    legacy_id character varying(255),
    name character varying(255) NOT NULL,
    organization_id uuid,
    owner_id uuid NOT NULL,
    quarter character varying(255),
    start_date date,
    status character varying(255) NOT NULL,
    team_size integer,
    tech_stack jsonb NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT projects_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'COMPLETED'::character varying, 'ARCHIVED'::character varying])::text[])))
);


--
-- Name: refresh_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.refresh_tokens (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL,
    family_id uuid NOT NULL,
    revoked_at timestamp(6) with time zone,
    token_hash character varying(255) NOT NULL,
    user_id uuid NOT NULL
);


--
-- Name: resource_records; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.resource_records (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    created_by uuid NOT NULL,
    legacy_id character varying(200),
    payload jsonb NOT NULL,
    project_id uuid NOT NULL,
    resource_type character varying(80) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL
);


--
-- Name: schedule_assignments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.schedule_assignments (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    effective_from date NOT NULL,
    effective_to date,
    employee_id uuid NOT NULL,
    organization_id uuid NOT NULL,
    schedule_id uuid NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL
);


--
-- Name: schedule_slots; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.schedule_slots (
    id uuid NOT NULL,
    day_of_week smallint NOT NULL,
    schedule_id uuid NOT NULL,
    shift_id uuid NOT NULL
);


--
-- Name: shifts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.shifts (
    id uuid NOT NULL,
    active boolean NOT NULL,
    break_minutes integer NOT NULL,
    created_at timestamp(6) with time zone DEFAULT now() NOT NULL,
    end_time time(0) without time zone NOT NULL,
    name character varying(255) NOT NULL,
    organization_id uuid NOT NULL,
    start_time time(0) without time zone NOT NULL,
    updated_at timestamp(6) with time zone DEFAULT now() NOT NULL,
    code character varying(50),
    break_start_time character varying(50),
    break_end_time character varying(50),
    standard_working_hours numeric(4,1) DEFAULT 8.0,
    flexible_start_minutes integer DEFAULT 15,
    early_leave_grace_minutes integer DEFAULT 5,
    allowed_radius_meters integer DEFAULT 250,
    shift_allowance numeric(15,2) DEFAULT 0,
    ot_rate numeric(4,2) DEFAULT 1.5,
    department_scope character varying(255) DEFAULT 'Tất cả phòng ban'::character varying,
    notes text,
    status character varying(50) DEFAULT 'ACTIVE'::character varying
);


--
-- Name: user_profiles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_profiles (
    user_id uuid NOT NULL,
    address character varying(255),
    appearance_preferences jsonb NOT NULL,
    bio character varying(255),
    created_at timestamp(6) with time zone NOT NULL,
    department character varying(255),
    notification_preferences jsonb NOT NULL,
    notification_settings jsonb NOT NULL,
    phone character varying(255),
    skills jsonb NOT NULL,
    timezone character varying(255),
    title character varying(255),
    updated_at timestamp(6) with time zone NOT NULL
);


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    id uuid NOT NULL,
    avatar_url character varying(255),
    created_at timestamp(6) with time zone DEFAULT now() NOT NULL,
    display_name character varying(255) NOT NULL,
    email character varying(255) NOT NULL,
    password_hash character varying(255),
    role character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    updated_at timestamp(6) with time zone DEFAULT now() NOT NULL
);


--
-- Name: work_schedules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.work_schedules (
    id uuid NOT NULL,
    active boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    name character varying(255) NOT NULL,
    organization_id uuid NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL
);


--
-- Name: activity_events activity_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.activity_events
    ADD CONSTRAINT activity_events_pkey PRIMARY KEY (id);


--
-- Name: attendance_adjustments attendance_adjustments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attendance_adjustments
    ADD CONSTRAINT attendance_adjustments_pkey PRIMARY KEY (id);


--
-- Name: attendance_records attendance_records_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attendance_records
    ADD CONSTRAINT attendance_records_pkey PRIMARY KEY (id);


--
-- Name: company_policies company_policies_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.company_policies
    ADD CONSTRAINT company_policies_pkey PRIMARY KEY (organization_id);


--
-- Name: departments departments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.departments
    ADD CONSTRAINT departments_pkey PRIMARY KEY (id);


--
-- Name: employee_compensations employee_compensations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employee_compensations
    ADD CONSTRAINT employee_compensations_pkey PRIMARY KEY (employee_id);


--
-- Name: employees employees_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employees
    ADD CONSTRAINT employees_pkey PRIMARY KEY (id);


--
-- Name: enterprise_activity_logs enterprise_activity_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.enterprise_activity_logs
    ADD CONSTRAINT enterprise_activity_logs_pkey PRIMARY KEY (id);


--
-- Name: enterprise_announcements enterprise_announcements_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.enterprise_announcements
    ADD CONSTRAINT enterprise_announcements_pkey PRIMARY KEY (id);


--
-- Name: enterprise_company_profile enterprise_company_profile_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.enterprise_company_profile
    ADD CONSTRAINT enterprise_company_profile_pkey PRIMARY KEY (id);


--
-- Name: enterprise_contracts enterprise_contracts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.enterprise_contracts
    ADD CONSTRAINT enterprise_contracts_pkey PRIMARY KEY (id);


--
-- Name: enterprise_discussions enterprise_discussions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.enterprise_discussions
    ADD CONSTRAINT enterprise_discussions_pkey PRIMARY KEY (id);


--
-- Name: enterprise_kpi_evaluations enterprise_kpi_evaluations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.enterprise_kpi_evaluations
    ADD CONSTRAINT enterprise_kpi_evaluations_pkey PRIMARY KEY (id);


--
-- Name: enterprise_leave_balances enterprise_leave_balances_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.enterprise_leave_balances
    ADD CONSTRAINT enterprise_leave_balances_pkey PRIMARY KEY (id);


--
-- Name: enterprise_master_catalogs enterprise_master_catalogs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.enterprise_master_catalogs
    ADD CONSTRAINT enterprise_master_catalogs_pkey PRIMARY KEY (id);


--
-- Name: enterprise_teams enterprise_teams_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.enterprise_teams
    ADD CONSTRAINT enterprise_teams_pkey PRIMARY KEY (id);


--
-- Name: enterprise_user_profiles enterprise_user_profiles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.enterprise_user_profiles
    ADD CONSTRAINT enterprise_user_profiles_pkey PRIMARY KEY (uid);


--
-- Name: leave_requests leave_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.leave_requests
    ADD CONSTRAINT leave_requests_pkey PRIMARY KEY (id);


--
-- Name: oauth_identities oauth_identities_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.oauth_identities
    ADD CONSTRAINT oauth_identities_pkey PRIMARY KEY (id);


--
-- Name: organization_audit_logs organization_audit_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organization_audit_logs
    ADD CONSTRAINT organization_audit_logs_pkey PRIMARY KEY (id);


--
-- Name: organization_memberships organization_memberships_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organization_memberships
    ADD CONSTRAINT organization_memberships_pkey PRIMARY KEY (id);


--
-- Name: organizations organizations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organizations
    ADD CONSTRAINT organizations_pkey PRIMARY KEY (id);


--
-- Name: outbox_events outbox_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.outbox_events
    ADD CONSTRAINT outbox_events_pkey PRIMARY KEY (id);


--
-- Name: permission_group_members permission_group_members_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permission_group_members
    ADD CONSTRAINT permission_group_members_pkey PRIMARY KEY (id);


--
-- Name: permission_group_modules permission_group_modules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permission_group_modules
    ADD CONSTRAINT permission_group_modules_pkey PRIMARY KEY (group_id, module_key);


--
-- Name: permission_groups permission_groups_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permission_groups
    ADD CONSTRAINT permission_groups_pkey PRIMARY KEY (id);


--
-- Name: projects projects_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projects
    ADD CONSTRAINT projects_pkey PRIMARY KEY (id);


--
-- Name: refresh_tokens refresh_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_pkey PRIMARY KEY (id);


--
-- Name: resource_records resource_records_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.resource_records
    ADD CONSTRAINT resource_records_pkey PRIMARY KEY (id);


--
-- Name: schedule_assignments schedule_assignments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.schedule_assignments
    ADD CONSTRAINT schedule_assignments_pkey PRIMARY KEY (id);


--
-- Name: schedule_slots schedule_slots_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.schedule_slots
    ADD CONSTRAINT schedule_slots_pkey PRIMARY KEY (id);


--
-- Name: shifts shifts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shifts
    ADD CONSTRAINT shifts_pkey PRIMARY KEY (id);


--
-- Name: users uk6dotkott2kjsp8vw4d0m25fb7; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT uk6dotkott2kjsp8vw4d0m25fb7 UNIQUE (email);


--
-- Name: activity_events ukglpmdxwtnet1f2q69bp4ycy7d; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.activity_events
    ADD CONSTRAINT ukglpmdxwtnet1f2q69bp4ycy7d UNIQUE (event_id);


--
-- Name: refresh_tokens uko2mlirhldriil2y7krapq4frt; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT uko2mlirhldriil2y7krapq4frt UNIQUE (token_hash);


--
-- Name: organizations uksfr9257mbjkowos3ci3e22ay2; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organizations
    ADD CONSTRAINT uksfr9257mbjkowos3ci3e22ay2 UNIQUE (slug);


--
-- Name: projects uktdy6mmn5b5ojse7ysnwh6jid9; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projects
    ADD CONSTRAINT uktdy6mmn5b5ojse7ysnwh6jid9 UNIQUE (legacy_id);


--
-- Name: user_profiles user_profiles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_profiles
    ADD CONSTRAINT user_profiles_pkey PRIMARY KEY (user_id);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: work_schedules work_schedules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_schedules
    ADD CONSTRAINT work_schedules_pkey PRIMARY KEY (id);


--
-- Name: refresh_tokens fk1lih5y2npsf8u5o3vhdb9y0os; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT fk1lih5y2npsf8u5o3vhdb9y0os FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: oauth_identities fkcwhpmr8ej1s107ds2ex7afd65; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.oauth_identities
    ADD CONSTRAINT fkcwhpmr8ej1s107ds2ex7afd65 FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: permission_group_modules fkq90yp67fk9k04g39tscgcgq9p; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permission_group_modules
    ADD CONSTRAINT fkq90yp67fk9k04g39tscgcgq9p FOREIGN KEY (group_id) REFERENCES public.permission_groups(id);


--
--
-- Name: organization_permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.organization_permissions (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    permission_key character varying(255) NOT NULL,
    role_key character varying(255) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL
);

ALTER TABLE ONLY public.organization_permissions
    ADD CONSTRAINT organization_permissions_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.organization_permissions
    ADD CONSTRAINT organization_permissions_organization_fk
    FOREIGN KEY (organization_id) REFERENCES public.organizations(id);

ALTER TABLE ONLY public.organization_permissions
    ADD CONSTRAINT organization_permissions_business_key
    UNIQUE (organization_id, permission_key, role_key);

-- PostgreSQL database dump complete
--
