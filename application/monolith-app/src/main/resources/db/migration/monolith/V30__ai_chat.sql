create table ai_conversations (
    id uuid primary key,
    organization_id uuid not null,
    owner_user_id uuid not null,
    title varchar(200) not null,
    model_id varchar(200) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table ai_messages (
    id uuid primary key,
    conversation_id uuid not null references ai_conversations(id) on delete cascade,
    organization_id uuid not null,
    owner_user_id uuid not null,
    role varchar(20) not null check (role in ('USER', 'ASSISTANT')),
    content text not null,
    provider_model varchar(200),
    prompt_tokens integer,
    completion_tokens integer,
    created_at timestamptz not null
);

create index ai_conversations_scope_idx on ai_conversations(organization_id, owner_user_id, updated_at desc);
create index ai_messages_scope_idx on ai_messages(conversation_id, organization_id, owner_user_id, created_at);
