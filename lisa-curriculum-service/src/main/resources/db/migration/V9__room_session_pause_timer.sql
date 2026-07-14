ALTER TABLE room_learning_sessions
    ADD COLUMN IF NOT EXISTS paused_seconds_remaining BIGINT;
