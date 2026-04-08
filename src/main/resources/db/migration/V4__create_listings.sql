CREATE TYPE listing_status AS ENUM ( 'DRAFT', 'ACTIVE', 'SOLD', 'REMOVED' );

CREATE TABLE listings (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID            NOT NULL REFERENCES tenants(id),
    seller_id           UUID            NOT NULL REFERENCES users(id),
    category_id         UUID            NOT NULL REFERENCES categories(id),
    title               VARCHAR(255)    NOT NULL,
    slug                VARCHAR(300)    NOT NULL,
    description         TEXT            NOT NULL,
    price               NUMERIC(12, 2)  NOT NULL,
    currency            CHAR(3)         NOT NULL,
    status              listing_status  NOT NULL DEFAULT 'DRAFT',
    location_display    VARCHAR(255),
    location_lat        NUMERIC(9, 6),
    location_lng        NUMERIC(9, 6),
    search_vector       TSVECTOR,
    deleted_at          TIMESTAMPTZ,
    expires_at          TIMESTAMPTZ,
    is_active           BOOLEAN         NOT NULL DEFAULT true,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          timestamptz     NOT NULL,
    UNIQUE(tenant_id, slug)
);

CREATE TABLE listing_images (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id          UUID            NOT NULL REFERENCES listings(id),
    storage_key         TEXT            NOT NULL,
    url                 TEXT            NOT NULL,
    sort_order          SMALLINT        NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);