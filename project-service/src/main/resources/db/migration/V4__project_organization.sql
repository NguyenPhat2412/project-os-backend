alter table projects add column organization_id uuid;
create index projects_organization_updated_idx on projects(organization_id, updated_at desc) where organization_id is not null;
