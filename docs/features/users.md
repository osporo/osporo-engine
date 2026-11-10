# Users API

Source: **Osporo API v1.docx**, User Domain. Converted and checked against the repository on 2026-10-19.

Design updated: 2026-10-20. The role catalogue and profile visibility policy below supersede the corresponding tables in the original document.

**Status:** Partially implemented. The four directory and profile routes and both suspension actions are implemented. Invitations and self-deletion remain planned.

Read [API standards](../API_STANDARDS.md) for the current wire contract and shared rules, and the [domain index](README.md) for source interpretation. All routes remain under `/v1`. The endpoint descriptions and examples in **Source API design** document the original design, not proof of availability.

## Implementation notes and open decisions

Phase 1 foundation (2026-10-22): [UserService.getUser](../../src/main/java/com/osporo/engine/user/UserService.java) loads by ID within the current tenant context. Missing and cross-tenant users both raise 404 `PROFILE_NOT_FOUND`; absent tenant context raises 400 `TENANT_CONTEXT_MISSING`. The user entity permits a null display name, matching the database and registration behavior. This loading method does not implement profile visibility or account-status filtering.

Phase 2 adds `GET /v1/users/me` through `UserController`, `UserService.getOwnProfile`, and `UserMapper`. Phase 3 (2026-10-23) adds `PATCH /v1/users/me` with sparse-field validation and transactional updates. Their implemented contracts are described in sections 3 and 7 below. Phase 6 now enforces current account/tenant status on Bearer requests; token versioning remains deferred.

Phase 4 (2026-10-23) adds `GET /v1/users/{id}`, `UserAuthorizationPolicy`, and separate public/full DTOs, implementing section 4 below. It validates the requested tenant and filters target account status. Phase 6 additionally checks the caller before applying target-profile visibility; token versioning remains deferred.

Phase 5 adds `GET /v1/users`, requiring `USER_READ_ANY` and querying the authenticated principal's active tenant through a custom repository fragment. Section 2 below documents role/lifecycle filtering, approved sorting, and pagination. Phase 6 adds authentication-time membership and status checks before directory authorization and validation.

Phase 6 (2026-10-23) also aligns login/refresh checks and adds an internal tenant/user-scoped refresh-session revocation operation for future lifecycle endpoints. Suspended accounts and inactive tenants fail with 403; deleted/missing/mismatched Bearer identities fail with 401. Restoring active status can make an old unexpired access token usable again until token versioning is implemented.

Phase 7 implements account suspension and uses Phase 6 revocation to invalidate the target's refresh sessions in the same transaction as the suspension.

Phase 8 implements lifting a suspension and links the lift audit record to the active suspension when its original record exists.

Open details from the source:

