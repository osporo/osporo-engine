# Tenants API

Source: **Osporo API v1.docx**, Tenant Domain. Converted and checked against the repository on 2026-10-19.

**Status:** Planned API; no controller implements these routes.

Read [API standards](../API_STANDARDS.md) for the current wire contract and shared rules, and the [domain index](README.md) for source interpretation. All routes remain under `/v1`. The endpoint descriptions and examples in **Source API design** document the original design, not proof of availability.

## Implementation notes and open decisions

Tenant entities, repositories, a basic service, and role-bundle persistence exist, but no tenant controller implements the routes below. Tenant settings, role editing, listing/moderation configuration APIs, and dynamic CORS are planned behavior.

Open details from the source:

- Several endpoints require the `MARKETPLACE_OWNER` role directly, conflicting with permission-based authorization in [API standards](../API_STANDARDS.md) and [ADR-008](../adrs/ADR-008-permission-based-auth-with-tenant-configurable-roles.md). Resolve a concrete tenant-management permission policy before implementation; this conversion does not invent new permissions.
- Ordinary tenant operations must never grant or edit `PLATFORM_ADMIN` or platform administrative roles. The source's owner minimum-permission protection is a design claim, not an implemented safeguard; the minimum set is not specified.
- `OWNER_MINIMUM_PERMISSIONS` and `INVALID_ORIGIN_FORMAT` are not in the current `ErrorCode` enum.
- `PAYFAST` and the `GROWTH` plan are source examples, not implemented provider support or committed commercial scope. Listing commission examples use percentage-like values such as `5.00`, whereas ADR-007 uses a fractional database example; define units explicitly.
- The moderation-configuration routes duplicate the [moderation domain](moderation.md) routes. Select one canonical contract or define aliases before implementation.
- Replacement semantics are deliberate in the source: permissions, required attributes, and allowed origins replace their entire arrays; filter rules update only the named entries.
- Dynamic CORS needs a tenant-resolution policy for browser preflight requests and a safe origin-validation/cache policy. CORS is not authentication or a tenant-isolation boundary.

The original overview describes platform administration as tenant management without access to tenant business data, but supplies no platform tenant-management routes. Do not infer such routes from this tenant-owner API.

## Endpoint index

