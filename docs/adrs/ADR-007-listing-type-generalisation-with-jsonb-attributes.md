# ADR-007: Listing Type Generalisation with JSONB Attributes

**Status:** Accepted  
**Date:** 2026-03-12  
**Author:** Wahina Prince Chabalala  
**Deciders:** Wahina Prince Chabalala  

---

## Context

Osporo is a marketplace infrastructure platform, not a physical goods marketplace.
Tenants deploy Osporo to run marketplaces of entirely different types: a used
goods platform (physical products), a template or digital asset store (digital
goods), a freelance services marketplace (services), a vehicle dealership platform
(vehicles), or an entirely custom domain.

Each listing type requires different attributes. A physical good listing needs
condition and weight. A digital good needs a delivery URL and a file format.
A vehicle needs a VIN, make, model, year, and mileage. A service needs a
turnaround time and scope description.

These differences also affect the order fulfilment lifecycle — a digital good
delivers instantly on payment, a physical good requires shipping and confirmation,
a service requires delivery and approval.

The schema design decision is how to model these varying attribute requirements
in a single `listings` table that serves all marketplace types without requiring
schema changes to support new types.

---

## Decision

**Osporo will use a `listing_type` enum column and an `attributes` JSONB column
on the `listings` table to generalise listing behaviour across all marketplace
types.**

A `tenant_listing_configs` table stores the per-tenant configuration: which
listing type is active for this marketplace, what the delivery method is, and
which attributes are required or optional. The engine validates listing attributes
at the service layer against this configuration before persisting any listing.

```sql
-- Listing type and attributes on the listings table
CREATE TYPE listing_type AS ENUM (
    'PHYSICAL_GOOD',
    'DIGITAL_GOOD',
    'SERVICE',
    'VEHICLE',
    'CUSTOM'
);

CREATE TYPE delivery_method AS ENUM (
    'SHIPPING',
    'DIGITAL_DELIVERY',
    'IN_PERSON',
    'SERVICE_COMPLETION'
);

ALTER TABLE listings
    ADD COLUMN listing_type listing_type NOT NULL,
    ADD COLUMN attributes   JSONB;

-- GIN index for attribute queries
CREATE INDEX idx_listings_attributes ON listings USING GIN(attributes);

-- Per-tenant configuration
CREATE TABLE tenant_listing_configs (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL UNIQUE REFERENCES tenants(id),
    listing_type        listing_type NOT NULL DEFAULT 'PHYSICAL_GOOD',
    delivery_method     delivery_method NOT NULL,
    required_attributes JSONB,      -- schema definition, not data
    commission_rate     NUMERIC(5,4) NOT NULL DEFAULT 0.05,
    allow_negotiation   BOOLEAN NOT NULL DEFAULT false,
    track_inventory     BOOLEAN NOT NULL DEFAULT false,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL
);
```

At the service layer, listing creation validates attributes against the tenant's
configuration before any database write:

```java
// Service layer validation — database never stores unvalidated attributes
public Listing createListing(CreateListingRequest request, TenantContext ctx) {
    TenantListingConfig config = configRepository.findByTenantId(ctx.getTenantId());
    attributeValidator.validate(request.getAttributes(), config.getRequiredAttributes());
    // proceeds to persistence only if validation passes
}
```

The `listing_type` on a listing is inherited from the tenant's
`TenantListingConfig` at creation time and is immutable afterward.

---

## Reasons For This Decision

### 1. The alternative — separate tables per listing type — breaks down quickly

The most obvious alternative is a separate table for each listing type:
`physical_good_listings`, `digital_good_listings`, `vehicle_listings`. This
approach has an immediate problem: core relational queries become a union across
multiple tables.

"Show all listings" requires a UNION. "Search all listings" requires searching
across multiple tables. Foreign keys from orders and messages that reference
"a listing" can no longer point to a single table — they must use a polymorphic
pattern or pick one table as canonical. Adding a new listing type requires
a schema migration and new application code for every layer of the stack.

The relational structure breaks in every direction. This approach is rejected
without significant consideration.

### 2. Entity-Attribute-Value (EAV) is worse than JSONB

EAV is a pattern that stores attributes as rows in a separate table:

```sql
listing_attributes (listing_id, attribute_name, attribute_value)
```

EAV tables are notoriously painful to query. Retrieving all attributes for a
listing requires multiple rows and typically a PIVOT or application-layer
aggregation. Querying "all vehicle listings with mileage under 50,000" requires
a self-join or complex subquery. Indexing specific attributes is non-trivial.
Type safety is lost — everything is stored as a string.

PostgreSQL's JSONB column provides the same flexibility with far better query
support, proper type handling within the JSON structure, GIN indexing for
attribute key queries, and a single column that returns all attributes in one
row. JSONB strictly dominates EAV for this use case.

### 3. JSONB is not schema-less — validation happens at the service layer

A common objection to JSONB for structured data is that it permits arbitrary,
unvalidated data to enter the database. This objection is addressed directly by
the `TenantListingConfig.required_attributes` schema and the service-layer
validation that runs before every write.

