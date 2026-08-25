SET search_path = public;

-- Legacy enterprise columns are text and contain business identifiers. Keep the
-- original values intact and require an explicit mapping before UUID FKs are added.
CREATE TABLE IF NOT EXISTS enterprise_identifier_mappings (
    id uuid PRIMARY KEY,
    source_table text NOT NULL,
    source_column text NOT NULL,
    source_value text NOT NULL,
    target_entity text NOT NULL,
    target_id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (source_table, source_column, source_value),
    UNIQUE (source_table, source_column, target_id)
);

CREATE INDEX IF NOT EXISTS idx_enterprise_identifier_mappings_target
    ON enterprise_identifier_mappings (target_entity, target_id);
