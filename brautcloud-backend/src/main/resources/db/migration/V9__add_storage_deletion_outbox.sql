ALTER TABLE events ADD COLUMN IF NOT EXISTS deletion_requested BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE images ADD COLUMN IF NOT EXISTS deletion_requested BOOLEAN NOT NULL DEFAULT FALSE;

-- Jobs intentionally do not have foreign keys to resources. A job must survive a
-- partial database failure and can finish after its resource row is gone.
CREATE TABLE IF NOT EXISTS storage_deletion_jobs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    resource_type VARCHAR(16) NOT NULL,
    resource_id UUID NOT NULL,
    event_id UUID NOT NULL,
    object_key TEXT,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_storage_deletion_job_resource UNIQUE (resource_type, resource_id)
);

CREATE INDEX IF NOT EXISTS idx_storage_deletion_jobs_due
    ON storage_deletion_jobs (next_attempt_at);
CREATE INDEX IF NOT EXISTS idx_storage_deletion_jobs_event
    ON storage_deletion_jobs (event_id, resource_type);
