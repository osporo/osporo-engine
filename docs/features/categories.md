# Categories API

Source: **Osporo API v1.docx**, Category Domain. Converted and checked against the repository on 2026-10-19.

**Status:** Partially implemented. Category creation and listing are available. Single-category reads, updates, and status changes remain planned.

Read [API standards](../API_STANDARDS.md) for the current wire contract and shared rules, and the [domain index](README.md) for source interpretation. All routes remain under `/v1`. The endpoint descriptions and examples in **Source API design** document the original design, not proof of availability.

## Implementation notes and open decisions

`POST /v1/categories` creates a flat category in the caller's tenant. V12 seeds `CATEGORY_CREATE` in the marketplace owner's role bundle. Use a fresh development database to apply the updated seed, then log in as the owner to get a token with the permission.

Open details from the source:

- Creation and listing use `is_active` and shared response metadata. Align the remaining planned representations when implementing them.
- Inactive single-category visibility still needs definition.
- Category creation returns 409 for slug conflicts. Resolve the status codes for repeated lifecycle actions when implementing those routes.
- Categories are flat in the MVP. No category-delete route is supplied, despite the existing `CATEGORY_DELETE` permission.

## Endpoint index

| Endpoint | Operation |
| --- | --- |
| `GET /v1/categories` | [1) Get all categories](#1-get-all-categories) |
| `GET /v1/categories/{id}` | [2) Get single category](#2-get-single-category) |
| `POST /v1/categories` | [3) Create category](#3-create-category) |
| `PATCH /v1/categories/{id}` | [4) Update category](#4-update-category) |
| `POST /v1/categories/{id}/activate` | [5) Activate category](#5-activate-category) |
| `POST /v1/categories/{id}/deactivate` | [6) Deactivate category](#6-deactivate-category) |

## Source API design

## Purpose

- Allows admins to create, edit and rename categories
- Categories are unique to the tenant
- In the MVP categories are flat, however subcategory support is built in for later implementation

## Endpoints

### 1) Get all categories

`GET /v1/categories`

**Implemented.** Lists categories for an active tenant, ordered by name ascending and then UUID ascending. An authenticated caller uses the signed tenant context. A guest supplies the tenant UUID in `X-Tenant-ID`.

#### Parameters

| Parameter | Default | Constraints | Description |
| --- | --- | --- | --- |
| page | 1 | ≥ 1 | Page number |
| per_page | 20 | 1 – 100 | Results per page |
| status | ACTIVE | ACTIVE, INACTIVE, ALL | Filter by status |

#### Permissions

Active categories are public within the resolved tenant. Filtering with `INACTIVE` or `ALL` requires an authenticated caller with `CATEGORY_CREATE` or `CATEGORY_EDIT`. Owners using the seeded role can use both filters through `CATEGORY_CREATE`.

#### Request

No body

#### Response

```json
{
  "data": [
    {
      "id": "uuid",
      "name": "Clothing",
      "slug": "clothing",
      "is_active": true,
      "created_at": "2026-03-18T11:13:00+02:00"
    },
    {
      "id": "uuid",
      "name": "Electronics",
      "slug": "electronics",
      "is_active": true,
      "created_at": "2026-03-18T11:13:00+02:00"
    }
  ],
  "meta": {
    "requestId": "uuid",
    "pagination": {
      "page": 1,
      "perPage": 20,
      "total": 2,
      "totalPages": 1
    }
  }
}
```

#### Success

200 OK. Out-of-range pages return an empty `data` array and retain the filtered total.

#### Error

| Status | Code | Condition |
| --- | --- | --- |
| 400 | `INVALID_PARAMETER` | Invalid, repeated, or unsupported query parameter |
| 400 | `TENANT_CONTEXT_MISSING` | Guest tenant header is missing or malformed |
| 401 | `INVALID_TOKEN` | Supplied Bearer token is invalid |
| 403 | `INSUFFICIENT_PERMISSIONS` | Caller lacks a category permission required for `INACTIVE` or `ALL` |
| 403 | `ACCOUNT_SUSPENDED`, `TENANT_SUSPENDED` | Caller account or resolved tenant is suspended |
| 404 | `TENANT_NOT_FOUND` | Guest tenant UUID is unknown |

### 2) Get single category

`GET /v1/categories/{id}`

#### Permissions

None

#### Request

No body

#### Response

```json
{
  "data": {
    "id": "uuid",
    "name": "Electronics",
    "slug": "electronics",
    "status": "ACTIVE",
    "created_at": "2026-03-01T11:23:00+02:00",
    "updated_at": "2026-03-01T11:23:00+02:00"
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
    "code": "CATEGORY_NOT_FOUND",
    "message": "The requested category was not found.",
    "request_id": "uuid",
    "details": []
  }
}
```

404 Not Found

Error: CATEGORY_NOT_FOUND

### 3) Create category

`POST /v1/categories`

**Implemented.** Requires a valid Bearer token with `CATEGORY_CREATE`. The signed principal supplies the tenant, so a conflicting `X-Tenant-ID` cannot change where the category is created. Each category is created without a parent.

#### Request

`name` is required and must be nonblank and at most 100 characters after trimming. It cannot contain NUL characters. `is_active` is optional and defaults to `true` when omitted or null. Send `false` to create an inactive category.

The engine generates the slug from the name. It lowercases and normalizes the text, keeps Unicode letters, decimal digits, and combining marks, and collapses other characters to hyphens. The slug must contain a letter or digit and fit within 120 characters. Uniqueness applies within the tenant, including inactive categories.

```json
{
  "name": "Gadgets and Electronics",
  "is_active": true
}
```

#### Response (201 Created)

```json
{
  "data": {
    "id": "uuid",
    "name": "Gadgets and Electronics",
    "slug": "gadgets-and-electronics",
    "is_active": true,
    "created_at": "2026-03-18T06:37:00Z"
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
| 400 | `VALIDATION_ERROR` | Missing or unreadable body, invalid name, or name that cannot produce a valid slug |
| 401 | `INVALID_TOKEN` | Missing or invalid authentication |
| 403 | `INSUFFICIENT_PERMISSIONS` | Caller lacks `CATEGORY_CREATE` |
| 403 | `ACCOUNT_SUSPENDED`, `TENANT_SUSPENDED` | Caller account or tenant is suspended |
| 409 | `CATEGORY_SLUG_EXISTS` | Generated slug is already in use in the tenant |

### 4) Update category

`PATCH /v1/categories/{id}`

#### Permissions

CATEGORY_EDIT

#### Request

```json
{
  "name": "Consumer Electronics",
  "slug": "consumer-electronics"
}
```

#### Response

```json
{
  "data": {
    "id": "uuid",
    "name": "Consumer Electronics",
    "slug": "consumer-electronics",
    "status": "ACTIVE",
    "updated_at": "2026-03-18T10:00:00Z"
  },
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

404 Not Found

Error: CATEGORY_NOT_FOUND

422 Unprocessable Entity

Error: CATEGORY_SLUG_EXISTS

### 5) Activate category

`POST /v1/categories/{id}/activate`

#### Permissions

CATEGORY_EDIT

#### Request

No body

#### Response

```json
{
  "data": {
    "id": "uuid",
    "name": "Electronics",
    "slug": "electronics",
    "status": "ACTIVE",
    "updated_at": "2026-03-18T10:00:00Z"
  },
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

404 Not Found

Error: CATEGORY_NOT_FOUND

422 Unprocessable Entity

Error: CATEGORY_ALREADY_ACTIVE

### 6) Deactivate category

`POST /v1/categories/{id}/deactivate`

#### Permissions

CATEGORY_EDIT

#### Request

No body

#### Response

```json
{
  "data": {
    "id": "uuid",
    "name": "Electronics",
    "slug": "electronics",
    "status": "INACTIVE",
    "updated_at": "2026-03-18T10:00:00Z"
  },
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

404 Not Found

Error: CATEGORY_NOT_FOUND

422 Unprocessable Entity

Error: CATEGORY_ALREADY_INACTIVE

422 Unprocessable Entity

Error: CATEGORY_HAS_ACTIVE_LISTINGS
