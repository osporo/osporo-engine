# ADR-002: Shared Schema Multi-Tenancy

**Status:** Accepted  
**Date:** 2026-03-12  
**Author:** Wahina Prince Chabalala  
**Deciders:** Wahina Prince Chabalala  

---

## Context

Osporo is a multi-tenant platform. Each tenant is an independent marketplace operator
running on shared infrastructure. The platform must isolate tenant data such that one
tenant cannot access or affect another's data, while keeping infrastructure costs and
operational complexity manageable at early scale.

There are three established patterns for multi-tenant data isolation in relational
databases:

1. **Separate databases per tenant** — each tenant gets their own database instance
2. **Separate schemas per tenant** — one database, one schema per tenant
3. **Shared schema** — one database, one schema, all tenants in the same tables,
   differentiated by a `tenant_id` column on every row

The choice of pattern has significant consequences for infrastructure cost, query
complexity, migration management, and the platform's ability to onboard tenants
dynamically without manual intervention.

---

## Decision

**Osporo will use a shared schema multi-tenancy model.**

Every domain entity table carries a `tenant_id UUID NOT NULL` column with a foreign
key to the `tenants` table. All queries are scoped to the current tenant via this
column. Tenant isolation is enforced at the application layer through a
`TenantContext` that is populated from the authenticated JWT on every request and
injected into all repository queries automatically.

The `tenant_id` column is present on every entity table from the initial schema,
even in the single-tenant MVP where only one default tenant exists. This is
non-negotiable — adding `tenant_id` retrospectively to a populated production
database is a high-risk, expensive migration.

```sql
-- Every domain table follows this pattern
CREATE TABLE listings (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL REFERENCES tenants(id),
    -- ... other columns
);

CREATE INDEX idx_listings_tenant_id ON listings(tenant_id);
```

The application resolves the current tenant on every request:

```
Request arrives → JWT validated → tenant_id extracted from claims
→ stored in TenantContext (ThreadLocal) → injected into all queries
→ cleared after request completes
```

---

## Reasons For This Decision

### 1. Infrastructure cost at early scale

Separate databases per tenant means provisioning a Postgres instance for every
tenant onboarded. At 10 tenants this is manageable. At 100 tenants it becomes
expensive and operationally complex. At 1,000 tenants it is economically
unviable for a bootstrapped product competing on price against $299/month SaaS
alternatives.

Shared schema means one database cluster serves all tenants. Resource utilisation
is pooled. A tenant with low activity doesn't hold idle database resources. A
tenant onboarding at 2am does not require manual infrastructure provisioning.

### 2. Zero-touch tenant onboarding

With separate databases or schemas, creating a new tenant requires provisioning
infrastructure — creating a database, running migrations, configuring connection
strings. This cannot be fully automated without significant orchestration overhead.

With shared schema, onboarding a new tenant is an INSERT into the `tenants` table
and a commit. The platform can onboard tenants programmatically through the normal
application layer. This is essential for a self-serve SaaS product.

### 3. Migration management

With 100 tenants each in their own schema or database, a schema migration requires
running Flyway 100 times, coordinating failures, and handling partial migration
states. With shared schema, one Flyway run migrates all tenants simultaneously.

Migration complexity scales with the number of migration runs required. Shared
schema keeps this at a constant O(1) regardless of tenant count.

### 4. Acceptable isolation for the use case

Shared schema multi-tenancy provides logical isolation, not physical isolation.
The risk is that a bug in the application layer exposes one tenant's data to
another. This is mitigated by the `TenantContext` being applied consistently and
tested explicitly.

For Osporo's use case — marketplace operators running independent marketplaces —
this level of isolation is appropriate. These are not financial institutions with
regulatory requirements for physical data separation. The isolation level matches
the risk profile of the customer.

Enterprise tenants with stricter isolation requirements are an edge case handled
at the infrastructure level if and when that demand materialises.

---

## Alternatives Considered

### Separate database per tenant

**Rejected.** Infrastructure cost scales linearly with tenant count. Tenant
onboarding requires infrastructure provisioning. Migrations must run per tenant.
Connection pooling becomes complex — each tenant database requires its own pool.
Unacceptable operational overhead for a bootstrapped product at early scale.

### Separate schema per tenant

**Rejected.** Solves some of the isolation concern but retains most of the
migration complexity of separate databases. Postgres schema-per-tenant with Flyway
requires either one migration run per schema or significant custom tooling.
Connection pooling across schemas is non-trivial. Offers minimal benefit over
shared schema for Osporo's customer risk profile while retaining significant
operational cost.

### Hybrid: shared schema with schema isolation for enterprise

**Deferred.** A valid long-term position — standard tenants in shared schema,
enterprise tenants with strict regulatory requirements in isolated schemas or
databases. Implementing this complexity before enterprise demand exists is
premature. The architecture does not prevent this hybrid model from being
introduced later.

---

## Consequences

### Positive

- Tenant onboarding is a single database write. Fully automatable.
- One Flyway migration run applies to all tenants simultaneously.
- Infrastructure scales to many tenants on a single database cluster.
- Simple connection pooling — one pool, all tenants.

### Negative

- Tenant isolation is logical, not physical. A missing `tenant_id` filter in
  a query would expose cross-tenant data. Mitigated by consistent use of
  `TenantContext` and explicit integration tests that assert isolation.
- Tables grow larger as tenant count scales. Mitigated with composite indexes
  on `(tenant_id, ...)` for all common query patterns.
- A runaway query from one tenant affects database performance for all tenants.
  Mitigated with query timeouts, read replicas for heavy read operations, and
  per-tenant rate limiting at the API layer.

---

## Revisit Criteria

This decision should be revisited when:

- A regulated enterprise tenant requires contractual physical data isolation
- Database performance degrades measurably due to table size and tenant count
  despite proper indexing
- A security audit identifies the shared schema isolation level as insufficient
  for the customer base at that point

---

## References

- ADR-001: Modular Monolith over Microservices (related)
- ADR-003: PostgreSQL as Primary Database (related)
- Hibernate Multi-tenancy documentation
- [The SaaS CTO Security Guide — Data Isolation](https://www.saascto.guide)
