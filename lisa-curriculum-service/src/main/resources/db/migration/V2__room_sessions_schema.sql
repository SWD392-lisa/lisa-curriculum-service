-- ================================================================
-- room sessions & pinned materials tables
-- ================================================================

CREATE TABLE room_learning_sessions (
    id                     UUID PRIMARY KEY,
    channel_name           VARCHAR(255) NOT NULL,
    mentor_user_id         VARCHAR(255) NOT NULL,
    level_id               BIGINT NOT NULL REFERENCES levels(id),
    current_sub_level_id   BIGINT NOT NULL REFERENCES sub_levels(id),
    status                 VARCHAR(50) NOT NULL,
    auto_switch_enabled    BOOLEAN NOT NULL DEFAULT TRUE,
    sub_level_started_at   TIMESTAMPTZ,
    started_at             TIMESTAMPTZ,
    ended_at               TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version                BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_room_sessions_switch ON room_learning_sessions(status, auto_switch_enabled, sub_level_started_at);
CREATE INDEX idx_room_sessions_mentor ON room_learning_sessions(mentor_user_id);

CREATE TABLE pinned_materials (
    id                     BIGSERIAL PRIMARY KEY,
    room_session_id        UUID NOT NULL REFERENCES room_learning_sessions(id) ON DELETE CASCADE,
    title                  VARCHAR(255) NOT NULL,
    material_type          VARCHAR(50) NOT NULL,
    url                    VARCHAR(1024) NOT NULL,
    display_order          INT DEFAULT 0,
    active                 BOOLEAN NOT NULL DEFAULT TRUE,
    pinned_by_user_id      VARCHAR(255) NOT NULL,
    pinned_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pinned_materials_session ON pinned_materials(room_session_id, active);
