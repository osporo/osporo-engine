# Users API

Source: **Osporo API v1.docx**, User Domain. Converted and checked against the repository on 2026-10-19.

Design updated: 2026-10-20. The role catalogue and profile visibility policy below supersede the corresponding tables in the original document.

**Status:** Partially implemented. `GET /v1/users`, `GET /v1/users/me`, `PATCH /v1/users/me`, and `GET /v1/users/{id}` are implemented; the other four routes remain planned.

Read [API standards](../API_STANDARDS.md) for the current wire contract and shared rules, and the [domain index](README.md) for source interpretation. All routes remain under `/v1`. The endpoint descriptions and examples in **Source API design** document the original design, not proof of availability.

## Implementation notes and open decisions

Phase 1 foundation (2026-10-22): [UserService.getUser](../../src/main/java/com/osporo/engine/user/UserService.java) loads by ID within the current tenant context. Missing and cross-tenant users both raise 404 `PROFILE_NOT_FOUND`; absent tenant context raises 400 `TENANT_CONTEXT_MISSING`. The user entity permits a null display name, matching the database and registration behavior. This loading method does not implement profile visibility or account-status filtering.

Phase 2 adds `GET /v1/users/me` through `UserController`, `UserService.getOwnProfile`, and `UserMapper`. Phase 3 (2026-10-23) adds `PATCH /v1/users/me` with sparse-field validation and transactional updates. Their implemented contracts are described in sections 3 and 7 below. Phase 6 now enforces current account/tenant status on Bearer requests; token versioning remains deferred.

Phase 4 (2026-10-23) adds `GET /v1/users/{id}`, `UserAuthorizationPolicy`, and separate public/full DTOs, implementing section 4 below. It validates the requested tenant and filters target account status. Phase 6 additionally checks the caller before applying target-profile visibility; token versioning remains deferred.

Phase 5 adds `GET /v1/users`, requiring `USER_READ_ANY` and querying the authenticated principal's active tenant through a custom repository fragment. Section 2 below documents role/lifecycle filtering, approved sorting, and pagination. Phase 6 adds authentication-time membership and status checks before directory authorization and validation.

Phase 6 (2026-10-23) also aligns login/refresh checks and adds an internal tenant/user-scoped refresh-session revocation operation for future lifecycle endpoints. Suspended accounts and inactive tenants fail with 403; deleted/missing/mismatched Bearer identities fail with 401. Restoring active status can make an old unexpired access token usable again until token versioning is implemented.

Open details from the source:

- `BUSINESS_RULE_VIOLATION` on invitations is not in the current `ErrorCode` enum. Invitation acceptance, token delivery, and validation constraints are not specified here.
- `USER_READ_PUBLIC` exists in the permission enum and V12 seeds it for `BUYER` and `SELLER`. Login and refresh include it when resolving those seeded bundles. Phase 4 enforces its public-profile scope; it does not grant directory or private-field access.
- The suspension hierarchy and handling of multiple roles need precise rules before implementation. An error detail with `input` differs from the current shared `{field, message}` type.
- Deleting or updating one's own account requires authentication and ownership even where the source says no specific permission. The source does not fully define deleted-account token handling.

Editorial corrections: the list example now represents buyer and seller as two role strings, and `ROLE_HIERARCY_VIOLATION` is corrected to `ROLE_HIERARCHY_VIOLATION`.

## Endpoint index

