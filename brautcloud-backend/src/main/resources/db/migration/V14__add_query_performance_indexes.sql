CREATE INDEX IF NOT EXISTS idx_events_user_created_id_active
    ON events (user_id, created_at, id)
    WHERE deletion_requested = FALSE;

CREATE INDEX IF NOT EXISTS idx_images_event_created_id_uploaded
    ON images (event_id, created_at, id)
    WHERE is_uploaded = TRUE AND deletion_requested = FALSE;

CREATE INDEX IF NOT EXISTS idx_images_pending_cleanup
    ON images (created_at, id)
    WHERE is_uploaded = FALSE AND deletion_requested = FALSE;

CREATE INDEX IF NOT EXISTS idx_images_event_guest_session
    ON images (event_id, guest_session_hash);
