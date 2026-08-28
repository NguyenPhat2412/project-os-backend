alter table ai_conversations add column if not exists project_id uuid;
drop index if exists ai_conversations_scope_idx;
create index ai_conversations_scope_idx on ai_conversations(organization_id, owner_user_id, project_id, updated_at desc);
