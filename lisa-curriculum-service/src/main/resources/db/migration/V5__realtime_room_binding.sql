ALTER TABLE room_learning_sessions
    ADD COLUMN IF NOT EXISTS realtime_room_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS realtime_agora_channel_name VARCHAR(255);

CREATE UNIQUE INDEX IF NOT EXISTS idx_room_sessions_realtime_room_id
    ON room_learning_sessions(realtime_room_id)
    WHERE realtime_room_id IS NOT NULL;
