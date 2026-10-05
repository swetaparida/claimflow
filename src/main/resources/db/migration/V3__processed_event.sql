-- =====================================================================================
-- Idempotent consumer support: one row per (event, consumer) successfully processed.
-- =====================================================================================

CREATE TABLE processed_event
(
    event_id     UUID         NOT NULL,
    consumer     VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (event_id, consumer)
);