| Endpoint | Operation |
| --- | --- |
| `POST /v1/users/invite` | [1) Invite users](#1-invite-users) |
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

This endpoint exists to allow admins to invite users to the platform. Particularly to allow admins to invite staff members to the admin end.

#### Parameters

No params

#### Permissions

USER_INVITE

#### Request

```json
{
  "invites": [
    {
      "email": "john@company.com",
      "roles": [
        "MARKETPLACE_OWNER"
      ]
    },
    {
      "email": "jane@company.com",
      "roles": [
        "MARKETPLACE_STAFF"
      ]
    }
  ]
}
```

#### Response

```json
{
  "data": {
    "invites": [
      {
        "email": "john@company.com",
        "roles": [
          "MARKETPLACE_OWNER"
        ],
        "status": "SENT"
      },
      {
        "email": "jane@company.com",
        "roles": [
          "MARKETPLACE_STAFF"
        ],
        "status": "ALREADY_MEMBER"
      }
    ],
    "expires_in": 172800
  },
  "meta": {
    "request_id": "uuid"
  }
}
```

#### Success

202 Accepted

#### Error

```json
{
  "error": {
    "code": "INSUFFICIENT_PERMISSIONS",
    "message": "Not authorized to access this endpoint",
    "request_id": "uuid",
    "details": []
  }
}
```

403 Forbidden

Error: INSUFFICIENT_PERMISSIONS

422 Unprocessable Entity

Error: BUSINESS_RULE_VIOLATION

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

This endpoint allows an authorized user to suspend another user account, with the exception that they cannot suspend another user at their level or higher.

#### Parameters

No params

#### Permissions

USER_SUSPEND

#### Request

```json
{
  "violation_type": "PAYMENT_FRAUD",
  "details": "User attempted to move payment off of the platform."
}
```

#### Response

```json
{
  "data": {
    "id": "uuid",
    "user_id": "uuid",
    "actor_id": "uuid",
    "action": "SUSPEND",
    "violation_type": "PAYMENT_FRAUD",
    "details": "User attempted to move payment off of the platform.",
    "suspended_at": "2026-03-17T09:14:45+02:00"
  },
  "meta": {
    "request_id": "uuid"
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
    "message": "You are not authorized to access this endpoint",
    "request_id": "uuid",
    "details": []
  }
}
```

403 Forbidden

Error: INSUFFICIENT_PERMISSIONS

```json
{
  "error": {
    "code": "ROLE_HIERARCHY_VIOLATION",
    "message": "Cannot suspend a profile with a role equal to or higher than yours",
    "request_id": "uuid",
    "details": [
      {
        "field": "roles",
        "input": [
          "MARKETPLACE_OWNER"
        ]
      }
    ]
  }
}
```

422 Unprocessable Entity

Error: ROLE_HIERARCHY_VIOLATION

```json
{
  "error": {
    "code": "USER_ALREADY_SUSPENDED",
    "message": "Cannot suspend a user that is suspended",
    "request_id": "uuid",
    "details": []
  }
}
```

422 Unprocessable Entity

Error: USER_ALREADY_SUSPENDED

### 6) Unsuspend user

`POST /v1/users/{id}/unsuspend`

This endpoint is used to lift the suspension of another user with a lower role.

#### Parameters

No params

#### Permissions

USER_SUSPEND

#### Requests

```json
{
  "details": "Appeal approved"
}
```

#### Response

```json
{
  "data": {
    "id": "uuid",
    "user_id": "uuid",
    "actor_id": "uuid",
    "action": "UPLIFT",
    "details": "Appeal approved",
    "action_date": "2026-03-18T10:13:32+02:00",
    "suspension": {
      "id": "uuid",
      "actor_id": "uuid",
      "violation_type": "PAYMENT_FRAUD",
      "details": "User attempted to move payment off of the platform.",
      "action_date": "2026-03-17T09:14:45+02:00"
    }
  },
  "meta": {
    "request_id": "uuid"
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
    "message": "You are not authorized to access this endpoint",
    "request_id": "uuid",
    "details": []
  }
}
```

403 Forbidden

Error: INSUFFICIENT_PERMISSIONS

```json
{
  "error": {
    "code": "ROLE_HIERARCHY_VIOLATION",
    "message": "Cannot unsuspend a profile with a role equal to or higher than yours",
    "request_id": "uuid",
    "details": []
  }
}
```

422 Unprocessable Entity

Error: ROLE_HIERARCHY_VIOLATION

```json
{
  "error": {
    "code": "USER_NOT_SUSPENDED",
    "message": "Cannot unsuspend a user that is not suspended",
    "request_id": "uuid",
    "details": []
  }
}
```

422 Unprocessable Entity

Error: USER_NOT_SUSPENDED

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
