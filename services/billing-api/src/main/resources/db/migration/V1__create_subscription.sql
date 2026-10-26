CREATE TABLE subscription (
    id           UUID        PRIMARY KEY,
    customer_id  VARCHAR(64) NOT NULL,
    plan         VARCHAR(32) NOT NULL,
    status       VARCHAR(16) NOT NULL,
    started_at   TIMESTAMPTZ NOT NULL,
    cancelled_at TIMESTAMPTZ
);

CREATE INDEX idx_subscription_customer ON subscription (customer_id);
