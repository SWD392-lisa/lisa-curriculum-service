-- Normalize sub-level duration_minutes before adding CHECK constraint
UPDATE sub_levels
SET duration_minutes = CASE
    WHEN duration_minutes < 10 THEN 10
    WHEN duration_minutes > 20 THEN 20
    ELSE duration_minutes
END
WHERE duration_minutes IS DISTINCT FROM CASE
    WHEN duration_minutes < 10 THEN 10
    WHEN duration_minutes > 20 THEN 20
    ELSE duration_minutes
END;

ALTER TABLE sub_levels
    DROP CONSTRAINT IF EXISTS ck_sub_levels_duration_minutes;

ALTER TABLE sub_levels
    ADD CONSTRAINT ck_sub_levels_duration_minutes
    CHECK (duration_minutes BETWEEN 10 AND 20);

ALTER TABLE room_learning_sessions
    DROP CONSTRAINT IF EXISTS ck_room_learning_sessions_status;

ALTER TABLE room_learning_sessions
    ADD CONSTRAINT ck_room_learning_sessions_status
    CHECK (status IN ('WAITING', 'LIVE', 'PAUSED', 'ENDED'));

ALTER TABLE sub_levels
    DROP CONSTRAINT IF EXISTS uq_sub_levels_id_level_id;

ALTER TABLE sub_levels
    ADD CONSTRAINT uq_sub_levels_id_level_id UNIQUE (id, level_id);

UPDATE room_learning_sessions rls
SET current_sub_level_id = (
    SELECT sl.id
    FROM sub_levels sl
    WHERE sl.level_id = rls.level_id
    ORDER BY sl.sub_number ASC, sl.id ASC
    LIMIT 1
)
WHERE NOT EXISTS (
    SELECT 1
    FROM sub_levels sl
    WHERE sl.id = rls.current_sub_level_id
      AND sl.level_id = rls.level_id
)
AND EXISTS (
    SELECT 1
    FROM sub_levels sl
    WHERE sl.level_id = rls.level_id
);

ALTER TABLE room_learning_sessions
    DROP CONSTRAINT IF EXISTS fk_room_sessions_level_sublevel;

ALTER TABLE room_learning_sessions
    ADD CONSTRAINT fk_room_sessions_level_sublevel
    FOREIGN KEY (current_sub_level_id, level_id)
    REFERENCES sub_levels(id, level_id);

CREATE TABLE IF NOT EXISTS room_session_sub_level_history (
    id BIGSERIAL PRIMARY KEY,
    room_session_id UUID NOT NULL REFERENCES room_learning_sessions(id) ON DELETE CASCADE,
    from_sub_level_id BIGINT,
    to_sub_level_id BIGINT NOT NULL,
    changed_by_user_id VARCHAR(255),
    change_source VARCHAR(20) NOT NULL CHECK (change_source IN ('MANUAL', 'AUTO', 'INIT')),
    note VARCHAR(1000),
    changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sub_level_history_session_changed_at
    ON room_session_sub_level_history(room_session_id, changed_at, id);
