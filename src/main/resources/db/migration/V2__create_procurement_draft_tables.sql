CREATE SEQUENCE procurement_request_business_number_seq
    START WITH 1
    INCREMENT BY 1;

CREATE TABLE procurement_request
(
    id                     UUID           PRIMARY KEY,
    business_number        VARCHAR(32)    NOT NULL UNIQUE,
    creator_id             VARCHAR(100)   NOT NULL CHECK (btrim(creator_id) <> ''),
    title                  VARCHAR(200)   NOT NULL CHECK (btrim(title) <> ''),
    purpose                TEXT           NOT NULL CHECK (btrim(purpose) <> '' AND char_length(purpose) <= 2000),
    department             VARCHAR(100)   NOT NULL CHECK (btrim(department) <> ''),
    expected_delivery_date DATE           NOT NULL,
    currency               VARCHAR(3)     NOT NULL CHECK (currency = 'CNY'),
    estimated_total        NUMERIC(19, 2) NOT NULL CHECK (estimated_total >= 0),
    status                 VARCHAR(20)    NOT NULL CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED')),
    version                BIGINT         NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at             TIMESTAMPTZ    NOT NULL,
    updated_at             TIMESTAMPTZ    NOT NULL
);

CREATE TABLE procurement_item
(
    id                       UUID           PRIMARY KEY,
    procurement_request_id   UUID           NOT NULL REFERENCES procurement_request (id) ON DELETE RESTRICT,
    line_no                  INTEGER        NOT NULL CHECK (line_no > 0),
    name                     VARCHAR(200)   NOT NULL CHECK (btrim(name) <> ''),
    category_code            VARCHAR(40)    NOT NULL CHECK (
        category_code IN ('LAPTOP', 'MONITOR', 'OFFICE_CHAIR', 'SOFTWARE_LICENSE')
    ),
    specification            TEXT           NOT NULL CHECK (
        btrim(specification) <> '' AND char_length(specification) <= 2000
    ),
    quantity                 NUMERIC(19, 4) NOT NULL CHECK (quantity > 0),
    unit                     VARCHAR(40)    NOT NULL CHECK (btrim(unit) <> ''),
    estimated_unit_price     NUMERIC(19, 2) NOT NULL CHECK (estimated_unit_price >= 0),
    CONSTRAINT uk_procurement_item_request_line UNIQUE (procurement_request_id, line_no)
);

CREATE TABLE audit_event
(
    id                       UUID         PRIMARY KEY,
    procurement_request_id   UUID         NOT NULL REFERENCES procurement_request (id) ON DELETE RESTRICT,
    actor_id                 VARCHAR(100) NOT NULL CHECK (btrim(actor_id) <> ''),
    action                   VARCHAR(80)  NOT NULL CHECK (btrim(action) <> ''),
    target_type              VARCHAR(50)  NOT NULL CHECK (btrim(target_type) <> ''),
    target_id                UUID         NOT NULL,
    occurred_at              TIMESTAMPTZ  NOT NULL,
    result                   VARCHAR(30)  NOT NULL CHECK (btrim(result) <> ''),
    request_identifier       VARCHAR(64)  NOT NULL CHECK (btrim(request_identifier) <> '')
);

CREATE INDEX idx_audit_event_request_time
    ON audit_event (procurement_request_id, occurred_at, id);
