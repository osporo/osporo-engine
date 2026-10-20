# Users API

Source: **Osporo API v1.docx**, User Domain. Converted and checked against the repository on 2026-10-19.

**Status:** Planned API; no controller implements these routes.

Read [API standards](../API_STANDARDS.md) for the current wire contract and shared rules, and the [domain index](README.md) for source interpretation. All routes remain under `/v1`. The endpoint descriptions and examples in **Source API design** document the original design, not proof of availability.

## Implementation notes and open decisions

No user controller implements these routes. [UserService.getUser](../../src/main/java/com/osporo/engine/user/UserService.java) still uses an unscoped ID lookup; making it tenant-scoped is an agreed pending fix. The source's profile visibility, invitations, suspension hierarchy, audit responses, and deletion behavior are designs, not implemented guarantees.

Open details from the source:

- The user-list parameter table says `MARKETPLACE_ADMIN`, while the enum and examples use `MARKETPLACE_STAFF`. Preserve this discrepancy for resolution; do not introduce a new role from the table.
- `BUSINESS_RULE_VIOLATION` on invitations is not in the current `ErrorCode` enum. Invitation acceptance, token delivery, and validation constraints are not specified here.
- The profile visibility matrix uses role names; implementation must reconcile it with permission-based authorization without treating roles as permission substitutes.
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

Allows a user to fetch all users in a marketplace.

#### Parameters

| Parameter | Default | Constraints | Description |
| --- | --- | --- | --- |
| page | 1 | ≥ 1 | Page number |
| per_page | 20 | 1 – 100 | Results per page |
| sort | CREATED_AT | CREATED_AT, DELETED_AT, UPDATED_AT, SUSPENDED_AT | Field to sort by |
| order | desc | asc \| desc | Sort direction |
| roles | [BUYER, SELLER, MARKETPLACE_ADMIN, MARKETPLACE_OWNER] | All roles excluding OSPORO_ADMIN | Roles to filter by, provided as a list |

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
      "created_at": "2025-05-01T00:00:00+02:00"
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
      "created_at": "2026-03-17T00:00:00+02:00"
    }
  ],
  "meta": {
    "request_id": "uuid",
    "pagination": {
      "page": 1,
      "per_page": 20,
      "total": 143,
      "total_pages": 8
    },
    "filter_roles": [
      "MARKETPLACE_OWNER",
      "MARKETPLACE_STAFF",
      "BUYER",
      "SELLER"
    ]
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
    "message": "Not authorized to access this endpoint",
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
    "code": "INVALID_PARAMETER",
    "message": "The roles filter contains an invalid or unsupported value.",
    "request_id": "uuid",
    "details": [
      {
        "field": "roles",
        "message": "OSPORO_ADMIN is not a valid filter value."
      }
    ]
  }
}
```

400 Bad Request

Error: INVALID_PARAMETER

### 3) Get own profile

`GET /v1/users/me`

This endpoint is used by the client to tell which user is currently logged in, what their roles are and what they can do.

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
    "code": "INVALID_TOKEN",
    "message": "Token is invalid or has expired",
    "request_id": "uuid",
    "details": []
  }
}
```

401 Unauthorized

Error: INVALID_TOKEN

### 4) Get user profile

`GET /v1/users/{id}`

This endpoint allows any users to access a user’s public profile. Guests can only view the profile of a seller. Participants can see each other’s profiles. Operators can see all profiles.

#### Parameters

No params

#### Permissions

Visibility matrix

| Viewer | Can see |
| --- | --- |
| Guest (no token) | SELLER profiles only |
| BUYER | BUYER + SELLER profiles |
| SELLER | BUYER + SELLER profiles |
| MARKETPLACE_STAFF | All tenant profiles |
| MARKETPLACE_OWNER | All tenant profiles |

#### Request

No body

#### Response

Callers with USER_READ_ANY receive the full profile response. All other callers receive the public profile response.

Public profile

```json
{
  "data": {
    "id": "uuid",
    "display_name": "John Doe",
    "avatar_url": "https://...",
    "seller_rating": 4.8,
    "member_since": "2026-03-12T00:00:00+02:00"
  },
  "meta": {
    "request_id": "uuid"
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
    "seller_rating": 4.8,
    "created_at": "2026-03-12T00:00:00+02:00",
    "suspended_at": null
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
    "code": "PROFILE_NOT_FOUND",
    "message": "The requested profile was not found",
    "request_id": "uuid",
    "details": []
  }
}
```

404 Not Found

Error: PROFILE_NOT_FOUND

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

This endpoint allows a user to make updates to their own profile.

#### Parameters

No params

#### Permissions

Users can only update their own profile

#### Request

```json
{
  "display_name": "Mr John",
  "avatar_url": "https://..."
}
```

#### Response

```json
{
  "data": {
    "id": "uuid",
    "display_name": "Mr John",
    "email": "john@company.com",
    "avatar_url": "https://...",
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
    "code": "PROFILE_NOT_FOUND",
    "message": "Requested profile not found",
    "request_id": "uuid",
    "details": []
  }
}
```

404 Not Found

Error: PROFILE_NOT_FOUND

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
