ALTER TABLE users
    ADD COLUMN IF NOT EXISTS onboarding_completed_at TIMESTAMP;

UPDATE users
SET onboarding_completed_at = sub.min_created_at
FROM (
    SELECT user_id, MIN(created_at) AS min_created_at
    FROM events
    GROUP BY user_id
) AS sub
WHERE users.id = sub.user_id
  AND users.onboarding_completed_at IS NULL;
