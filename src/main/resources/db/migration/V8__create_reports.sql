CREATE TYPE report_target_type AS ENUM ( 'LISTING', 'USER' );

CREATE TYPE report_status AS ENUM ( 'OPEN', 'REVIEWED', 'RESOLVED' );

CREATE TYPE report_actions AS ENUM ( 'SUSPEND', 'LIFT' );

CREATE TABLE reports (
    id              UUID                PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID                NOT NULL REFERENCES tenants(id),
    reporter_id     UUID                NOT NULL REFERENCES users(id),
    target_type     report_target_type  NOT NULL,
    target_id       UUID                NOT NULL,
    reason          VARCHAR(500)        NOT NULL,
    status          report_status       NOT NULL DEFAULT 'OPEN',
    resolved_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ         NOT NULL DEFAULT now()
);

CREATE TABLE suspensions (
    id              UUID                PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID                NOT NULL REFERENCES tenants(id),
    user_id         UUID                NOT NULL REFERENCES users(id),
    actor_id        UUID                NOT NULL REFERENCES users(id),
    action          report_actions      NOT NULL,
    violation_type  VARCHAR(500)        NOT NULL,
    details         TEXT                NOT NULL DEFAULT '',
    suspension_id   UUID                REFERENCES suspensions(id),
    action_date     TIMESTAMPTZ         NOT NULL DEFAULT now()
);