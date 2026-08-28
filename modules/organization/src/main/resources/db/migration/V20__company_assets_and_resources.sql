-- Organization-owned assets, resources and employee handover history.
create table if not exists company_assets (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organizations(id) on delete cascade,
    code varchar(80) not null,
    name varchar(200) not null,
    category varchar(50) not null,
    serial_number varchar(150),
    model varchar(150),
    manufacturer varchar(150),
    purchase_date date,
    purchase_price numeric(19,2),
    currency varchar(3) not null default 'VND',
    warranty_until date,
    location varchar(200),
    status varchar(20) not null default 'AVAILABLE' check (status in ('AVAILABLE','IN_USE','MAINTENANCE','RETIRED')),
    supplier varchar(200),
    notes text,
    is_deleted boolean not null default false,
    created_by uuid,
    updated_by uuid,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint company_assets_org_code_unique unique (organization_id, code)
);

create table if not exists company_shared_resources (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organizations(id) on delete cascade,
    code varchar(80) not null,
    name varchar(200) not null,
    category varchar(50) not null,
    quantity integer not null default 1 check (quantity > 0),
    unit varchar(40) not null default 'item',
    location varchar(200),
    owner_department_id uuid references departments(id) on delete set null,
    bookable boolean not null default false,
    status varchar(20) not null default 'ACTIVE' check (status in ('ACTIVE','INACTIVE')),
    notes text,
    created_by uuid,
    updated_by uuid,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint company_shared_resources_org_code_unique unique (organization_id, code)
);

create table if not exists asset_handover_orders (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organizations(id) on delete cascade,
    employee_id uuid not null references employees(id) on delete restrict,
    status varchar(20) not null default 'PENDING' check (status in ('PENDING','CONFIRMED','RETURNED','CANCELLED')),
    purpose varchar(500) not null,
    notes text,
    created_by uuid not null,
    confirmed_at timestamptz,
    returned_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists asset_handover_items (
    id uuid primary key default gen_random_uuid(),
    handover_id uuid not null references asset_handover_orders(id) on delete cascade,
    asset_id uuid not null references company_assets(id) on delete restrict,
    condition_out varchar(500),
    condition_in varchar(500),
    issued_at timestamptz,
    returned_at timestamptz,
    note text,
    constraint asset_handover_items_unique_asset unique (handover_id, asset_id)
);

create table if not exists asset_assignments (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organizations(id) on delete cascade,
    asset_id uuid not null references company_assets(id) on delete restrict,
    employee_id uuid not null references employees(id) on delete restrict,
    handover_id uuid not null references asset_handover_orders(id) on delete restrict,
    status varchar(20) not null default 'ACTIVE' check (status in ('ACTIVE','RETURNED')),
    assigned_at timestamptz not null default now(),
    returned_at timestamptz,
    assigned_by uuid not null,
    returned_by uuid,
    notes text
);

create unique index if not exists asset_assignments_one_active_asset
    on asset_assignments(asset_id) where status = 'ACTIVE';
create index if not exists company_assets_org_status_idx on company_assets(organization_id, status) where is_deleted = false;
create index if not exists company_shared_resources_org_category_idx on company_shared_resources(organization_id, category);
create index if not exists asset_handover_orders_org_status_idx on asset_handover_orders(organization_id, status);
create index if not exists asset_handover_orders_employee_idx on asset_handover_orders(organization_id, employee_id);
create index if not exists asset_handover_items_asset_idx on asset_handover_items(asset_id);
create index if not exists asset_assignments_employee_idx on asset_assignments(organization_id, employee_id, status);