The database does not enforce attribute schemas because the attribute requirements
vary by tenant. The application enforces them. This is a deliberate layering
decision: the database handles relational integrity (entities, foreign keys,
constraints on typed columns), and the application handles business rules (which
attributes are required for this tenant's listing type). These are different
responsibilities and belong in different layers.

The key constraint is that no database write occurs without passing service-layer
validation. This is enforced by the architecture — the repository is not directly
accessible from outside its module, and the only path to creating a listing goes
through the service that runs validation.

### 4. TenantListingConfig as the configuration layer

The `tenant_listing_configs` table is the architectural mechanism that makes
Osporo's single engine serve multiple marketplace types. It stores not listing
data but listing behaviour — what type of listing this marketplace uses, how
orders fulfil, what attributes are required, and what the commission structure is.

This table is what a vehicle marketplace configures differently from a digital
goods marketplace. The engine code does not fork per listing type. It reads
the configuration and behaves accordingly. Adding a new listing type means
adding a value to the enum and writing validation logic — not restructuring the
schema or branching the engine code.

### 5. The delivery method drives the order state machine, not the listing type directly

A key design insight: the order state machine does not branch on `listing_type`.
It branches on `delivery_method`, which is denormalised from `TenantListingConfig`
onto the `Order` at creation time.

This separation means the same delivery method can serve multiple listing types.
`IN_PERSON` delivery works for both vehicle pickup and local service delivery.
`SHIPPING` works for physical goods and physical rental returns. The listing type
classifies what is being sold. The delivery method governs how it moves. These
are separate concerns and keeping them separate avoids a combinatorial explosion
of state machine branches.

```java
// Order state machine branches on delivery_method, not listing_type
switch (order.getDeliveryMethod()) {
    case DIGITAL_DELIVERY -> completeImmediately(order);
    case SHIPPING, IN_PERSON -> awaitConfirmation(order);
    case SERVICE_COMPLETION -> awaitServiceDelivery(order);
}
```

---

## Alternatives Considered

### Separate tables per listing type

**Rejected.** Breaks relational integrity, requires UNION queries for core
operations, makes foreign keys from orders and messages ambiguous, and requires
schema migrations for every new listing type. Not viable.

### Entity-Attribute-Value (EAV) pattern

**Rejected.** Complex queries, no type safety, poor indexing story, application-
layer aggregation required for every attribute fetch. PostgreSQL JSONB is
strictly better in every dimension for this use case.

### Class Table Inheritance (one base table + type-specific extension tables)

**Considered and rejected.** Class table inheritance uses a base `listings` table
with shared columns and separate extension tables (`vehicle_listing_details`,
`digital_good_listing_details`) joined via the listing's primary key.

This approach has clean type safety and avoids nullable columns on the base table.
However, it requires a JOIN for every listing fetch to retrieve full data. Adding
a new type requires a new table and new JOIN logic throughout the application.
Querying across types (search, browse) still requires UNION or LEFT JOINs.
For Osporo's use case, where tenants typically operate a single listing type,
the complexity is not justified by the type safety benefit over JSONB with
service-layer validation.

### Separate listing type per tenant using polymorphic dispatch

**Partially adopted.** This is essentially what `TenantListingConfig` does —
it configures per-tenant listing behaviour. The decision to use a single shared
table with JSONB rather than type-specific tables is the specific rejection within
this broader pattern.

---

## Consequences

### Positive

- Single `listings` table. All relational queries — orders, messages, reports,
  search — reference one table cleanly.
- New listing types added via enum extension and configuration — no schema
  restructuring required.
- GIN index on `attributes` enables efficient queries on specific attribute keys
  when needed.
- `TenantListingConfig` makes marketplace type a configuration concern, not a
  code fork.
- `delivery_method` on `Order` cleanly decouples fulfilment behaviour from
  listing classification.

### Negative

- Attributes are not type-enforced at the database level. A vehicle listing could
  theoretically store malformed VIN data if service-layer validation is bypassed.
  Mitigated by ensuring the repository is not directly accessible and all writes
  go through the validated service layer. Explicit integration tests assert that
  invalid attributes are rejected.
- JSONB columns are opaque to tools that inspect the database schema directly —
  a database schema viewer will show `attributes JSONB` without knowing what
  keys are expected. Mitigated by documenting the attribute schema in
  `TenantListingConfig.required_attributes` and in the API documentation.
- Querying deeply nested JSONB attributes at scale requires careful index design.
  Mitigated by restricting which attribute keys are indexable and maintaining
  GIN indexes on commonly queried keys.

---

## Revisit Criteria

This decision should be revisited when:

- A listing type emerges with attributes so structurally complex that JSONB
  storage becomes genuinely unwieldy compared to a dedicated table
- Query performance on `attributes` JSONB degrades at production listing volume
  despite GIN indexing, and the degradation is attributable to the JSONB model
  rather than missing indexes

---

## References

- ADR-003: PostgreSQL as Primary Database (JSONB capability)
- ADR-002: Shared Schema Multi-Tenancy (tenant_id context)
- Domain Model Reference v0.2 — Listing entity, TenantListingConfig entity
- Requirements v0.2 — Section 1.5 Listing Type Configuration
- PostgreSQL JSONB documentation — Indexing JSONB
