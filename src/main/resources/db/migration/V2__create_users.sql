CREATE EXTENSION IF NOT EXISTS pgcrypto;



CREATE TABLE users (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID            NOT NULL REFERENCES tenants(id),
    email               VARCHAR(255)    NOT NULL,
    password_hash       VARCHAR(255)    NOT NULL,
    display_name        VARCHAR(100),
    avatar_url          TEXT,
    roles               TEXT[]          NOT NULL DEFAULT ARRAY[ 'BUYER' ],
    stripe_connect_id   VARCHAR(255),
    suspended_at        TIMESTAMPTZ,
    deleted_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL,
    UNIQUE( tenant_id, email )
);

ALTER TABLE users ADD CONSTRAINT valid_role_types
    CHECK (
        roles <@ ARRAY[
            'BUYER',
            'SELLER',
            'MARKETPLACE_STAFF',
            'MARKETPLACE_OWNER',
            'OSPORO_ADMIN'
        ]::TEXT[]
    );

CREATE INDEX idx_users_tenant_id ON users(tenant_id);