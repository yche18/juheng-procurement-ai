CREATE TABLE approval_decision
(
    id                 UUID         PRIMARY KEY,
    approval_task_id   UUID         NOT NULL REFERENCES approval_task (id) ON DELETE RESTRICT,
    decision           VARCHAR(20)  NOT NULL CHECK (decision IN ('APPROVED', 'REJECTED')),
    actor_id           VARCHAR(100) NOT NULL CHECK (btrim(actor_id) <> ''),
    decided_at         TIMESTAMPTZ  NOT NULL,
    comment            TEXT,
    CONSTRAINT uk_approval_decision_task UNIQUE (approval_task_id),
    CONSTRAINT ck_approval_decision_comment CHECK (
        (comment IS NULL OR (btrim(comment) <> '' AND char_length(comment) <= 2000))
        AND (decision = 'APPROVED' OR comment IS NOT NULL)
    )
);
