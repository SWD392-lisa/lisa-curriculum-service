-- ================================================================
-- Learner progress and room attendance logs schema
-- ================================================================

CREATE TABLE IF NOT EXISTS learner_progress (
    id                   BIGSERIAL PRIMARY KEY,
    learner_user_id      VARCHAR(255) NOT NULL,
    level_id             BIGINT NOT NULL REFERENCES levels(id) ON DELETE CASCADE,
    sub_level_id         BIGINT NOT NULL REFERENCES sub_levels(id) ON DELETE CASCADE,
    completed            BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at         TIMESTAMPTZ,
    speaking_seconds     INT NOT NULL DEFAULT 0,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_learner_sublevel UNIQUE (learner_user_id, sub_level_id)
);

CREATE INDEX IF NOT EXISTS idx_learner_progress_composite ON learner_progress (learner_user_id, level_id, sub_level_id);

CREATE TABLE IF NOT EXISTS room_attendances (
    id                   BIGSERIAL PRIMARY KEY,
    room_session_id      UUID NOT NULL REFERENCES room_learning_sessions(id) ON DELETE CASCADE,
    learner_user_id      VARCHAR(255) NOT NULL,
    joined_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    left_at              TIMESTAMPTZ,
    total_seconds        BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_room_attendances_composite ON room_attendances (room_session_id, learner_user_id);
