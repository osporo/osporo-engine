# Osporo API Standards

Last reviewed against the repository: 2026-10-17.

This is the maintained reference for Osporo Engine API conventions. It combines the original **Osporo API Standards v1.0** Word document with the current controllers, DTOs, security configuration, services, migrations, and tests. Maintain this file alongside API changes rather than treating the original document as a frozen specification.

**Current contract** describes behavior present in the source; linked tests provide evidence where available, not a claim that every behavior has test coverage. **Convention for new work** carries forward design guidance without claiming it is implemented. **Gap** identifies missing or inconsistent behavior. Source and tests determine what is actually implemented; a gap is not authorization to redesign or change a published contract.

Read [the project vision](PROJECT_VISION.md) and applicable [architecture decisions](adrs/) alongside this reference, particularly [shared-schema tenancy](adrs/ADR-002-shared-schema-multi-tenancy.md), [permission-based authorization](adrs/ADR-008-permission-based-auth-with-tenant-configurable-roles.md), [tenant-scoped identity](adrs/ADR-009-tenant-scoped-user-identity.md), and [future authentication providers](adrs/ADR-012-pluggable-authentication-providers.md).

## 1. API surface and versioning

**Current contract:** business API routes use `/v1`. The implemented business controller is `AuthController`; `/ping` is an unversioned diagnostic endpoint returning the plain string `pong`.

| Method | Path | Request body | Success | Tenant source |
| --- | --- | --- | --- | --- |
| POST | `/v1/auth/register` | `email`, `password` | 201; `data` contains `id`, `email`, `roles` | Request tenant context |
| POST | `/v1/auth/login` | `email`, `password` | 200; token pair | Request tenant context |
| POST | `/v1/auth/refresh` | `refresh_token` | 200; new token pair | Stored refresh token |
| POST | `/v1/auth/logout` | `refresh_token` | 204; no body | Refresh token lookup |
| GET | `/ping` | None | 200; `pong` | None |

The four auth routes permit requests without an access token. Security matchers also permit selected listing, category, user, Stripe webhook, documentation, and health paths. A matcher is not an endpoint implementation: there are no corresponding domain controllers for listings, categories, orders, payments, messaging, or moderation in the reviewed source. Likewise, migrations and permission constants do not establish endpoint availability.

**Convention for new work:** keep the version in the URL prefix. Additive, optional fields and new endpoints can remain in the current version when they preserve existing behavior. Removing fields, changing semantics, or introducing required inputs requires an explicit compatibility decision and normally a new prefix such as `/v2`. Document a deprecation window before retiring a supported version; no fixed window is currently defined.

Deployments supply their own origin. `https://api.osporo.dev/v1/` in the original document is an illustrative hosted address, not a verified deployment or a required self-hosted hostname. Tenant identifiers are not part of current route paths.

Evidence: [AuthController](../src/main/java/com/osporo/engine/auth/AuthController.java), [application entry point and ping](../src/main/java/com/osporo/engine/OsporoEngineApplication.java), [security configuration](../src/main/java/com/osporo/engine/shared/config/OsporoSecurityConfig.java).

## 2. Tenant resolution and isolation

**Current contract:** `JwtAuthenticationFilter` populates `TenantContextHolder` for each request and clears it in `finally`.

- With a valid `Authorization: Bearer <access_token>`, tenant context comes from the signed `tenant_id` claim. `X-Tenant-ID` does not override it, and a differing header is not explicitly rejected.
- Without a header starting with `Bearer `, the filter accepts a UUID in `X-Tenant-ID`. This is how unauthenticated registration and login select a tenant. An absent or malformed tenant header leaves context unset; services that require it receive 400 with `TENANT_CONTEXT_MISSING`.
- With a rejected Bearer token, the filter clears authentication and does not fall back to `X-Tenant-ID`. It continues the filter chain rather than writing a standardized error itself.
- Refresh resolves the user by the tenant and user IDs stored with the refresh token. Logout looks up the supplied refresh token. Neither flow requires an `X-Tenant-ID` header or compares that header with the stored token's tenant.

An unauthenticated tenant header selects a tenant; it does not establish identity, membership, ownership, or permission. For authenticated domain work, obtain tenant context from the validated principal/context rather than trusting a body, query, path, or header override. Tenant-owned resource lookups and mutations must apply tenant scope and ownership checks server-side.

User identities belong to one tenant. The database enforces email uniqueness on `(tenant_id, email)`; registration and login use tenant-scoped repository methods. Do not introduce a shared cross-tenant account implicitly.

**Gaps:** the original document's JWT-only tenant rule does not describe unauthenticated auth flows. There is no gateway tenant-mismatch rejection or automatic tenant predicate applied to every repository query. For example, `UserService.getUser` uses an unscoped ID lookup; it is not currently exposed by a user controller. New workflows must not assume every service or repository is already isolated.

