CREATE TABLE refresh_tokens (
    id           UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    token        VARCHAR(255)  NOT NULL UNIQUE,
    user_id      UUID          NOT NULL REFERENCES users(id),
    tenant_id    UUID          NOT NULL REFERENCES tenants(id),
    expires_at   TIMESTAMPTZ   NOT NULL,
    invalidated  BOOLEAN       NOT NULL DEFAULT false,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_token    ON refresh_tokens(token);
CREATE INDEX idx_refresh_tokens_user_id  ON refresh_tokens(user_id);
