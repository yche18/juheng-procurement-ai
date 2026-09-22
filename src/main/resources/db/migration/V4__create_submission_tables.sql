CREATE TABLE approval_task
(
    id                       UUID         PRIMARY KEY,
    procurement_request_id   UUID         NOT NULL REFERENCES procurement_request (id) ON DELETE RESTRICT,
    assignee_id              VARCHAR(100) NOT NULL CHECK (btrim(assignee_id) <> ''),
    status                   VARCHAR(20)  NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    version                  BIGINT       NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at               TIMESTAMPTZ  NOT NULL,
    updated_at               TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_approval_task_request UNIQUE (procurement_request_id)
);

CREATE TABLE idempotency_record
(
    id                       UUID         PRIMARY KEY,
    caller_id                VARCHAR(100) NOT NULL CHECK (btrim(caller_id) <> ''),
    operation                VARCHAR(80)  NOT NULL CHECK (btrim(operation) <> ''),
    target_type              VARCHAR(50)  NOT NULL CHECK (btrim(target_type) <> ''),
    target_id                UUID         NOT NULL,
    idempotency_key          VARCHAR(64)  NOT NULL CHECK (btrim(idempotency_key) <> ''),
    request_fingerprint      CHAR(64)     NOT NULL CHECK (request_fingerprint ~ '^[0-9a-f]{64}$'),
    status                   VARCHAR(20)  NOT NULL CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
    result_reference_type    VARCHAR(50),
    result_reference_id      UUID,
    result_target_version    BIGINT       CHECK (result_target_version >= 0),
    created_at               TIMESTAMPTZ  NOT NULL,
    updated_at               TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_idempotency_scope UNIQUE (
        caller_id, operation, target_type, target_id, idempotency_key
    ),
    CONSTRAINT ck_idempotency_result_state CHECK (
        (status = 'IN_PROGRESS'
            AND result_reference_type IS NULL
            AND result_reference_id IS NULL
            AND result_target_version IS NULL)
        OR
        (status = 'COMPLETED'
            AND btrim(result_reference_type) <> ''
            AND result_reference_id IS NOT NULL
            AND result_target_version IS NOT NULL)
    )
);
