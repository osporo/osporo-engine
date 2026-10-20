# Categories API

Source: **Osporo API v1.docx**, Category Domain. Converted and checked against the repository on 2026-10-19.

**Status:** Planned API; no controller implements these routes.

Read [API standards](../API_STANDARDS.md) for the current wire contract and shared rules, and the [domain index](README.md) for source interpretation. All routes remain under `/v1`. The endpoint descriptions and examples in **Source API design** document the original design, not proof of availability.

## Implementation notes and open decisions

No category controller or category service implements these routes. Tenant-specific categories and the behavior below remain planned.

Open details from the source:

- Create returns `is_active`, while read/update examples use `status`. Its response also omits `meta`. A consistent representation must be chosen before implementation.
- The source assigns creation status 200; shared standards prefer 201 for resource creation. The source value is retained below, pending resolution.
- Access to `INACTIVE` or `ALL` is described as admin-only without a precise permission; inactive single-category visibility also needs definition.
- Slug conflicts and repeated status actions use 422 below; shared standards distinguish state conflicts (409) from other business-rule failures. Resolve each concrete error before implementation.
- Subcategory support is a design intention, not verified runtime support. No category-delete route is supplied, despite the existing `CATEGORY_DELETE` permission.

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

#### Parameters

| Parameter | Default | Constraints | Description |
| --- | --- | --- | --- |
| page | 1 | ≥ 1 | Page number |
| per_page | 20 | 1 – 100 | Results per page |
| status | ACTIVE | ACTIVE, INACTIVE, ALL | Filter by status |

#### Permissions

None

#### Request

No body

#### Response

```json
{
  "data": [
    {
      "id": "uuid",
      "name": "Electronics",
      "slug": "electronics",
      "status": "ACTIVE",
      "created_at": "2026-03-18T11:13:00+02:00"
    },
    {
      "id": "uuid",
      "name": "Clothing",
      "slug": "clothing",
      "status": "ACTIVE",
      "created_at": "2026-03-18T11:13:00+02:00"
    }
  ],
  "meta": {
    "request_id": "uuid",
    "pagination": {
      "page": 1,
      "per_page": 20,
      "total": 12,
      "total_pages": 1
    }
  }
}
```

#### Success

200 OK

#### Error

403 Forbidden

Error: INSUFFICIENT_PERMISSIONS ← only if status=INACTIVE or ALL is requested by a non-admin caller

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

This allows admins to create new categories.

#### Parameters

No params

#### Permissions

CATEGORY_CREATE

#### Request

```json
{
  "name": "Gadgets and Electronics",
  "is_active": true
}
```

#### Response

```json
{
  "data": {
    "id": "uuid",
    "name": "Gadgets and Electronics",
    "slug": "gadgets-and-electronics",
    "is_active": true,
    "created_at": "2026-03-18T08:37:00+02:00"
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

422 Unprocessable Entity

Error: CATEGORY_SLUG_EXISTS

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
