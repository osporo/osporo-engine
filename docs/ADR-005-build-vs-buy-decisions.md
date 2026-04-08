# ADR-005: Build vs Buy Decisions

**Status:** Accepted  
**Date:** 2026-03-12  
**Author:** Wahina Prince Chabalala  
**Deciders:** Wahina Prince Chabalala  

---

## Context

Osporo is built by a single engineer. Every capability the platform requires is
either built from scratch, assembled from open source libraries, or purchased as
a managed service. The choice between these options for each capability is a
resource allocation decision — engineering time spent integrating a third-party
service is time not spent building marketplace domain logic, and vice versa.

The principle governing these decisions is straightforward: **build what
differentiates Osporo, buy what does not.** The marketplace domain logic — listing
types, order lifecycle, escrow behaviour, multi-tenancy — is Osporo's competitive
differentiator. Infrastructure capabilities that every SaaS product needs are
not. Paying for solved problems to protect time for unsolved ones is not a
compromise; it is correct prioritisation.

This ADR documents the build vs buy position for every significant capability
Osporo requires, and the reasoning behind each.

---

## Decision

The following table summarises the position for each capability area:

| Capability | Decision | Provider / Approach |
|---|---|---|
| Payment processing | Buy | Stripe |
| Marketplace split payments | Buy | Stripe Connect |
| Transactional email | Buy | Resend or SendGrid |
| Object storage | Buy | AWS S3 or S3-compatible |
| Error tracking | Buy | Sentry |
| Application monitoring | Buy | Managed (Grafana Cloud / Datadog) |
| Full-text search (MVP) | Build | PostgreSQL tsvector |
| Full-text search (scale) | Buy | Elasticsearch (deferred) |
| Authentication framework | Build on OSS | Spring Security + JJWT |
| Database | Self-hosted OSS | PostgreSQL |
| Background jobs | Build | Spring @Scheduled / Quartz |
| Real-time messaging | Build (deferred) | WebSocket, post-MVP |
| Recommendation engine | Buy or build (deferred) | Post-MVP |
| Frontend deployment   | Buy | Vercel                              |
| Engine deployment     | Buy | Railway or Render                   |
| Database (managed)    | Buy | Neon or Railway Postgres            |
| Object storage        | Buy | Cloudflare R2 (S3-compatible)       |
| CI/CD                 | Buy | GitHub Actions                      |

---

## Reasoning By Capability Area

### Payments — Buy (Stripe / Stripe Connect)

Documented in full in ADR-004. Summary: payment processing, split payouts,
KYC, escrow, and PCI compliance are a multi-year build effort with regulatory
implications. Stripe solves all of it. The cost is per-transaction fees, which
are a cost of revenue and acceptable at all realistic early-stage volume levels.

### Transactional Email — Buy (Resend or SendGrid)

Osporo sends transactional emails: order confirmation, seller notification,
message received, account suspension notice. Delivering email reliably at scale
involves IP reputation management, SPF/DKIM/DMARC configuration, bounce
handling, unsubscribe management, and ISP relationship management.

None of this is marketplace-differentiating work. Resend provides a developer-
friendly API with excellent deliverability, simple integration, and pricing that
is negligible at MVP volume. The engineering cost of building reliable email
delivery infrastructure far exceeds the service cost at every stage of Osporo's
growth.

### Object Storage — Buy (AWS S3 or S3-compatible)

Osporo stores listing images and digital goods files. Object storage requires
durable, redundant, geographically distributed file storage with CDN delivery
for low-latency access globally.

Self-hosting file storage on application servers is not acceptable — it creates
a single point of failure, doesn't scale, and ties storage to application
deployment. S3-compatible object storage (AWS S3, Cloudflare R2, MinIO for
self-hosting) is the industry standard. Osporo uses presigned URLs so files
never transit application servers. This is a solved problem with commodity
pricing — there is no justification for building it.

### Error Tracking — Buy (Sentry)

When exceptions occur in production, the engineering response requires a full
stack trace, request context, user context, and frequency data. Building this
observability capability from scratch — capturing exceptions, storing context,
deduplicating, alerting — is a meaningful engineering investment for zero
competitive value.

Sentry's free tier handles Osporo's MVP volume. At any paying customer scale,
the monthly cost is trivial relative to the operational value. This is an
unambiguous buy decision.

### Application Monitoring — Buy (Grafana Cloud or Datadog)

Metrics collection, dashboard creation, and alerting require an observability
platform. Spring Boot Actuator exposes Prometheus-compatible metrics. Grafana
Cloud ingests these with a generous free tier. Running self-hosted Prometheus
and Grafana adds infrastructure complexity for a capability that is not
marketplace-differentiating. Buy until the cost justifies self-hosting.

### Full-Text Search MVP — Build (PostgreSQL tsvector)

The MVP requires keyword search over listing titles and descriptions.
PostgreSQL's full-text search with a GIN index handles this without introducing
a separate search service. The search module is abstracted behind an interface.

This is build because the capability is already available in the database Osporo
uses, the integration cost is low, and the operational overhead of running
Elasticsearch for MVP search volume is unjustified. The abstraction means this
decision is reversible.

### Full-Text Search at Scale — Buy (Elasticsearch, deferred)

When listing volume and query complexity exceed what PostgreSQL full-text search
handles well — relevance scoring, fuzzy matching, faceted filtering, synonym
handling — Elasticsearch becomes the correct substitution. This is a deferred
buy decision, not a rejected one. The search interface abstraction in the codebase
exists specifically to make this transition low-cost when the time comes.

### Authentication Framework — Build on OSS (Spring Security + JJWT)