Evidence: [JWT filter](../src/main/java/com/osporo/engine/shared/security/JwtAuthenticationFilter.java), [tenant context](../src/main/java/com/osporo/engine/shared/security/TenantContextHolder.java), [auth service](../src/main/java/com/osporo/engine/auth/AuthService.java), [user service](../src/main/java/com/osporo/engine/user/UserService.java), [users migration](../src/main/resources/db/migration/V2__create_users.sql).

## 3. Authentication and token lifecycle

**Current contract:** Spring Security uses stateless sessions with form login, HTTP Basic, and anonymous authentication disabled. Other than explicitly permitted routes, requests require authentication. Method security is enabled.

Access tokens are signed JWTs containing `sub` (user UUID), `tenant_id`, `roles`, `permissions`, `iat`, and `exp`. The filter validates the signature and expiry and maps permission strings to Spring authorities. It does not reload the user, tenant status, or permissions from the database on every authenticated request.

Token lifetimes are configured through `JWT_ACCESS_TOKEN_EXPIRY` and `JWT_REFRESH_TOKEN_EXPIRY`, both in **seconds**. There are no application-property defaults. Tests configure 900 seconds and 2,592,000 seconds (15 minutes and 30 days); those values are examples, not guaranteed deployment settings. `expires_in` reports the configured access-token lifetime in seconds.

Refresh tokens are opaque, randomly generated URL-safe values, stored with a user, tenant, expiry, and invalidation flag. Refresh rejects missing, expired, or invalidated tokens and suspended users, invalidates the old token, and issues a new pair. Logout invalidates a matching refresh token and silently succeeds when no token matches. Logout does not revoke an already issued access token.

Registration checks that the tenant exists and is active. Login checks credentials and user suspension; refresh checks user suspension. Login and refresh do not currently recheck tenant status. Permissions are resolved from the union of the user's tenant role bundles when issuing tokens, so role changes do not immediately alter existing access tokens.

External identity providers remain a direction in ADR-012, not current API support. Automatic suspicious-activity revocation and guarantees about concurrent refresh-token reuse are not established by the current implementation or tests.

Evidence: [JWT service](../src/main/java/com/osporo/engine/auth/JwtService.java), [auth service](../src/main/java/com/osporo/engine/auth/AuthService.java), [application properties](../src/main/resources/application.properties), [refresh-token migration](../src/main/resources/db/migration/V10__create_refresh_tokens.sql).

## 4. Permissions and public access

**Convention for new work:** authorize operations by permission, for example `@PreAuthorize("hasAuthority('LISTING_CREATE')")`, then enforce tenant scope and resource ownership in the service/repository workflow. A permission ending in `_OWN` requires an ownership check; an `_ANY` tenant permission does not grant cross-tenant access. Role labels are not substitutes for permission checks.

The authoritative enum is `com.osporo.engine.shared.enums.Permission`, not the package named in the original Word document.

| Area | Defined constants |
| --- | --- |
| Listings | `LISTING_CREATE`, `LISTING_READ`, `LISTING_UPDATE_OWN`, `LISTING_DELETE_OWN`, `LISTING_TAKEDOWN_ANY` |
| Categories | `CATEGORY_CREATE`, `CATEGORY_EDIT`, `CATEGORY_DELETE` |
| Orders | `ORDER_CREATE`, `ORDER_READ_OWN`, `ORDER_READ_ANY`, `ORDER_REFUND` |
| Messages | `MESSAGE_CREATE`, `MESSAGE_READ_OWN` |
| Moderation | `MODERATION_REVIEW`, `MODERATION_CONFIG`, `REPORT_CREATE` |
| Users | `USER_READ_ANY`, `USER_SUSPEND`, `USER_INVITE` |
| Platform | `PLATFORM_ADMIN` |

**Current contract:** permission resolution and conversion to authorities exist, but current auth methods do not use `@PreAuthorize`. There is no role-management API. V12 seeds role bundles for the selected default tenant, not automatically for every future tenant. Its staff bundle includes `ORDER_READ_ANY`, `USER_READ_ANY`, and `MODERATION_REVIEW`, differing from the original document's permission table.

**Security requirement:** ordinary tenant configuration must never grant platform administration. The current model includes an `OSPORO_ADMIN` role bundle containing `PLATFORM_ADMIN` in the seed tenant, and token issuance unions configured permissions without a separate platform-permission boundary. Do not describe platform separation or tenant role-editing safeguards as implemented; establish that boundary before exposing tenant-controlled role changes.

Public endpoints must be explicitly permitted in the security chain and designed to handle absent identity and resolve tenant scope safely. Omitting `@PreAuthorize` alone does not make a route public. No guest token is implemented.

