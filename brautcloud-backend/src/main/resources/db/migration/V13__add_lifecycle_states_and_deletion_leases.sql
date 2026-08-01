ALTER TABLE events ADD COLUMN IF NOT EXISTS lifecycle_state VARCHAR(32);
UPDATE events
SET lifecycle_state = CASE WHEN deletion_requested THEN 'DELETE_REQUESTED' ELSE 'ACTIVE' END
WHERE lifecycle_state IS NULL;
ALTER TABLE events ALTER COLUMN lifecycle_state SET DEFAULT 'ACTIVE';
ALTER TABLE events ALTER COLUMN lifecycle_state SET NOT NULL;

ALTER TABLE images ADD COLUMN IF NOT EXISTS lifecycle_state VARCHAR(32);
UPDATE images
SET lifecycle_state = CASE
    WHEN deletion_requested THEN 'DELETE_REQUESTED'
    WHEN is_uploaded THEN 'AVAILABLE'
    ELSE 'PENDING'
END
WHERE lifecycle_state IS NULL;
ALTER TABLE images ALTER COLUMN lifecycle_state SET DEFAULT 'PENDING';
ALTER TABLE images ALTER COLUMN lifecycle_state SET NOT NULL;

ALTER TABLE storage_deletion_jobs ADD COLUMN IF NOT EXISTS lease_token VARCHAR(64);
ALTER TABLE storage_deletion_jobs ADD COLUMN IF NOT EXISTS lease_until TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_storage_deletion_jobs_claimable
    ON storage_deletion_jobs (next_attempt_at, lease_until);
CREATE INDEX IF NOT EXISTS idx_storage_deletion_jobs_lease_token
    ON storage_deletion_jobs (lease_token);
