CREATE TABLE IF NOT EXISTS session_recordings (
    recording_id       VARCHAR(255) PRIMARY KEY,
    room_session_id    UUID NOT NULL REFERENCES room_learning_sessions(id) ON DELETE CASCADE,
    playback_url       VARCHAR(2048),
    duration_seconds   INT,
    status             VARCHAR(64) NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at         TIMESTAMPTZ,
    ended_at           TIMESTAMPTZ,
    provider           VARCHAR(128)
);

CREATE INDEX IF NOT EXISTS idx_session_recordings_session_created_at
    ON session_recordings (room_session_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_session_recordings_status
    ON session_recordings (status);
