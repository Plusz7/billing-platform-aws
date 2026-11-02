CREATE TABLE outbox_event (
    id           UUID        PRIMARY KEY,
    aggregate_id UUID        NOT NULL,
    type         VARCHAR(64) NOT NULL,
    payload      TEXT        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_event_unpublished ON outbox_event (created_at) WHERE published_at IS NULL;
