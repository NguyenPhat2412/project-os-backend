-- The directory uses case-insensitive substring search on name and email.
-- Trigram indexes keep the project member picker responsive as the user base grows.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS users_active_directory_display_name_trgm_idx
    ON identity.users USING gin (lower(display_name) gin_trgm_ops)
    WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS users_active_directory_email_trgm_idx
    ON identity.users USING gin (lower(email) gin_trgm_ops)
    WHERE status = 'ACTIVE';
