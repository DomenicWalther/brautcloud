ALTER TABLE users
    ADD COLUMN IF NOT EXISTS onboarding_completed_at TIMESTAMP;

UPDATE users AS u
SET onboarding_completed_at = (
    SELECT MIN(e.created_at)
    FROM events AS e
    WHERE e.user_id = u.id
)
WHERE u.onboarding_completed_at IS NULL
  AND EXISTS (
      SELECT 1
      FROM events AS e
      WHERE e.user_id = u.id
  );
