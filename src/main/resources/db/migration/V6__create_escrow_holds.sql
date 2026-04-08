CREATE TYPE escrow_status AS ENUM ( 'HOLDING', 'RELEASED', 'REFUNDED', 'DISPUTED' );

CREATE TABLE escrow_holds (
    id                          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id                    UUID            NOT NULL REFERENCES orders(id),
    stripe_payment_intent_id    VARCHAR(255)    NOT NULL,
    held_amount                 NUMERIC(12, 2)  NOT NULL,
    release_after               TIMESTAMPTZ     NOT NULL,
    status                      escrow_status   NOT NULL DEFAULT 'HOLDING',
    released_at                 TIMESTAMPTZ     NOT NULL,
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT now()
);