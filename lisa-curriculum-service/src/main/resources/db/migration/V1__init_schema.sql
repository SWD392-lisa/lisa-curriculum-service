-- ================================================================
-- baseline schema for curriculum
-- ================================================================

CREATE TABLE IF NOT EXISTS levels (
    id               BIGSERIAL PRIMARY KEY,
    language         VARCHAR(20)  NOT NULL CHECK (language IN ('ENGLISH','CHINESE','JAPANESE')),
    stage            INT          NOT NULL CHECK (stage BETWEEN 1 AND 3),
    level_number     INT          NOT NULL CHECK (level_number BETWEEN 1 AND 100),
    title            VARCHAR(255) NOT NULL,
    cefr_target      VARCHAR(20),
    duration_minutes INT          NOT NULL DEFAULT 60,
    group_label      TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_language_level UNIQUE (language, level_number)
);

CREATE INDEX IF NOT EXISTS idx_levels_language ON levels (language);
CREATE INDEX IF NOT EXISTS idx_levels_language_stage ON levels (language, stage);

CREATE TABLE IF NOT EXISTS sub_levels (
    id               BIGSERIAL PRIMARY KEY,
    level_id         BIGINT       NOT NULL REFERENCES levels(id) ON DELETE CASCADE,
    sub_number       INT          NOT NULL CHECK (sub_number BETWEEN 1 AND 10),
    topic            VARCHAR(500) NOT NULL,
    duration_minutes INT          NOT NULL DEFAULT 10,
    CONSTRAINT uq_level_sub UNIQUE (level_id, sub_number)
);

CREATE INDEX IF NOT EXISTS idx_sub_levels_level_id ON sub_levels (level_id);

CREATE TABLE IF NOT EXISTS speaking_tasks (
    id            BIGSERIAL PRIMARY KEY,
    sub_level_id  BIGINT       NOT NULL REFERENCES sub_levels(id) ON DELETE CASCADE,
    task_type     VARCHAR(20)  NOT NULL CHECK (task_type IN ('QUESTION','ANSWER','BULLET')),
    content       TEXT         NOT NULL,
    pronunciation TEXT,
    order_index   INT          NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_tasks_sub_level_id ON speaking_tasks (sub_level_id);

CREATE OR REPLACE VIEW curriculum_stats AS
SELECT
    language,
    COUNT(DISTINCT id)                                   AS total_levels,
    SUM((SELECT COUNT(*) FROM sub_levels sl WHERE sl.level_id = l.id)) AS total_sub_levels,
    SUM((SELECT COUNT(*) FROM speaking_tasks st
         JOIN sub_levels sl ON st.sub_level_id = sl.id
         WHERE sl.level_id = l.id))                      AS total_tasks
FROM levels l
GROUP BY language
ORDER BY language;
