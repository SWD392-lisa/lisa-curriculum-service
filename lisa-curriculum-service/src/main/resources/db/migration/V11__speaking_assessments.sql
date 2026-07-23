CREATE TABLE IF NOT EXISTS speaking_assessments (
    id                 BIGSERIAL PRIMARY KEY,
    learner_user_id    VARCHAR(255) NOT NULL,
    task_id            BIGINT NOT NULL REFERENCES speaking_tasks(id) ON DELETE CASCADE,
    transcript         TEXT NOT NULL,
    overall_score      INT NOT NULL,
    relevance_score    INT NOT NULL,
    grammar_score      INT NOT NULL,
    vocabulary_score   INT NOT NULL,
    feedback           TEXT NOT NULL,
    suggested_answer   TEXT NOT NULL,
    speaking_seconds   INT NOT NULL,
    assessed_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_speaking_assessment_learner_task UNIQUE (learner_user_id, task_id),
    CONSTRAINT ck_speaking_assessment_scores CHECK (
        overall_score BETWEEN 0 AND 100
        AND relevance_score BETWEEN 0 AND 100
        AND grammar_score BETWEEN 0 AND 100
        AND vocabulary_score BETWEEN 0 AND 100
    )
);

CREATE INDEX IF NOT EXISTS idx_speaking_assessment_learner
    ON speaking_assessments (learner_user_id);
