ALTER TABLE users
    ADD COLUMN IF NOT EXISTS onboarding_completed_at TIMESTAMP;

UPDATE users
SET onboarding_completed_at = (
    SELECT MIN(e.created_at)
    FROM events e
    WHERE e.user_id = users.id
)
WHERE users.onboarding_completed_at IS NULL
  AND EXISTS (SELECT 1 FROM events e WHERE e.user_id = users.id);
