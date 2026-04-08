CREATE TYPE order_status AS ENUM (
    'PENDING_PAYMENT',
    'PAID',
    'SHIPPED',
    'DELIVERED',
    'COMPLETED',
    'REFUNDED',
    'CANCELLED'
);

CREATE TABLE orders (
    id                          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID            NOT NULL REFERENCES tenants(id),
    listing_id                  UUID            NOT NULL REFERENCES listings(id),
    buyer_id                    UUID            NOT NULL REFERENCES users(id),
    seller_id                   UUID            NOT NULL REFERENCES users(id),
    status                      order_status    NOT NULL DEFAULT 'PENDING_PAYMENT',
    amount                      NUMERIC(12, 2)  NOT NULL,
    currency                    CHAR(3)         NOT NULL,
    platform_fee                NUMERIC(12, 2)  NOT NULL,
    seller_payout               NUMERIC(12, 2)  NOT NULL,
    stripe_payment_intent_id    VARCHAR(255)    UNIQUE,
    expected_delivery_at        TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                  timestamptz     NOT NULL
);