Authentication is not bought as a managed service (Auth0, Clerk, Firebase Auth)
for a specific reason: Osporo is an infrastructure product used by developers
and agencies building marketplace products for their clients. A dependency on a
third-party auth service in the open source engine means every self-hosting
developer also has a dependency on that service — and must have an account,
manage pricing, and trust a third party with their users' identity data.

Spring Security with JWT is the correct choice. It is battle-tested, runs
entirely within the engine, adds no external dependency for self-hosters, and
handles Osporo's auth requirements (roles, JWT, route protection) without
managed service overhead. The integration complexity is well-documented and
well-understood.

### Background Jobs — Build (Spring @Scheduled / Quartz)

Osporo requires background jobs: escrow auto-release, listing expiry, fraud
flagging. These are simple scheduled tasks — not complex workflow orchestration.
Spring's `@Scheduled` annotation handles single-instance scheduling adequately
for MVP. Quartz adds persistent job state and cluster-safe execution when
horizontal scaling requires it. Neither requires an external service. A managed
job queue (Temporal, Inngest, Trigger.dev) would introduce an external dependency
for work that Spring handles natively.

### Frontend Deployment — Buy (Vercel)

osporo-frontend is a Next.js application. Vercel was built for Next.js —
it handles SSR, edge functions, preview deployments, and CDN distribution
without configuration. The free tier covers MVP traffic with no monthly
cost. This is the correct deployment target for the reference frontend
and for SaaS tenants using the hosted UI.

### Engine Deployment — Buy (Railway or Render)

osporo-engine is a Spring Boot JVM application. Vercel is not a viable
deployment target for JVM workloads — it is built for serverless
functions and Node.js, not long-running servlet containers. Railway and
Render both support Docker-based JVM deployments natively, have free
tiers suitable for MVP scale, and are operationally simpler than AWS at
this stage. Both produce a deployment story for self-hosters that is
closer to "run this Docker image" than "configure these AWS services."
AWS remains the correct target when transaction volume justifies its
operational complexity. That threshold has not been reached.

### Database — Buy (Neon or Railway Postgres)

Managed Postgres with a free tier. Neon's serverless Postgres includes
database branching — each pull request can get its own isolated database
branch, which makes integration testing and schema migration previews
significantly safer. Railway Postgres is simpler and co-located with the
engine deployment if Railway is used for compute. Both are standard
PostgreSQL that Flyway migrates against without modification. Neither
creates a self-hosting documentation problem — the self-hosting guide
points to any managed Postgres or a local Docker container, not to a
specific provider.

The reason Supabase is not the primary recommendation despite its
generous free tier is that Supabase bundles auth, storage, and realtime
features that Osporo deliberately builds itself. Using Supabase Postgres
risks the implicit pressure to adopt Supabase's other services over time,
which would undermine the engine's self-contained architecture. Neon or
Railway Postgres gives you managed Postgres without that pull.

### Object Storage — Buy (Cloudflare R2)

Cloudflare R2 is S3-compatible — the same AWS SDK and presigned URL
pattern works without code changes. Its free tier is permanent (10GB
storage, 1 million operations per month) rather than time-limited like
AWS's 12-month free tier. For self-hosters, MinIO provides a fully free
S3-compatible alternative that runs in Docker alongside the engine.
The storage module is already abstracted behind the S3 interface — no
code changes are required to switch between R2, S3, or MinIO.

### CI/CD — Buy (GitHub Actions)

GitHub Actions is free for public repositories. osporo-engine and
osporo-frontend are public. Every pull request triggers build, lint, and
test automatically at zero cost. No justification required.

---

## The Principle Restated

Osporo's competitive advantage is its marketplace domain logic — the engine that
handles listings, orders, escrow, multi-tenancy, and payment flows better than
anything a developer would build themselves. Every hour spent on email
deliverability infrastructure, exception aggregation tooling, or object storage
replication is an hour not spent improving that domain logic.

Buy the solved problems. Build the differentiated ones.

---

## Consequences

### Positive

- Engineering time is concentrated on marketplace domain logic.
- Third-party services carry their own operational responsibility.
- Many bought services have generous free tiers that make MVP infrastructure
  cost negligible.
- Bought services are typically more reliable than self-built equivalents at
  early stage.

### Negative

- External service dependencies introduce vendor risk. Mitigated by
  choosing services with strong market position and by abstracting
  integrations behind interfaces where practical.
- Railway and Render have less operational flexibility than AWS at high
  scale. Accepted — the migration path to AWS or GCP exists and the
  Docker-based deployment makes it low-friction when the time comes.
- Per-unit costs for bought services (Stripe fees, email volume pricing)
  become significant at high scale. Accepted — these are costs of
  revenue tied to transaction volume, not fixed costs, and the volume
  that makes them significant implies the revenue to absorb them.
- Every bought service requires a developer deploying Osporo to create
  an account. Mitigated by ensuring all bought services either have a
  meaningful permanent free tier or are configurable — no mandatory
  paid dependency exists in the default self-hosting path.

---

## Revisit Criteria

Individual buy decisions should be revisited when the cost of the service
materially exceeds the estimated engineering cost to build and operate an
equivalent capability reliably. Specific triggers:

- Railway or Render deployment costs exceed what a self-managed VPS or
  AWS deployment would cost at the same traffic level
- Neon or Railway Postgres pricing at production volume justifies a
  dedicated database server or AWS RDS
- A self-hosting friction report identifies a specific bought service
  as a barrier to adoption that a free alternative would resolve

The general principle — build what differentiates, buy what does not —
does not need revisiting.

---

## References

- ADR-004: Stripe Connect for Payments and Escrow
- ADR-001: Modular Monolith over Microservices (search abstraction enabling
  Elasticsearch substitution)
