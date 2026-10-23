# Domain API documentation

This directory contains ten domain API references, initially converted from **Osporo API v1.docx**, covering 71 endpoints. Authentication documents the current implementation and supersedes its original design; users now includes own-profile GET/PATCH and public/full profile reads alongside planned routes. The other domains document planned endpoint contracts and source gaps, with subsequent design revisions identified in each file. Authentication was reviewed on 2026-10-20; planned-domain implementation status was checked on 2026-10-19. The users role catalogue and permission-based profile visibility design were updated on 2026-10-20; own-profile GET was added on 2026-10-22.

## Domains

| Domain | Endpoints | Implementation status |
| --- | --- | --- |
| [Authentication](auth.md) | 4 | Current implementation |
| [Users](users.md) | 8 | 3 profile routes implemented; 5 routes planned |
| [Categories](categories.md) | 6 | Planned |
| [Listings](listings.md) | 15 | Planned |
| [Orders](orders.md) | 10 | Planned |
| [Payments](payments.md) | 4 | Planned |
| [Escrow](escrow.md) | 2 | Planned |
| [Messaging](messaging.md) | 4 | Planned |
| [Moderation](moderation.md) | 7 | Planned |
| [Tenants](tenants.md) | 11 | Planned |

## How to use these references

The original document describes a full API design; it does not establish which features exist. The four auth routes and three user-profile routes are implemented. The remaining 64 routes are planned designs. Security matchers, database migrations, enums, or basic services alone do not expose those APIs. The diagnostic `/ping` route is outside this source's domain inventory.

Read [authentication](auth.md) directly for its implemented contract. For planned domains, read each domain's implementation notes before its source API design. [API standards](../API_STANDARDS.md) describes the current shared contract and overrides conflicting historical conventions; source code and tests establish implemented behavior. [Project vision](../PROJECT_VISION.md) and applicable [ADRs](../adrs/) provide direction. Do not silently resolve an open product or architecture decision by implementing an example literally.

All business routes remain `/v1` until the project owner says otherwise. Rate limiting is outside current scope and is not an API requirement here. Idempotency and tracing described in source examples remain planned where the standards identify them as unimplemented.

## Shared context from the source overview

Osporo is headless, multi-tenant marketplace infrastructure. The source uses a common response envelope so clients can share response handling, with exceptions such as responses without bodies. Success examples generally use `data` and `meta`; errors use `error`. The source's `request_id`, `per_page`, and `total_pages` metadata are design spellings: current shared response records instead use `requestId`, `perPage`, and `totalPages`, and current error bodies omit a request ID. Planned-domain examples retain the design spellings; auth examples use the current wire format.

The overview describes `OSPORO_ADMIN` as responsible for platform tenant management, without access to tenant-specific users, messages, or listings. This is a security design requirement, not a verified platform administration boundary. The document supplies tenant-owner routes but no platform tenant-management API. Ordinary tenant role configuration must never grant platform-wide permissions.

The source writes `https://api.opsoro.com` as its base URL, while the earlier standards document used a different illustrative domain. Neither establishes a deployed service or a required hostname. Use the origin of the actual engine deployment and the `/v1` paths documented here.

Tenant-owned data must be scoped server-side. Authenticated domain requests use the validated tenant context; unauthenticated registration/login select a tenant with `X-Tenant-ID`. The proposed Stripe webhook path carries a tenant identifier and therefore needs provider-verified routing, separately from JWT authentication. Public browsing still needs a safe tenant-selection policy; a public route or a client filter does not establish tenant isolation.

## Conversion and unresolved details

Source instructions are recorded as API design material, not instructions to execute operations or implement features. This documentation change adds no endpoints or infrastructure. The original document remains unchanged.

The initial conversion repaired JSON formatting, a two-role array written as one string, the misspelled hierarchy error, `404 Found`, heading spelling, missing backticks, and a stray tab in a moderation path. Planned-domain source semantics, including disputed status codes and incomplete contracts, are retained and explained in the corresponding domain notes. The auth reference has since been rewritten to match the implemented behavior, including validation, tenant resolution, response fields, errors, and token lifecycle.

Important unresolved details include the order-creation request body, image-upload inputs, category representation and creation status, inconsistent listing filters, order state-machine contradictions, webhook acknowledgement/retry policy, permission-versus-role checks, duplicate moderation-configuration routes, and unspecified error constants. These require implementation-time decisions rather than guessed values.

Maintain the affected domain file and [API standards](../API_STANDARDS.md) whenever a contract changes. Mark a planned route implemented only after checking its controller, service behavior, authorization and tenant isolation, and relevant tests.