Evidence: [permission enum](../src/main/java/com/osporo/engine/shared/enums/Permission.java), [role seed](../src/main/resources/db/migration/V12__seed_tenant_role_config.sql), [principal](../src/main/java/com/osporo/engine/shared/security/OsporoPrincipal.java).

## 5. JSON naming and representations

**Convention for new work:** use lowercase, hyphen-separated plural resource paths, natural ownership nesting, and action subpaths where needed: `/v1/tenant-settings`, `/v1/listings/{id}/images`, `/v1/orders/{id}/ship`. These examples do not imply implemented routes.

The original JSON convention is `snake_case`. Current token DTOs explicitly use `access_token`, `refresh_token`, and `expires_in`, but shared metadata uses `requestId`, `perPage`, and `totalPages`. No global snake-case configuration is present. Preserve existing wire names until an explicit compatibility change is made; use explicit DTO mappings for new snake-case fields.

Carry forward these representation conventions when introducing the relevant fields:

- UUIDs are JSON strings.
- Timestamps use ISO 8601 with an explicit offset, preferably UTC, such as `2026-10-17T10:30:00Z`.
- Monetary values use decimal strings with an explicit currency, such as `"price": "250.00", "currency": "ZAR"`; no financial endpoint currently establishes this contract.
- Nullable response fields remain present with `null` rather than disappearing. Current success metadata includes a nullable `pagination` component; no global null-omission policy is configured.
- Boolean fields use positive meanings, such as `is_active` and `is_flagged`.

Use API DTOs rather than exposing persistence entities. Validate input at the controller boundary with Jakarta validation and enforce business and security rules in services. Current registration and login require a nonblank valid email and a nonblank password of at least eight characters. Refresh and logout require a nonblank `refresh_token`.

Evidence: [auth DTOs](../src/main/java/com/osporo/engine/auth/dto/), [auth mapper](../src/main/java/com/osporo/engine/auth/AuthMapper.java), [response types](../src/main/java/com/osporo/engine/shared/response/).

## 6. Success responses and pagination

**Current contract:** auth success responses with bodies use `ApiResponse<T>` containing `data` and `meta`. A token response has this shape; values are illustrative:

```json
{
  "data": {
    "access_token": "<jwt>",
    "refresh_token": "<opaque-token>",
    "expires_in": 900
  },
  "meta": {
    "requestId": "550e8400-e29b-41d4-a716-446655440000",
    "pagination": null
  }
}
```

204 responses have no body or envelope. `/ping` is also an explicit exception to the JSON envelope.

`Meta.paginated(Page<?>)` exists and converts Spring's zero-based page index to a one-based `page`. Its pagination fields are `page`, `perPage`, `total`, and `totalPages`. There is no implemented public list controller using it, so public query defaults and bounds are not yet enforced.

**Convention for new list endpoints:** use offset pagination with `page=1`, `per_page=20`, `page >= 1`, and `1 <= per_page <= 100`. Accept only endpoint-approved `sort` fields and `order=asc|desc`, defaulting to `desc` unless documented otherwise. Return the items in `data` and totals in `meta.pagination`; define deterministic ordering. Map these parameters explicitly rather than assuming Spring Pageable's defaults match. Cursor pagination remains deferred.

Evidence: [ApiResponse](../src/main/java/com/osporo/engine/shared/response/ApiResponse.java), [Meta](../src/main/java/com/osporo/engine/shared/response/Meta.java), [PaginationMeta](../src/main/java/com/osporo/engine/shared/response/PaginationMeta.java).

## 7. Errors and HTTP semantics

