ALTER TABLE attendance_records
    ADD COLUMN IF NOT EXISTS check_in_latitude double precision,
    ADD COLUMN IF NOT EXISTS check_in_longitude double precision,
    ADD COLUMN IF NOT EXISTS check_in_accuracy_meters double precision,
    ADD COLUMN IF NOT EXISTS check_in_distance_meters double precision,
    ADD COLUMN IF NOT EXISTS check_out_latitude double precision,
    ADD COLUMN IF NOT EXISTS check_out_longitude double precision,
    ADD COLUMN IF NOT EXISTS check_out_accuracy_meters double precision,
    ADD COLUMN IF NOT EXISTS check_out_distance_meters double precision,
    ADD COLUMN IF NOT EXISTS work_mode varchar(32) NOT NULL DEFAULT 'OFFICE',
    ADD COLUMN IF NOT EXISTS qr_verified boolean NOT NULL DEFAULT false;