| Endpoint | Operation |
| --- | --- |
| `GET /v1/tenant` | [1) Get tenant details](#1-get-tenant-details) |
| `PATCH /v1/tenant` | [2) Update tenant settings](#2-update-tenant-settings) |
| `GET /v1/tenant/roles` | [3) Get all role configurations](#3-get-all-role-configurations) |
| `GET /v1/tenant/roles/{roleId}` | [4) Get single role configuration](#4-get-single-role-configuration) |
| `PATCH /v1/tenant/roles/{roleId}` | [5) Update role permissions](#5-update-role-permissions) |
| `GET /v1/tenant/listing-config` | [6) Get listing configuration](#6-get-listing-configuration) |
| `PATCH /v1/tenant/listing-config` | [7) Update listing configuration](#7-update-listing-configuration) |
| `GET /v1/tenant/moderation-config` | [8) Get moderation configuration](#8-get-moderation-configuration) |
| `PATCH /v1/tenant/moderation-config` | [9) Update moderation configuration](#9-update-moderation-configuration) |
| `GET /v1/tenant/cors` | [10) Get allowed origins (CORS configuration)](#10-get-allowed-origins-cors-configuration) |
| `PATCH /v1/tenant/cors` | [11) Update allowed origins](#11-update-allowed-origins) |

## Source API design

## Purpose

- Allow marketplace owners to view and update their tenant settings
- Allow marketplace owners to manage role configurations
- Allow marketplace owners to configure listing behaviour

## Endpoints

### 1) Get tenant details

`GET /v1/tenant`

#### Permissions

MARKETPLACE_OWNER role — operators only

#### Request

No body

#### Response

```json
{
  "data": {
    "id": "uuid",
    "name": "My Marketplace",
    "slug": "my-marketplace",
    "status": "ACTIVE",
    "plan": "GROWTH",
    "payment_provider": "STRIPE",
    "created_at": "2026-01-01T00:00:00Z",
    "updated_at": "2026-03-18T00:00:00Z"
  },
  "meta": {
    "request_id": "uuid"
  }
}
```

#### Success

200 OK

#### Errors

403 Forbidden

Error: INSUFFICIENT_PERMISSIONS

### 2) Update tenant settings

`PATCH /v1/tenant`

#### Permissions

MARKETPLACE_OWNER role — operators only

All fields optional.

#### Request

```json
{
  "name": "My Updated Marketplace",
  "payment_provider": "PAYFAST"
}
```

#### Response

Full tenant object — same shape as GET /v1/tenant

#### Success

200 OK

#### Errors

403 Forbidden

Error: INSUFFICIENT_PERMISSIONS

422 Unprocessable Entity

Error: UNSUPPORTED_PAYMENT_PROVIDER

### 3) Get all role configurations

`GET /v1/tenant/roles`

#### Permissions

MARKETPLACE_OWNER role

#### Request

No body

#### Response

```json
{
  "data": [
    {
      "id": "uuid",
      "role_name": "SELLER",
      "permissions": [
        "LISTING_CREATE",
        "LISTING_UPDATE_OWN",
        "LISTING_DELETE_OWN",
        "LISTING_READ",
        "ORDER_READ_OWN",
        "MESSAGE_CREATE",
        "MESSAGE_READ_OWN",
        "REPORT_CREATE"
      ],
      "is_default": false,
      "created_at": "2026-01-01T00:00:00Z",
      "updated_at": "2026-01-01T00:00:00Z"
    },
    {
      "id": "uuid",
      "role_name": "BUYER",
      "permissions": [
        "LISTING_READ",
        "ORDER_CREATE",
        "ORDER_READ_OWN",
        "MESSAGE_CREATE",
        "MESSAGE_READ_OWN",
        "REPORT_CREATE"
      ],
      "is_default": true,
      "created_at": "2026-01-01T00:00:00Z",
      "updated_at": "2026-01-01T00:00:00Z"
    },
    {
      "id": "uuid",
      "role_name": "MARKETPLACE_STAFF",
      "permissions": [
        "LISTING_READ",
        "LISTING_TAKEDOWN_ANY",
        "ORDER_READ_ANY",
        "USER_READ_ANY",
        "USER_SUSPEND",
        "MODERATION_REVIEW",
        "REPORT_CREATE"
      ],
      "is_default": false,
      "created_at": "2026-01-01T00:00:00Z",
      "updated_at": "2026-01-01T00:00:00Z"
    },
    {
      "id": "uuid",
      "role_name": "MARKETPLACE_OWNER",
      "permissions": [
        "LISTING_READ",
        "LISTING_TAKEDOWN_ANY",
        "ORDER_READ_ANY",
        "ORDER_REFUND",
        "USER_READ_ANY",
        "USER_SUSPEND",
        "USER_INVITE",
        "MODERATION_REVIEW",
        "MODERATION_CONFIG",
        "REPORT_CREATE"
      ],
      "is_default": false,
      "created_at": "2026-01-01T00:00:00Z",
      "updated_at": "2026-01-01T00:00:00Z"
    }
  ],
  "meta": {
    "request_id": "uuid"
  }
}
```

#### Success

200 OK

#### Error

403 Forbidden

Error: INSUFFICIENT_PERMISSIONS

### 4) Get single role configuration

`GET /v1/tenant/roles/{roleId}`

#### Permissions

MARKETPLACE_OWNER role

#### Request

No body

#### Response

Single role object — same shape as one item from GET /v1/tenant/roles

#### Success

200 OK

#### Errors

403 Forbidden

Error: INSUFFICIENT_PERMISSIONS

404 Not Found

Error: ROLE_NOT_FOUND

### 5) Update role permissions

`PATCH /v1/tenant/roles/{roleId}`

#### Permissions

MARKETPLACE_OWNER role

The permissions array replaces the existing bundle entirely — it is not additive. The caller must send the complete desired permission set. This avoids ambiguity about whether the request adds to or replaces the existing bundle.

MARKETPLACE_OWNER cannot have its permissions reduced below a minimum set required to manage the tenant — the engine enforces this. A marketplace owner cannot accidentally lock themselves out.

#### Request

```json
{
  "permissions": [
    "LISTING_CREATE",
    "LISTING_UPDATE_OWN",
    "LISTING_DELETE_OWN",
    "LISTING_READ",
    "ORDER_READ_OWN",
    "MESSAGE_CREATE",
    "MESSAGE_READ_OWN",
    "REPORT_CREATE"
  ]
}
```

#### Response

Full role object — same shape as GET /v1/tenant/roles/{roleId}

#### Success

200 OK

#### Errors

403 Forbidden

Error: INSUFFICIENT_PERMISSIONS

404 Not Found

Error: ROLE_NOT_FOUND

422 Unprocessable Entity

Error: INVALID_PERMISSION_BUNDLE      ←  contains an unrecognised permission constant

422 Unprocessable Entity

Error: CANNOT_MODIFY_SYSTEM_ROLE      ←  OSPORO_ADMIN cannot be modified by tenants

422 Unprocessable Entity

Error: OWNER_MINIMUM_PERMISSIONS      ←  would remove permissions required to manage tenant

### 6) Get listing configuration

`GET /v1/tenant/listing-config`

#### Permissions

MARKETPLACE_OWNER role

#### Request

No body

#### Response

```json
{
  "data": {
    "id": "uuid",
    "listing_type": "PHYSICAL_GOOD",
    "delivery_method": "SHIPPING",
    "required_attributes": [
      {
        "name": "condition",
        "type": "STRING",
        "required": true
      },
      {
        "name": "quantity",
        "type": "INTEGER",
        "required": true
      }
    ],
    "allow_negotiation": false,
    "track_inventory": true,
    "commission_rate": "5.00",
    "max_images_per_listing": 8,
    "updated_at": "2026-01-01T00:00:00Z"
  },
  "meta": {
    "request_id": "uuid"
  }
}
```

#### Success

200 OK

#### Errors

403 Forbidden

Error: INSUFFICIENT_PERMISSIONS

### 7) Update listing configuration

`PATCH /v1/tenant/listing-config`

#### Permissions

MARKETPLACE_OWNER role

All fields optional — only supplied fields are updated.

required_attributes replaces the existing array entirely when supplied, same as the permissions bundle on roles. Send the full desired attribute set.

#### Request

```json
{
  "allow_negotiation": true,
  "commission_rate": "7.50",
  "max_images_per_listing": 12,
  "required_attributes": [
    {
      "name": "condition",
      "type": "STRING",
      "required": true
    },
    {
      "name": "quantity",
      "type": "INTEGER",
      "required": true
    },
    {
      "name": "brand",
      "type": "STRING",
      "required": false
    }
  ]
}
```

#### Response

Full listing config — same shape as GET /v1/tenant/listing-config

#### Success

200 OK

#### Errors

403 Forbidden

Error: INSUFFICIENT_PERMISSIONS

422 Unprocessable Entity

Error: INVALID_LISTING_TYPE

422 Unprocessable Entity

Error: INVALID_ATTRIBUTE              ←  required_attributes contains an unsupported type

### 8) Get moderation configuration

`GET /v1/tenant/moderation-config`

#### Permissions

MODERATION_CONFIG

Note the permission here is MODERATION_CONFIG, not MARKETPLACE_OWNER role. This aligns with ADR-011 — moderation configuration is controlled by MODERATION_CONFIG permission, which defaults to MARKETPLACE_OWNER but can be granted separately.

#### Request

No body

#### Response

```json
{
  "data": {
    "id": "uuid",
    "require_review": true,
    "auto_suspend_threshold": 3,
    "filter_rules": [
      {
        "rule_name": "BANK_ACCOUNT_NUMBER",
        "enabled": true
      },
      {
        "rule_name": "PHONE_NUMBER",
        "enabled": false
      },
      {
        "rule_name": "EMAIL_ADDRESS",
        "enabled": true
      }
    ],
    "updated_at": "2026-01-01T00:00:00Z"
  },
  "meta": {
    "request_id": "uuid"
  }
}
```

#### Success

200 OK

#### Errors

403 Forbidden

Error: INSUFFICIENT_PERMISSIONS

### 9) Update moderation configuration

`PATCH /v1/tenant/moderation-config`

#### Permissions

MODERATION_CONFIG

All fields optional. filter_rules is additive — only the rules included in the request are updated. Rules not included remain unchanged. This differs from required_attributes and permissions which replace entirely, because filter rules are toggled individually rather than redefined as a set.

#### Request

```json
{
  "require_review": false,
  "auto_suspend_threshold": 5,
  "filter_rules": [
    {
      "rule_name": "PHONE_NUMBER",
      "enabled": true
    }
  ]
}
```

#### Response

Full moderation config — same shape as GET /v1/tenant/moderation-config

#### Success

200 OK

#### Errors

403 Forbidden

Error: INSUFFICIENT_PERMISSIONS

422 Unprocessable Entity

Error: INVALID_FILTER_RULE            ←  rule_name is not recognised by the engine

### 10) Get allowed origins (CORS configuration)

`GET /v1/tenant/cors`

#### Permissions

MARKETPLACE_OWNER role

Returns the list of origins permitted to make cross-origin requests to this tenant's API. Used by the dynamic CORS configuration to validate incoming requests.

#### Request

No body

#### Response

```json
{
  "data": {
    "allowed_origins": [
      "https://mymarketplace.com",
      "https://www.mymarketplace.com",
      "https://staging.mymarketplace.com"
    ]
  },
  "meta": {
    "request_id": "uuid"
  }
}
```

#### Success

200 OK

#### Errors

403 Forbidden

Error: INSUFFICIENT_PERMISSIONS

### 11) Update allowed origins

`PATCH /v1/tenant/cors`

#### Permissions

MARKETPLACE_OWNER role

Replaces the allowed origins list entirely. Send the complete desired set.

#### Request

```json
{
  "allowed_origins": [
    "https://mymarketplace.com",
    "https://www.mymarketplace.com"
  ]
}
```

#### Response

Full CORS config – same shape as GET /v1/tenant/cors

#### Success

200 OK

#### Errors

403 Forbidden

Error: INSUFFICIENT_PERMISSIONS

422 Unprocessable Entity

Error: INVALID_ORIGIN_FORMAT          ←  an origin is not a valid URL with scheme
