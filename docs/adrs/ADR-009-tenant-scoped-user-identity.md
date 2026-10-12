# ADR-009: Tenant-Scoped User Identity

## Status
Accepted

## Date
2026-03-14

## Context

Osporo powers independent marketplaces. Each marketplace is a tenant — an isolated deployment with its own users, listings, orders, and configuration. A person may use multiple Osporo-powered marketplaces without ever knowing they share underlying infrastructure.

The question is how user identity is modelled across this multi-tenant system. Two approaches exist:

**Tenant-scoped identity**: each marketplace has its own user directory. A person using two Osporo-powered marketplaces has two independent User records with separate credentials — one per tenant.

**Cross-tenant identity**: a single User record exists at the platform level. A join entity (`UserTenantMembership`) records the user's roles and status within each tenant they belong to. One login, multiple marketplace memberships.

This decision determines the shape of the User entity, the JWT structure, the registration flow, and the security boundary between tenants.

## Decision

**Users are tenant-scoped. A User record belongs to exactly one tenant. Cross-tenant identity is not supported.**

The User entity carries a `tenant_id` foreign key that scopes it entirely to that tenant. A person using two Osporo-powered marketplaces has two separate User records, two separate credentials, and two completely independent identities. Neither marketplace has any awareness of the other, and neither user record has any relationship to the other.

The `roles` array lives on the User entity, scoped by `tenant_id`:

```
User
────
id               UUID        PK
tenant_id        UUID        FK → Tenant
email            VARCHAR     unique within tenant scope
password_hash    VARCHAR
roles            TEXT[]      role names assigned within this tenant
status           ENUM        ACTIVE, SUSPENDED
created_at       TIMESTAMP
updated_at       TIMESTAMP
deleted_at       TIMESTAMP   nullable, soft delete
```

Email uniqueness is enforced at the `(tenant_id, email)` level, not globally. The same email address may be registered on multiple Osporo-powered marketplaces.

## Alternatives Considered

### Cross-tenant identity with UserTenantMembership

Evaluated and rejected on two grounds.

**Security**: a single set of credentials becomes a skeleton key across every marketplace the user participates in. A phishing attack on one marketplace — a buyer on a low-value goods platform — immediately compromises the user's seller account on a high-value vehicle marketplace. This is not a flaw that can be mitigated in the implementation — it is a fundamental property of shared credentials across unrelated security domains. The only way to prevent it is to not share credentials.

**Architecture**: managing a cross-tenant identity layer moves Osporo from infrastructure to platform. Infrastructure provides the rails. A platform owns the user relationship. These are different products with different responsibilities and different trust obligations. Osporo's position is infrastructure — it powers marketplaces, it does not own their users.

### Single global user table with tenant filtering

Equivalent to cross-tenant identity in its security implications. Rejected for the same reasons.

## Consequences

### Positive
- Each tenant's user directory is completely isolated. A security incident affecting one tenant's user records has no implications for any other tenant.
- The User entity is simple. No join tables, no membership resolution, no cross-tenant queries.
- Registration, authentication, and credential management are entirely within the tenant's scope. Tenants can implement their own credential policies without affecting other tenants.
- Aligns correctly with the infrastructure model — Osporo does not own or manage a cross-tenant user identity.

### Negative
- A person using multiple Osporo-powered marketplaces manages separate credentials for each. This is by design — the security isolation is the point — but it is a different experience from platforms with unified accounts.
- There is no mechanism for a user to discover or link their accounts across tenants. This is also by design.

## Revisit Criteria

This decision should be revisited only if Osporo's product direction changes to include a platform-level identity layer — for example, an "Osporo account" that provides verified identity across tenants. That would represent a fundamental shift from infrastructure to platform and would require a new ADR addressing the security model, trust boundaries, and data ownership implications in full.
