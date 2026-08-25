ALTER TABLE public.users
    ADD COLUMN IF NOT EXISTS deleted_at timestamptz,
    ADD COLUMN IF NOT EXISTS delete_expires_at timestamptz;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.users'::regclass
          AND conname = 'users_status_check'
    ) THEN
        ALTER TABLE public.users
            ADD CONSTRAINT users_status_check
            CHECK (status IN ('ACTIVE', 'DISABLED', 'DELETED'));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS users_deleted_expiry_idx
    ON public.users (delete_expires_at)
    WHERE status = 'DELETED';

CREATE INDEX IF NOT EXISTS users_status_created_idx
    ON public.users (status, created_at DESC);