**Current contract:** controller advice returns a separate `error` envelope. Clients branch on stable `code` values, not human-readable `message` text. Non-validation errors have an empty `details` array. A validation response is:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "The request contains invalid fields.",
    "details": [
      { "field": "email", "message": "must be a well-formed email address" }
    ]
  }
}
```

Validation message wording may vary. Error responses currently contain no `request_id` or `requestId`. `OsporoException` supplies the status and code, Jakarta body validation failures return 400, advice-handled access denial returns 403, and unexpected exceptions return 500 without a client stack trace. The current generic message contains a literal `{}` suffix; do not treat that wording as a stable contract.

| Status | Current use or convention |
| --- | --- |
| 200 | Login and refresh; convention for successful reads and updates |
| 201 | Registration; convention for resource creation |
| 204 | Logout; convention for successful deletion, always without a body |
| 400 | Validation failures and missing tenant context; convention for malformed inputs |
| 401 | `AUTHENTICATION_FAILED`, `INVALID_TOKEN`; convention for missing/invalid authentication |
| 403 | `ACCOUNT_SUSPENDED`, `TENANT_SUSPENDED`, advice-handled `INSUFFICIENT_PERMISSIONS` |
| 404 | `TENANT_NOT_FOUND`; `LISTING_NOT_FOUND` exception exists without a listing endpoint |
| 409 | `EMAIL_ALREADY_EXISTS`; convention for conflicts with current resource state |
| 422 | Convention for structurally valid requests violating a business rule; no current handler establishes a workflow using it |
| 429 | Deferred rate-limit behavior; enum value alone does not enforce a limit |
| 500 | Unexpected exceptions |

For new endpoints, keep 409 for resource-state conflicts and 422 for other business-rule failures. The original document used an already-sold listing for both; define one status per concrete error and test it. Tenant-owned resources outside the caller's scope should return 404 without revealing another tenant's resource.

**Gaps:** no dedicated JSON authentication entry point or access-denied handler is configured for the security filter chain. Its failures do not necessarily pass through controller advice or produce this envelope; protected-route 401 behavior is not established by current tests. Malformed JSON, type conversion, and routing failures lack dedicated advice mappings and must not be assumed to yield the intended 400/404 contract; the broad exception handler can classify otherwise unhandled MVC exceptions as 500.

Evidence: [global exception handler](../src/main/java/com/osporo/engine/shared/exception/GlobalExceptionHandler.java), [error envelope](../src/main/java/com/osporo/engine/shared/response/ErrorEnvelope.java), [error codes](../src/main/java/com/osporo/engine/shared/enums/ErrorCode.java), [exception types](../src/main/java/com/osporo/engine/shared/exception/).

## 8. HTTP methods and retries

**Convention for new work:** GET reads without mutation; POST creates or triggers an action; PUT replaces a complete representation; PATCH updates selected fields; DELETE removes a resource and returns 204. GET, PUT, and DELETE should have idempotent semantics. PATCH is not inherently idempotent: define and test retry behavior for each operation rather than promising it for every patch.

**Deferred idempotency convention:** financial operations such as order creation, refunds, and checkout should require a client-generated UUID in `Idempotency-Key`. The original design retains the response for 24 hours, replays duplicates without executing again, expires the key afterward, and returns 400 for a missing required key.

There is no implementation of this mechanism or these financial routes. Before exposing them, define tenant/caller and operation scoping, behavior for a reused key with a different payload, concurrent duplicate requests, and atomicity with the business operation. The original retention rule alone does not resolve those decisions. Do not claim an arbitrary POST is safe to retry today.

## 9. Tracing and rate limiting

**Current contract:** `Meta.simple()` and `Meta.paginated()` generate a UUID when constructing metadata. There is no shared per-request correlation ID propagated to error bodies, `X-Request-ID`, or logs. A metadata UUID is therefore not yet an end-to-end trace identifier.

**Deferred tracing convention:** generate one server request ID per request, propagate it consistently through success metadata, error bodies, response headers, and logs, and let clients surface it in support flows. Resolve the existing camelCase metadata contract before adopting the Word document's `request_id` spelling. Sentry/Grafana integration is not established by this repository.

**Deferred rate-limit convention:** the original design calls for gateway enforcement, `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`, and 429 responses with `Retry-After`. No limiter, gateway configuration, or tenant-plan limit settings implement this in the reviewed code. The Word document's self-hosted and SaaS plan quotas are historical proposals, not current enforced limits or committed product tiers. Choose quotas, enforcement scope, and storage when that work is approved.

## 10. Verification and maintenance

The [auth integration test source](../src/test/java/com/osporo/engine/auth/AuthServiceIntegrationTest.java) declares `AuthControllerIntegrationTest` and exercises HTTP registration, duplicate email, validation, missing tenant header, login, refresh rotation, and logout invalidation with PostgreSQL Testcontainers. It asserts `meta.requestId` and snake-case token fields. [Auth service unit tests](../src/test/java/com/osporo/engine/auth/AuthServiceTest.java) cover service success and failure paths. [JWT service tests](../src/test/java/com/osporo/engine/auth/JwtServiceTest.java) cover configured access-token lifetime in seconds. These are existing test definitions, not a report of a test run for this document.

Coverage does not yet establish a complete tenant-isolation boundary, authenticated header/token mismatch behavior, filter-level error contracts, platform permission isolation, pagination, tracing, idempotency, or rate limiting. Add relevant unit and integration coverage when implementing or changing those workflows.

For each API change, update this reference and the applicable DTOs, validation, error mappings, security rules, and tests together. Record the path, method, tenant source, authentication and permission requirements, ownership rules, request/response shape, and error codes. Move deferred behavior into the current-contract sections only after implementation and verification. Preserve existing contracts or document an explicit migration/versioning decision. This document does not itself change runtime behavior or commit the project to implementing every historical proposal.