- The [staff invitation plan](#1-invite-users) defines issuance and acceptance for manual delivery in development. Email delivery and invitations for additional owners need separate decisions.
- `USER_READ_PUBLIC` exists in the permission enum and V12 seeds it for `BUYER` and `SELLER`. Login and refresh include it when resolving those seeded bundles. Phase 4 enforces its public-profile scope; it does not grant directory or private-field access.
- Deleting or updating one's own account requires authentication and ownership even where the source says no specific permission. The source does not fully define deleted-account token handling.

Editorial corrections: the list example now represents buyer and seller as two role strings, and `ROLE_HIERARCY_VIOLATION` is corrected to `ROLE_HIERARCHY_VIOLATION`.

## Endpoint index

| Endpoint | Operation |
| --- | --- |
| `POST /v1/users/invite` | [1) Invite users](#1-invite-users) |
| `POST /v1/users/invitations/accept` | [Accept an invitation](#accept-an-invitation) |
| `GET /v1/users` | [2) Get all users (admin level)](#2-get-all-users-admin-level) |
| `GET /v1/users/me` | [3) Get own profile](#3-get-own-profile) |
| `GET /v1/users/{id}` | [4) Get user profile](#4-get-user-profile) |
| `POST /v1/users/{id}/suspend` | [5) Suspend user](#5-suspend-user) |
| `POST /v1/users/{id}/unsuspend` | [6) Unsuspend user](#6-unsuspend-user) |
| `PATCH /v1/users/me` | [7) Update user details](#7-update-user-details) |
| `DELETE /v1/users/me` | [8) Delete user](#8-delete-user) |

## Source API design

## Purpose

- Allow a user to view their own profile as well as other public profiles
- Allow a user to edit their own profile
- Allow a marketplace owner to invite users

## Endpoints

### 1) Invite users

`POST /v1/users/invite`

**Planned.** The first flow lets a marketplace owner invite a new `MARKETPLACE_STAFF` account into their own tenant. The owner delivers a personal token manually in development. Email delivery can later use the same acceptance contract.

#### Authorization and tenant

Require an authenticated `MARKETPLACE_OWNER` with `USER_INVITE`. Use the tenant from the validated principal. The first flow supports exactly one role per invitation, `MARKETPLACE_STAFF`. The tenant must be active and have that role configured. A caller with platform privileges alone cannot issue these tenant invitations.

#### Request

Keep the planned batch request shape. Require a nonempty `invites` array, a valid email per entry, and `roles: ["MARKETPLACE_STAFF"]`. Normalize emails the same way as registration. Reject duplicate normalized emails within the request and validate the entire batch before creating invitations.

```json
{
  "invites": [
    {
      "email": "staff@example.com",
      "roles": ["MARKETPLACE_STAFF"]
    }
  ]
}
```

#### Proposed response (200 OK)

Return one result per recipient in request order. A new invitation has status `CREATED` and returns the raw token once for manual delivery. Use `ALREADY_MEMBER` when the email is already registered in the tenant, including soft-deleted accounts whose email remains reserved. Use `ALREADY_INVITED` when an unexpired, unused invitation already exists for the same tenant and email. Both statuses omit the token and expiry. An expired invitation permits a new one.

```json
{
  "data": {
    "invites": [
      {
        "email": "staff@example.com",
        "roles": ["MARKETPLACE_STAFF"],
        "status": "CREATED",
        "token": "<one-time-invitation-token>",
        "expires_at": "2026-11-12T10:00:00Z"
      }
    ]
  },
  "meta": {
    "requestId": "uuid",
    "pagination": null
  }
}
```

The proposed expiry is 48 hours, carried over from the source example. The original `202 Accepted` and `SENT` example assumed delivery by the engine. Here `200 OK` reports the result of recording the invitations, and the owner handles delivery.

Use the shared error envelope. Return 400 `VALIDATION_ERROR` for invalid input, 401 `INVALID_TOKEN` for missing or invalid authentication, and 403 `INSUFFICIENT_PERMISSIONS` for an authenticated caller without the required authority. A well-formed request for a role outside the allowed staff role returns 422 `ROLE_EXCEEDS_CALLER_PERMISSIONS`.

#### Accept an invitation

`POST /v1/users/invitations/accept`

**Planned.** This route accepts an invitation token as its credential. The recipient submits the token and a password that satisfies the registration password rules. The stored invitation supplies the tenant, normalized email, and role. The recipient sends the request without an `Authorization` header or `X-Tenant-ID`.

```json
{
  "token": "<one-time-invitation-token>",
  "password": "<recipient-chosen-password>"
}
```

Look up the invitation by its token hash. Require an unused, unexpired invitation, an active tenant, and an available staff role. Create the account with a BCrypt password hash and consume the invitation in the same transaction. Return `201 Created` with the new user's `id`, `email`, `roles`, and `tenant_id` in `data`, using the shared response envelope. The recipient can then call the existing login endpoint with that tenant UUID.

The proposed 400 `INVALID_INVITATION` code covers unknown, expired, consumed, or otherwise unusable invitations with a common response. Invalid request fields return 400 `VALIDATION_ERROR`. If the email was registered after issuance, return 409 `EMAIL_ALREADY_EXISTS`. Account creation must preserve tenant-local email uniqueness even if acceptance and public registration race.

#### Implementation and verification plan

1. Add a Flyway migration for tenant-scoped invitations. Store the inviting user's ID, normalized email, assigned role, token digest, creation and expiry timestamps, and when the token was consumed. Use a cryptographically random token and a unique digest. Keep raw tokens out of persisted records and logs.
2. Implement issuance with a database guarantee that overlapping requests for the same tenant and email produce at most one pending invitation. An account in a different tenant does not block issuance.
3. Implement acceptance so concurrent redemptions can create at most one account. Reuse the existing password encoder, email normalization, and account creation rules. Verify that a rollback leaves the invitation redeemable and that a successful commit prevents reuse.
4. In Postman, log in as the seeded owner, issue one staff invitation, and copy its token into the acceptance request. Log in as the new staff user and verify that `GET /v1/users/me` reflects the configured staff permissions. With the seeded permissions, verify that staff can suspend a buyer and that suspending the owner fails the hierarchy check.

Before implementation, agree on a maximum batch size and whether pending invitations stay redeemable when the inviter loses access. The first flow handles new accounts. Role changes for existing accounts, co-owner invitations, and resend or revocation are separate decisions.

### 2) Get all users (admin level)

`GET /v1/users`

**Implemented in Phase 5.** Returns user summaries within the authenticated principal's tenant, requiring `USER_READ_ANY` before validating filters. A conflicting `X-Tenant-ID` cannot change the selected tenant. The tenant must exist and be active. Platform-administrator accounts are excluded even when they also carry tenant roles.

#### Parameters

| Parameter | Default | Constraints | Description |
| --- | --- | --- | --- |
| page | 1 | ≥ 1 | Page number |
| per_page | 20 | 1 – 100 | Results per page |
| sort | CREATED_AT | CREATED_AT, DELETED_AT, UPDATED_AT, SUSPENDED_AT | Field to sort by |
| order | desc | asc \| desc | Sort direction |
| roles | [BUYER, SELLER, MARKETPLACE_STAFF, MARKETPLACE_OWNER] | BUYER, SELLER, MARKETPLACE_STAFF, MARKETPLACE_OWNER | Roles to filter by, provided as a list; OSPORO_ADMIN is a platform role and is excluded from this tenant endpoint |
| status | Active and suspended accounts | ACTIVE, SUSPENDED, DELETED, ALL | Explicit lifecycle filter; omitted status excludes deleted accounts |

The role catalogue is:

| Role | Scope | Valid tenant-directory filter |
| --- | --- | --- |
| `BUYER` | Tenant participant | Yes |
| `SELLER` | Tenant participant | Yes |
| `MARKETPLACE_STAFF` | Tenant operator | Yes |
| `MARKETPLACE_OWNER` | Tenant owner | Yes |
| `OSPORO_ADMIN` | Platform administration | No |

The `roles` filter selects records within the resolved tenant; it never grants access or changes tenant scope. `OSPORO_ADMIN` is a valid engine role, but platform administrators are outside this tenant directory's intended visibility. Requests filtering by it retain the `INVALID_PARAMETER` response described below.

Supply repeated role parameters, for example `GET /v1/users?roles=BUYER&roles=SELLER`. Any requested role may match; a multi-role account appears once. Duplicate role values are harmless. Comma-separated role values, unsupported filters, and repeated values for single-value parameters are rejected with 400 `INVALID_PARAMETER`.

`ACTIVE` means neither deleted nor suspended. `SUSPENDED` excludes deleted accounts; `DELETED` includes deleted accounts regardless of suspension state. `ALL` includes all lifecycle states, with tenant scoping and platform-account exclusion still enforced. Sorting uses the requested timestamp/direction, nulls last, and ascending UUID as a deterministic tie-breaker. Out-of-range pages return an empty list while preserving the filtered total.

#### Permissions

USER_READ_ANY

#### Request

No body

#### Response

```json
{
  "data": [
    {
      "id": "uuid",
      "display_name": "John Doe",
      "email": "john@company.com",
      "roles": [
        "MARKETPLACE_OWNER"
      ],
      "avatar_url": "https://...",
      "created_at": "2026-03-17T00:00:00+02:00"
    },
    {
      "id": "uuid",
      "display_name": "Jane Doe",
      "email": "jane@company.com",
      "roles": [
        "MARKETPLACE_STAFF"
      ],
      "avatar_url": "https://...",
      "created_at": "2026-03-16T00:00:00+02:00"
    },
    {
      "id": "uuid",
      "display_name": "Mark Doe",
      "email": "mark@gmail.com",
      "roles": [
        "BUYER",
        "SELLER"
      ],
      "avatar_url": "https://...",
      "created_at": "2026-03-15T00:00:00+02:00"
    }
  ],
  "meta": {
    "requestId": "uuid",
    "pagination": {
      "page": 1,
      "perPage": 20,
      "total": 143,
      "totalPages": 8
    }
  }
}
```

#### Success

200 OK

#### Error

```json
{
  "error": {
    "code": "INSUFFICIENT_PERMISSIONS",
    "message": "You are not authorized to perform this action.",
    "details": []
  }
}
```

403 Forbidden

Error: INSUFFICIENT_PERMISSIONS

```json
{
  "error": {
    "code": "INVALID_PARAMETER",
    "message": "The roles parameter has an invalid or unsupported value.",
    "details": []
  }
}
```

400 Bad Request

Error: INVALID_PARAMETER

Missing or rejected authentication returns 401 `INVALID_TOKEN`. Missing/mismatched authenticated identities return 401 `INVALID_TOKEN`; inactive tenants return 403 `TENANT_SUSPENDED`. Successful metadata uses `requestId`, `perPage`, and `totalPages`; `filter_roles` is omitted. Errors have no request ID. Summary fields are `id`, `display_name`, `email`, `roles`, `avatar_url`, and `created_at`; nullable profile fields remain present.

### 3) Get own profile

`GET /v1/users/me`

**Implemented in Phase 2.** Returns the authenticated account using the signed principal's user and tenant IDs. It accepts no user selection from headers, query parameters, or a body. Roles come from the current account; permissions come from the access token and describe the authorities enforced for that request. The response contains only the six fields shown below, with nullable display name and avatar fields retained. Phase 6 checks current account membership, deletion/suspension, and tenant activity before authenticated requests reach the controller. Permissions remain the token snapshot; token versioning remains deferred.

#### Parameters

No params

#### Permissions

Authenticated users only (no specific permission required)

#### Request

No body

#### Response

```json
{
  "data": {
    "id": "uuid",
    "display_name": "User Name",
    "email": "user@gmail.com",
    "avatar_url": "https://...",
    "roles": [
      "BUYER",
      "SELLER"
    ],
    "permissions": [
      "LISTING_CREATE",
      "ORDER_CREATE",
      "MESSAGE_CREATE"
    ]
  },
  "meta": {
    "requestId": "uuid",
    "pagination": null
  }
}
```

#### Success

200 OK

#### Error

```json
{
  "error": {
    "code": "INVALID_TOKEN",
    "message": "Authentication is required. Supply a valid access token.",
    "details": []
  }
}
```

401 Unauthorized

Error: INVALID_TOKEN

Missing, malformed, or expired access tokens return 401 `INVALID_TOKEN` with `WWW-Authenticate: Bearer`. A missing/deleted account or a token user/tenant pair that matches no account returns 401 `INVALID_TOKEN`. Suspended callers return 403 `ACCOUNT_SUSPENDED`; inactive tenants return 403 `TENANT_SUSPENDED`. Errors use `{error: {code, message, details}}` without a request ID.

### 4) Get user profile

`GET /v1/users/{id}`

**Implemented in Phase 4.** Returns a public or full profile according to the caller's permissions and the target's tenant and profile category. Guests can view public seller profiles. `USER_READ_PUBLIC` allows authenticated callers to view public buyer and seller profiles without gaining directory access or private profile fields. `USER_READ_ANY` allows full profiles within the tenant, including suspended accounts. Deleted and platform-administrator targets are always hidden.

#### Parameters

| Parameter | Location | Requirement |
| --- | --- | --- |
| `id` | Path | Required user UUID within the resolved tenant |

#### Permissions

Visibility is enforced in the service after resolving and validating tenant scope. Guests supply `X-Tenant-ID`; authenticated requests use the signed principal's tenant. The tenant must exist and be active. The `id` path parameter must be a UUID; malformed values return 400 `INVALID_PARAMETER`.

| Caller access | Visible targets within the tenant | Representation |
| --- | --- | --- |
| Guest, or authenticated caller without either read permission | Public seller profiles only | Public profile |
| Authenticated with `USER_READ_PUBLIC` | Buyer and seller profiles | Public profile |
| Authenticated with `USER_READ_ANY` | Nondeleted tenant profiles, including suspended accounts; platform administrators excluded | Full profile |
| `PLATFORM_ADMIN` alone | No additional tenant-profile access | No tenant-data override |

`USER_READ_PUBLIC` is a narrower permission. Its intended scope does not authorize `GET /v1/users`, return email or administrative fields, expose staff/owner-only profiles, or grant cross-tenant access. V12 seeds it in the default `BUYER` and `SELLER` bundles; the default `MARKETPLACE_STAFF` and `MARKETPLACE_OWNER` bundles retain `USER_READ_ANY`. These are default bundles, not hardcoded checks on the caller's role name. Guests have no token or assigned permission; their seller access is enforced as an explicit public-route policy.

The target's `BUYER` or `SELLER` role classifies a participant profile; it does not authorize the caller. Mixed operator/participant accounts may expose the corresponding public participant projection: mixed seller accounts are public to guests, and mixed buyer accounts require `USER_READ_PUBLIC`. This never exposes operator roles or private fields. Platform-administrator profiles are excluded even if they also carry tenant roles. Without `USER_READ_ANY`, suspended targets are hidden regardless of participant roles.

Apply the strongest applicable read permission: `USER_READ_ANY` takes precedence over `USER_READ_PUBLIC`. Missing, out-of-tenant, or visibility-restricted targets return 404 `PROFILE_NOT_FOUND` rather than revealing whether a hidden profile exists. The own-profile endpoint `GET /v1/users/me` remains authenticated self-access without either permission. A public security matcher alone does not enforce these rules, and tenant scope must also be resolved safely for guest requests.

#### Request

No body

#### Response

Callers with `USER_READ_ANY` receive the full profile response for visible tenant targets. Callers allowed by the guest policy or `USER_READ_PUBLIC` receive only the public profile response. All other targets return 404; there is no fallback that exposes a hidden profile.

Public profile

```json
{
  "data": {
    "id": "uuid",
    "display_name": "John Doe",
    "avatar_url": "https://...",
    "seller_rating": null,
    "member_since": "2026-03-12T00:00:00+02:00"
  },
  "meta": {
    "requestId": "uuid",
    "pagination": null
  }
}
```

Full profile

```json
{
  "data": {
    "id": "uuid",
    "display_name": "John Doe",
    "email": "john@gmail.com",
    "avatar_url": "https://...",
    "roles": [
      "SELLER",
      "BUYER"
    ],
    "status": "ACTIVE",
    "seller_rating": null,
    "created_at": "2026-03-12T00:00:00+02:00",
    "suspended_at": null
  },
  "meta": {
    "requestId": "uuid",
    "pagination": null
  }
}
```

#### Success

200 OK

#### Error

```json
{
  "error": {
    "code": "PROFILE_NOT_FOUND",
    "message": "The requested profile was not found",
    "details": []
  }
}
```

404 Not Found

Error: PROFILE_NOT_FOUND

For guests, tenant errors are 400 `TENANT_CONTEXT_MISSING` for missing/malformed guest tenant context, 404 `TENANT_NOT_FOUND` for an unknown tenant, and 403 `TENANT_SUSPENDED` for an inactive tenant. Seller ratings remain null until the ratings domain exists. Public and full DTOs exclude password hashes, provider identifiers, and token internals.

### 5) Suspend user

`POST /v1/users/{id}/suspend`

**Implemented in Phase 7.** A caller with `USER_SUSPEND` can suspend a strictly lower-ranked account in their own tenant. The validated principal supplies the actor and tenant IDs. A conflicting `X-Tenant-ID` does not change the tenant. The operation records a `SUSPEND` audit entry in `suspensions`, sets the target's `suspended_at` and `updated_at`, and invalidates the target's refresh sessions in one transaction. Existing access tokens are rejected while the account is suspended.

#### Suspension hierarchy

| Rank, highest first | Roles |
| --- | --- |
| Platform administrator | `OSPORO_ADMIN` |
| Marketplace administrator | `MARKETPLACE_OWNER` |
| Marketplace staff | `MARKETPLACE_STAFF` |
| Marketplace participant | `BUYER`, `SELLER` |

Use the highest currently assigned role on each account. An account with no roles is treated as participant rank. For example, a caller with `MARKETPLACE_STAFF` and `SELLER` ranks as staff. They can suspend a buyer or seller if they have `USER_SUSPEND`, but a target with both `MARKETPLACE_STAFF` and `BUYER` ranks as staff and is therefore a peer. Buyer and seller have equal rank. Equal ranks also prevent self-suspension.

The hierarchy sets the rank boundary. Permission to suspend still comes from `USER_SUSPEND`. The top rank protects `OSPORO_ADMIN` accounts from tenant operators and does not grant cross-tenant access or platform moderation powers through this endpoint. The same hierarchy applies to lifting a suspension.

#### Request

Use the target's UUID as `id`. `violation_type` is required and must be a nonblank string of at most 500 characters after trimming. `details` is optional and must be a string or null. Omitted or null details are stored as an empty string. Neither field accepts NUL characters.

```json
{
  "violation_type": "PAYMENT_FRAUD",
  "details": "User attempted to move payment off of the platform."
}
```

#### Response (200 OK)

```json
{
  "data": {
    "id": "uuid",
    "user_id": "uuid",
    "actor_id": "uuid",
    "action": "SUSPEND",
    "violation_type": "PAYMENT_FRAUD",
    "details": "User attempted to move payment off of the platform.",
    "suspended_at": "2026-03-17T07:14:45Z"
  },
  "meta": {
    "requestId": "uuid",
    "pagination": null
  }
}
```

#### Errors

| Status | Code | Condition |
| --- | --- | --- |
| 400 | `INVALID_PARAMETER` | Malformed target UUID |
| 400 | `VALIDATION_ERROR` | Missing, unreadable, or invalid body |
| 401 | `INVALID_TOKEN` | Missing or invalid Bearer authentication |
| 403 | `INSUFFICIENT_PERMISSIONS` | Caller lacks `USER_SUSPEND` |
| 403 | `ACCOUNT_SUSPENDED`, `TENANT_SUSPENDED` | Caller account or tenant is suspended |
| 404 | `PROFILE_NOT_FOUND` | Target is missing, cross-tenant, deleted, or a platform administrator |
| 409 | `USER_ALREADY_SUSPENDED` | An eligible target is already suspended |
| 422 | `ROLE_HIERARCHY_VIOLATION` | Target is a peer or has higher rank |

A repeated request against a suspended target returns 409 without creating a second audit record. This follows the API standard for state conflicts. Errors use the current shared envelope.

### 6) Unsuspend user

`POST /v1/users/{id}/unsuspend`

**Implemented in Phase 8.** Uses the same [suspension hierarchy](#suspension-hierarchy), tenant boundary, and `USER_SUSPEND` permission as suspension. The operation records a `LIFT` audit entry, clears `suspended_at`, updates `updated_at`, and invalidates any remaining refresh sessions for the target in one transaction. The response uses the action name `UPLIFT`.

The lift links to the target's `SUSPEND` record matching the active `suspended_at` timestamp. If the account was suspended without an audit record, the lift still succeeds and returns `suspension: null`. The lift records its own details and date. Its violation type is available through the linked suspension, when present.

Revoked refresh tokens remain invalid. The restored account can sign in again. An existing unexpired access token can also work again because access-token versioning is deferred.

#### Request

Use the target's UUID as `id`. The body and `details` are optional. Omitted or null details are stored as an empty string. The `details` field binds to a Java `String` using the application's usual Jackson coercion rules. It cannot contain NUL characters.

```json
{
  "details": "Appeal approved"
}
```

#### Response (200 OK)

```json
{
  "data": {
    "id": "uuid",
    "user_id": "uuid",
    "actor_id": "uuid",
    "action": "UPLIFT",
    "details": "Appeal approved",
    "action_date": "2026-03-18T08:13:32Z",
    "suspension": {
      "id": "uuid",
      "actor_id": "uuid",
      "violation_type": "PAYMENT_FRAUD",
      "details": "User attempted to move payment off of the platform.",
      "action_date": "2026-03-17T07:14:45Z"
    }
  },
  "meta": {
    "requestId": "uuid",
    "pagination": null
  }
}
```

#### Errors

| Status | Code | Condition |
| --- | --- | --- |
| 400 | `INVALID_PARAMETER` | Malformed target UUID |
| 400 | `VALIDATION_ERROR` | Unreadable body or invalid details |
| 401 | `INVALID_TOKEN` | Missing or invalid Bearer authentication |
| 403 | `INSUFFICIENT_PERMISSIONS` | Caller lacks `USER_SUSPEND` |
| 403 | `ACCOUNT_SUSPENDED`, `TENANT_SUSPENDED` | Caller account or tenant is suspended |
| 404 | `PROFILE_NOT_FOUND` | Target is missing, cross-tenant, deleted, or a platform administrator |
| 409 | `USER_NOT_SUSPENDED` | An eligible target is active, including after a successful lift |
| 422 | `ROLE_HIERARCHY_VIOLATION` | Target is a peer or has higher rank |

### 7) Update user details

`PATCH /v1/users/me`

**Implemented in Phase 3.** Updates the authenticated account within the signed principal's tenant and returns the same projection as `GET /v1/users/me`. No additional permission is required. Headers, query parameters, and request fields cannot select a different account.

Only `display_name` and `avatar_url` are accepted. Validation rejects the entire request before changing the account if any field is invalid or unsupported.

| Field | When supplied | When omitted |
| --- | --- | --- |
| `display_name` | String, nonblank after trimming, at most 100 characters after trimming; null is invalid | Preserve existing name |
| `avatar_url` | Null clears it; otherwise a string containing an absolute HTTPS URL with a host, at most 2,048 characters | Preserve existing avatar |

The engine stores avatar URLs without fetching them. An empty `{}` is accepted and preserves profile fields. Every successful update refreshes `updated_at`. Credentials, email, roles, tenant, status, and all other unsupported fields are rejected with 400 `VALIDATION_ERROR`.

#### Parameters

No params

#### Permissions

Users can only update their own profile

#### Request

```json
{
  "display_name": "Mr John",
  "avatar_url": "https://example.com/avatar.png"
}
```

#### Response

```json
{
  "data": {
    "id": "uuid",
    "display_name": "Mr John",
    "email": "john@company.com",
    "avatar_url": "https://example.com/avatar.png",
    "roles": [
      "MARKETPLACE_STAFF"
    ],
    "permissions": [
      "LISTING_CREATE",
      "ORDER_CREATE",
      "MESSAGE_CREATE"
    ]
  },
  "meta": {
    "requestId": "uuid",
    "pagination": null
  }
}
```

#### Success

200 OK

#### Error

```json
{
  "error": {
    "code": "INVALID_TOKEN",
    "message": "Token is invalid or has expired.",
    "details": []
  }
}
```

401 Unauthorized

Error: INVALID_TOKEN

Missing or rejected authentication returns 401 `INVALID_TOKEN`. Missing/deleted or mismatched caller accounts return 401 `INVALID_TOKEN`. Suspended callers and inactive tenants return 403 `ACCOUNT_SUSPENDED` and `TENANT_SUSPENDED`, respectively. Invalid fields, unsupported fields, malformed JSON, and missing bodies return 400 `VALIDATION_ERROR`. Error responses contain no request ID.

### 8) Delete user

`DELETE /v1/users/me`

The delete user endpoints allows a user to delete their own account. The user account won’t be fully deleted off of the system, however the deleted_at value will be set, and all the user’s listings will no longer be visible, and the account won’t be discoverable.

#### Parameters

No params

#### Permissions

None

#### Request

No body

#### Response

No body

#### Success

204 No Content

#### Error

```json
{
  "error": {
    "code": "PROFILE_NOT_FOUND",
    "message": "Requested profile not found",
    "request_id": "uuid",
    "details": []
  }
}
```

404 Not Found

Error: PROFILE_NOT_FOUND

```json
{
  "error": {
    "code": "ACTIVE_ORDERS_EXIST",
    "message": "Account cannot be deleted while orders are in progress",
    "request_id": "uuid",
    "details": []
  }
}
```

422 Unprocessable Entity

Error: ACTIVE_ORDERS_EXIST
