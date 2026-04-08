CREATE TYPE tenant_status AS ENUM ( 'ACTIVE', 'SUSPENDED' );

CREATE TABLE tenants (
    id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255)    NOT NULL,
    slug        VARCHAR(100)    NOT NULL UNIQUE,
    status      tenant_status   NOT NULL DEFAULT 'ACTIVE',
    plan        VARCHAR(50)     NOT NULL,
    created_at  TIMESTAMPTZ      NOT NULL DEFAULT now()
);