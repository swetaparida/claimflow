-- =====================================================================================
-- Transactional outbox: events written in the business transaction, relayed to Kafka.
-- =====================================================================================

CREATE TABLE outbox_event
(
    id             UUID PRIMARY KEY,
    seq            BIGINT GENERATED ALWAYS AS IDENTITY,
    aggregate_type VARCHAR(64)  NOT NULL,
    aggregate_id   UUID         NOT NULL,
    event_type     VARCHAR(128) NOT NULL,
    topic          VARCHAR(255) NOT NULL,
    payload        JSONB        NOT NULL,
    status         VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    attempts       INTEGER      NOT NULL DEFAULT 0,
    last_error     VARCHAR(2000),
    created_at     TIMESTAMPTZ  NOT NULL,
    published_at   TIMESTAMPTZ,
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

-- Relay scans only pending rows, in insertion order.
CREATE INDEX ix_outbox_pending ON outbox_event (seq) WHERE status = 'PENDING';
CREATE INDEX ix_outbox_aggregate ON outbox_event (aggregate_id, seq);
CREATE INDEX ix_outbox_published_at ON outbox_event (published_at) WHERE status = 'PUBLISHED';
