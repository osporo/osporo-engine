# ADR-003: PostgreSQL as Primary Database

**Status:** Accepted  
**Date:** 2026-03-12  
**Author:** Wahina Prince Chabalala  
**Deciders:** Wahina Prince Chabalala  

---

## Context

Osporo requires a primary data store for all domain entities — tenants, users,
listings, orders, escrow holds, messages, and reports. The data is highly
relational: orders reference listings, listings reference users and categories,
escrow holds reference orders. Financial integrity requires ACID transactions
across multiple entities. The system also requires full-text search over listing
titles and descriptions, and will later require geospatial queries for
location-based filtering.

The choice of database is one of the highest-consequence decisions in the
system — it affects correctness guarantees, query capability, operational
complexity, and the cost of changing later.

---

## Decision

**Osporo will use PostgreSQL as its sole primary database.**

All domain data — relational entities, full-text search vectors, JSONB attribute
data, and audit logs — lives in a single PostgreSQL instance. No secondary
database is introduced for any use case that PostgreSQL handles adequately.

PostgreSQL version 16+ is targeted, which provides mature support for:

```sql
-- Full-text search with GIN index
ALTER TABLE listings ADD COLUMN search_vector TSVECTOR;
CREATE INDEX idx_listings_search ON listings USING GIN(search_vector);

-- JSONB for semi-structured listing attributes
ALTER TABLE listings ADD COLUMN attributes JSONB;
CREATE INDEX idx_listings_attributes ON listings USING GIN(attributes);

-- Append-only audit log
CREATE TABLE order_events (
    id           UUID PRIMARY KEY,
    order_id     UUID NOT NULL REFERENCES orders(id),
    occurred_at  TIMESTAMPTZ NOT NULL DEFAULT now()
    -- No updated_at — this table is never updated
);
```

---

## Reasons For This Decision

### 1. Relational integrity is non-negotiable for financial data

Orders, escrow holds, and payment records are financial data. The relationships
between them must be enforced at the database level, not just the application
level. A foreign key constraint that prevents an EscrowHold existing without a
corresponding Order is not optional — it is a data integrity guarantee that
prevents an entire class of bugs from reaching production.

PostgreSQL's foreign keys, NOT NULL constraints, and CHECK constraints form a
second layer of correctness beneath the application code. No document database
or key-value store provides this.

### 2. ACID transactions across the order lifecycle

The critical path of Osporo's order flow requires atomic writes across multiple
tables:

```
BEGIN;
  INSERT INTO orders (...) VALUES (...);
  INSERT INTO order_events (...) VALUES (...);
  INSERT INTO escrow_holds (...) VALUES (...);
COMMIT;
```

If any step fails, none of them should persist. PostgreSQL's ACID transaction
model guarantees this. A distributed system or eventually consistent database
cannot provide this guarantee without significant additional complexity —
saga patterns, compensating transactions, idempotency handling — all of which
introduce failure modes that are inappropriate for financial flows.

### 3. Full-text search without a separate service

Osporo's MVP requires keyword search across listing titles and descriptions.
PostgreSQL's `tsvector` / `tsquery` full-text search with a GIN index handles
this without introducing Elasticsearch or a dedicated search service.

This is not a permanent decision. The search module is abstracted behind an
interface specifically so that Elasticsearch can be substituted later if query
complexity or search latency justifies it. But introducing Elasticsearch in the
MVP for a search problem that PostgreSQL solves adequately is unnecessary
operational overhead.

### 4. JSONB for listing attributes without schema fragmentation

Osporo supports multiple listing types — physical goods, digital goods, services,
vehicles — each with different required attributes. Storing these in a single
JSONB column on the listings table avoids the need for separate attribute tables
per type, while still allowing indexed queries on specific attribute keys when
needed.

PostgreSQL's JSONB is not a document database — it lives alongside relational
data in the same transaction, queryable with standard SQL. This is precisely the
capability needed for semi-structured attributes on otherwise relational entities.

### 5. Operational simplicity

PostgreSQL is the most operationally well-understood open source database in
existence. Managed offerings are available on every cloud provider — RDS,
Cloud SQL, Supabase, Neon. Backup tooling, monitoring integrations, and
operational runbooks are mature and widely documented. The self-hosting
community for PostgreSQL is large enough that developers deploying Osporo
independently will have no difficulty running it.

Introducing a second database technology (MongoDB, Redis as primary store,
DynamoDB) doubles the operational knowledge required and the surface area
for things to go wrong.

---

## Alternatives Considered

### MySQL / MariaDB

**Rejected.** PostgreSQL is strictly superior for Osporo's specific requirements.
MySQL's JSON support is less capable than PostgreSQL's JSONB. Full-text search
in MySQL is less mature. PostgreSQL's constraint system is richer. No meaningful
advantage for MySQL in this context.

### MongoDB

**Rejected.** MongoDB's document model would require re-implementing relational
integrity at the application layer. Multi-document ACID transactions in MongoDB
are supported but significantly more complex and less performant than PostgreSQL
transactions. Financial data in a document database without enforced schema
constraints is an operational risk. No use case in Osporo justifies the tradeoff.

### CockroachDB / Distributed SQL

**Rejected.** Distributed SQL solves horizontal write scaling across geographic
regions. Osporo does not have this problem. CockroachDB introduces distributed
transaction overhead and operational complexity that is entirely unjustified at
this scale. Premature optimisation at the data layer.

### PostgreSQL + Redis for caching

**Deferred, not rejected.** Redis as a cache layer is a valid future addition
for session data, rate limiting counters, or hot listing caches under high read
load. It is not introduced in the MVP because there is no measured performance
problem that requires it. PostgreSQL with proper indexing handles MVP read loads
without a cache layer.

### PostgreSQL + Elasticsearch for search

**Deferred, not rejected.** Elasticsearch is a valid future substitution for the
search module when query complexity or latency under high listing volume justifies
it. The search module is abstracted behind an interface for exactly this reason.
See ADR-001 for the module boundary strategy that enables this substitution.

---

## Consequences

### Positive

- Single database technology. One set of operational knowledge required.
- ACID transactions across the full order lifecycle without saga complexity.
- Full-text search, JSONB attributes, and relational integrity in one system.
- `docker compose up` includes one database container — self-hosting is simple.
- Rich constraint system provides a second correctness layer beneath application code.

### Negative

- Full-text search has limits. Complex relevance scoring, fuzzy matching, and
  faceted search at high listing volume will eventually require Elasticsearch.
  Accepted — the search interface abstraction handles this transition.
- Single-node PostgreSQL is a vertical scaling boundary. Mitigated with read
  replicas for heavy read loads and connection pooling via PgBouncer.
- JSONB queries on `attributes` require careful indexing. Unindexed JSONB
  queries at table scale are slow. Mitigated with GIN indexes on commonly
  queried attribute keys and validation that prevents arbitrary attribute storage.

---

## Revisit Criteria

This decision should be revisited when:

- Full-text search latency under production listing volume exceeds acceptable
  response time targets with proper GIN indexing in place
- Write throughput on the orders table exceeds what a single primary PostgreSQL
  node can handle, measured under real load
- A specific use case arises that PostgreSQL genuinely cannot serve and that
  justifies introducing a second database technology

---

## References

- ADR-001: Modular Monolith over Microservices (search abstraction strategy)
- ADR-002: Shared Schema Multi-Tenancy (tenant_id on all tables)
- ADR-007: Listing Type Generalisation with JSONB Attributes (JSONB usage)
- PostgreSQL 16 Full-Text Search documentation
- PostgreSQL JSONB documentation
