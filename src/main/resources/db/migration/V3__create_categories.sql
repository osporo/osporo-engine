CREATE TABLE categories (
    id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID            NOT NULL REFERENCES tenants(id),
    parent_id   UUID            REFERENCES categories(id),
    name        VARCHAR(100)    NOT NULL,
    slug        VARCHAR(120)    NOT NULL,
    is_active   BOOLEAN         NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, slug)
);