CREATE TABLE invoice (
    id              UUID        PRIMARY KEY,
    subscription_id UUID        NOT NULL,
    customer_id     VARCHAR(64) NOT NULL,
    plan            VARCHAR(32) NOT NULL,
    status          VARCHAR(16) NOT NULL,
    issued_at       TIMESTAMPTZ NOT NULL
);

-- One invoice per subscription: makes the consumer idempotent under at-least-once delivery.
CREATE UNIQUE INDEX uq_invoice_subscription ON invoice (subscription_id);
