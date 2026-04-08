# ADR-001: Modular Monolith over Microservices

**Status:** Accepted  
**Date:** 2026-03-12  
**Author:** Wahina Prince Chabalala  
**Deciders:** Wahina Prince Chabalala  

---

## Context

MIP is a Marketplace as a Service platform built by a single engineer at inception.
The system must support multi-tenant marketplace operations including listings, orders,
escrow payments, messaging, and moderation — with a clear path to scaling as tenant
count and transaction volume grow.

At the outset of architecture design, two credible structural approaches were
considered: a **microservices architecture** and a **modular monolith**.

Microservices are commonly associated with senior engineering thinking and scalable
systems. The instinct to reach for them is understandable. This ADR documents why
that instinct was deliberately resisted and what was chosen instead.

---

## Decision

**MIP will be built as a modular monolith.**

The backend is a single deployable Spring Boot application organised into clearly
bounded domain modules. Each module owns its domain layer, service layer, repository,
and API controller. Modules communicate through defined interfaces — never through
direct repository access across module boundaries.

The package structure enforces this:

```
com.mip.marketplace
├── tenancy/
├── marketplace/
│   ├── listing/
│   │   ├── domain/
│   │   ├── application/
│   │   ├── infrastructure/
│   │   └── api/
│   ├── order/
│   ├── category/
│   ├── messaging/
│   └── moderation/
├── user/
├── payment/
├── search/
├── storage/
└── shared/
```

---

## Reasons For This Decision

### 1. Microservices solve problems we do not yet have

Microservices exist to solve specific problems: independent deployability of services
with different scaling requirements, team autonomy across large engineering
organisations, and fault isolation between independently valuable capabilities.

MIP has none of these problems at inception. There is one engineer. There are zero
tenants. There is no measured data showing that the listing service needs different
scaling characteristics than the messaging service. Building microservices now would
be building solutions to imaginary problems at real engineering cost.

Martin Fowler's microservices premium applies directly here: microservices impose
a significant operational and cognitive overhead that only pays off when the
complexity of the system at scale outweighs that overhead. That threshold has not
been reached and will not be reached for a long time.

### 2. A distributed system is harder to build correctly

Microservices introduce distributed systems problems that a monolith does not have.
Network partitions. Eventual consistency. Distributed transactions. Service discovery.
Inter-service authentication. Observability across service boundaries.

The order creation flow in MIP must atomically create an Order record, an EscrowHold,
and capture a Stripe payment — and fail cleanly if any step fails. In a monolith,
this is a database transaction. In a microservices architecture, this is a saga
pattern with compensating transactions. The latter is significantly harder to
implement correctly and significantly harder to debug when it goes wrong.

Correctness in financial flows is non-negotiable. The simpler implementation path
is the more reliable one at this stage.

### 3. The module boundaries are the real architectural investment

The value in this decision is not choosing a monolith over microservices. It is
enforcing module boundaries that would allow extraction into services later if
genuinely needed.

Each module is cohesive, loosely coupled, and communicates through interfaces.
The `listing` module does not reach into the `order` module's repository. The
`order` module does not know how messaging works internally. If order processing
ever needs independent scaling, the module can be extracted into a service with
minimal refactoring — the boundary already exists in the code.

This is the architectural investment: clean modules, not distributed infrastructure.

### 4. Developer experience and open source accessibility

MIP is an open source project. A developer who forks the repo should be able to
run the entire system with `docker compose up` and have a working marketplace in
under fifteen minutes.

A microservices architecture means running five or more services, understanding
inter-service networking, and managing service discovery — before writing a single
line of custom code. That friction kills open source adoption. A well-structured
monolith runs as one process, one database, and is immediately comprehensible.

The lower the barrier to the first running instance, the larger the contributor
community. That is a direct business interest, not just a developer convenience.

---

## Alternatives Considered

### Microservices from the start

**Rejected.** No measured justification for the operational overhead. Introduces
distributed systems complexity into financial flows where correctness is critical.
Damages developer experience for self-hosting and open source contribution.
Premature optimisation at the architecture level.

### Serverless / function-based architecture

**Rejected.** Serverless functions are poorly suited to long-running transactional
flows like escrow processing and webhook handling. Cold start latency is incompatible
with a responsive API. State management across functions for multi-step flows
(Stripe checkout → order creation → escrow hold) is significantly more complex.

### Layered monolith (traditional MVC without module boundaries)

**Rejected.** A layered monolith without enforced domain boundaries degrades into
a big ball of mud as the codebase grows. The listing service reaching directly into
the order repository, the order controller importing the payment service directly —
these patterns make extraction later nearly impossible and testing significantly
harder. Module boundaries are non-negotiable even within the monolith.

---

## Consequences

### Positive

- Single deployable artifact. Simple CI/CD pipeline. One Docker image.
- Database transactions span the full order lifecycle cleanly.
- `docker compose up` gives a running development environment immediately.
- Module boundaries enforce clean architecture without distributed overhead.
- Future extraction of high-load modules into services is possible with bounded
  coupling already established.
- Easier to reason about, test, and debug for a small team or single engineer.

### Negative

- The entire application deploys as one unit. A bug in the messaging module
  requires redeploying the listing and order modules too. Acceptable at this scale.
- All modules share the same database. If the listing search query causes a table
  lock, order processing is affected. Mitigated with proper indexing and query
  discipline, and by the search module using a read replica when load justifies it.
- No independent scaling of hot modules. If listing search becomes the bottleneck,
  the entire application must scale horizontally. Acceptable until transaction volume
  justifies extraction.

---

## Revisit Criteria

This decision should be revisited when **any two** of the following are true:

- The engineering team grows beyond five people working on overlapping modules
- A specific module (likely search or order processing) has measurably different
  resource utilisation than the rest of the system under real production load
- Deployment frequency is constrained by the monolith — i.e. a team cannot ship
  changes to messaging without risk to the order flow
- Transaction volume exceeds 10,000 orders per day sustained

Until these conditions are met, this decision stands.

---

## References

- Martin Fowler — [Monolith First](https://martinfowler.com/bliki/MonolithFirst.html)
- Martin Fowler — [Microservice Premium](https://martinfowler.com/bliki/MicroservicePremium.html)
- Sam Newman — *Building Microservices*, Chapter 1: Just Enough Microservices
- ADR-002: Shared Schema Multi-Tenancy (related decision)
- ADR-003: PostgreSQL as Primary Database (related decision)
