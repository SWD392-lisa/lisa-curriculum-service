-- ================================================================
-- curriculum imports audit and history table
-- ================================================================

CREATE TABLE IF NOT EXISTS curriculum_imports (
    id                   UUID PRIMARY KEY,
    file_name            VARCHAR(255) NOT NULL,
    file_hash            VARCHAR(64) NOT NULL,
    language             VARCHAR(20) NOT NULL CHECK (language IN ('ENGLISH','CHINESE','JAPANESE')),
    stage                INT NOT NULL,
    status               VARCHAR(20) NOT NULL CHECK (status IN ('SUCCESS','FAILED')),
    levels_imported      INT,
    sub_levels_imported  INT,
    tasks_imported       INT,
    error_message        TEXT,
    imported_by_user_id  VARCHAR(255),
    imported_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    duration_ms          BIGINT
);

CREATE INDEX IF NOT EXISTS idx_curriculum_imports_hash ON curriculum_imports (file_hash);
CREATE INDEX IF NOT EXISTS idx_curriculum_imports_status ON curriculum_imports (status);
