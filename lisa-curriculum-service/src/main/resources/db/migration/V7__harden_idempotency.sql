-- ================================================================
-- Hardening idempotency constraints
-- ================================================================

-- 1. Alter learner_progress table constraints and add last_idempotency_key
ALTER TABLE learner_progress DROP CONSTRAINT IF EXISTS uq_learner_sublevel;

-- Check and add the updated unique constraint
ALTER TABLE learner_progress ADD CONSTRAINT uq_learner_level_sublevel UNIQUE (learner_user_id, level_id, sub_level_id);

-- Add last_idempotency_key column
ALTER TABLE learner_progress ADD COLUMN IF NOT EXISTS last_idempotency_key VARCHAR(255);

-- 2. Prevent multiple active attendance rows for the same learner in the same session
CREATE UNIQUE INDEX uq_active_attendance ON room_attendances (room_session_id, learner_user_id) WHERE left_at IS NULL;
