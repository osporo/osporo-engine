CREATE TABLE tenant_role_config (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID            NOT NULL REFERENCES tenants(id),
    role_name           TEXT            NOT NULL,
    permissions         TEXT[]          NOT NULL,
    is_default          BOOLEAN         NOT NULL DEFAULT false,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_tenant_role_config_tenant_role ON tenant_role_config (tenant_id, role_name);

CREATE UNIQUE INDEX uq_tenant_role_config_default ON tenant_role_config (tenant_id) WHERE is_default = true;
