ALTER TABLE users
    ADD COLUMN IF NOT EXISTS deleted_at timestamptz,
    ADD COLUMN IF NOT EXISTS delete_expires_at timestamptz;

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_status_check;
ALTER TABLE users
    ADD CONSTRAINT users_status_check
    CHECK (status IN ('ACTIVE', 'DISABLED', 'DELETED'));

CREATE INDEX IF NOT EXISTS users_deleted_expiry_idx
    ON users (delete_expires_at)
    WHERE status = 'DELETED';
