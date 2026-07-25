UPDATE users u
SET onboarding_completed_at = agg.earliest_event_created_at
FROM (
    SELECT user_id, MIN(created_at) AS earliest_event_created_at
    FROM events
    GROUP BY user_id
) AS agg
WHERE u.id = agg.user_id
  AND u.onboarding_completed_at IS NULL;
