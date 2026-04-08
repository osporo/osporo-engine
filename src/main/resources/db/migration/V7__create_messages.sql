CREATE TABLE message_threads (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL REFERENCES tenants(id),
    listings_id     UUID            NOT NULL REFERENCES listings(id),
    buyer_id        UUID            NOT NULL REFERENCES users(id),
    seller_id       UUID            NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    last_message_at TIMESTAMPTZ     NOT NULL,
    UNIQUE(tenant_id, listings_id, buyer_id)
);

CREATE TABLE messages (
    id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    thread_id   UUID            NOT NULL REFERENCES message_threads(id),
    sender_id   UUID            NOT NULL REFERENCES users(id),
    body        TEXT            NOT NULL,
    is_flagged  BOOLEAN         NOT NULL DEFAULT false,
    sent_at     TIMESTAMPTZ     NOT NULL DEFAULT now()
)