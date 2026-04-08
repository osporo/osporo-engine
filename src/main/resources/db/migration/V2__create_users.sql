CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TYPE role_type AS ENUM ( 'OSPORO_ADMIN', 'MARKETPLACE_OWNER', 'MARKETPLACE_STAFF', 'BUYER', 'SELLER' );

CREATE TABLE users (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID            NOT NULL REFERENCES tenants(id),
    email               VARCHAR(255)    NOT NULL,
    password_hash       VARCHAR(255)    NOT NULL,
    display_name        VARCHAR(100)    NOT NULL,
    avatar_url          TEXT,
    roles               role_type[]     NOT NULL DEFAULT ARRAY[ 'BUYER' ]::role_type[],
    stripe_connect_id   VARCHAR(255),
    suspended_at        TIMESTAMPTZ,
    deleted_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ      NOT NULL,
    UNIQUE( tenant_id, email )
);

CREATE INDEX idx_users_tenant_id ON users(tenant_id);