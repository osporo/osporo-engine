# Listings API

Source: **Osporo API v1.docx**, Listings Domain. Converted and checked against the repository on 2026-10-19.

**Status:** Planned API; no controller implements these routes.

Read [API standards](../API_STANDARDS.md) for the current wire contract and shared rules, and the [domain index](README.md) for source interpretation. All routes remain under `/v1`. The endpoint descriptions and examples in **Source API design** document the original design, not proof of availability.

## Implementation notes and open decisions

No listing controller, listing service, or image-upload workflow implements these routes. Listing examples, search scores, ratings, moderation, and storage behavior below remain planned.

Open details from the source:

- The list endpoint's error mentions `listing_type`, but its parameter table omits that filter. Its `INACTIVE` status filter conflicts with the lifecycle examples using `DRAFT`, `ACTIVE`, `SOLD`, and `TAKEN_DOWN`. Visibility rules for nonpublic list results need definition.
- Public seller listings have a parameter named `status` described as a category UUID; the source table is retained rather than guessing its intended name.
- Image upload says both `multipart/form-data` and direct upload to object storage, but the request section is empty. Define the engine request fields, upload method, content constraints, expiry, and completion handling before implementing it.
- Listing creation accepts `listing_type`, whereas [ADR-007](../adrs/ADR-007-listing-type-generalisation-with-jsonb-attributes.md) says it is inherited from tenant configuration and immutable. Reconcile the request with that decision.
- Takedown mentions operator reinstatement, but the source supplies no reinstatement endpoint.
- Own-listings access says sellers only while naming `LISTING_READ`, which is also seeded for buyers. Define the intended permission and ownership policy without relying on a role label alone.
- All `_OWN` actions must check listing ownership and tenant scope. Matching an image UUID must also establish that it belongs to the scoped listing.
- Repeated actions and active-order conflicts use 422 below; resolve any differences from the shared 409/422 convention when implementing them.

Editorial corrections: missing closing backticks in the unpublish and mark-sold response descriptions are repaired.

## Endpoint index

| Endpoint | Operation |
| --- | --- |
| `GET /v1/listings` | [1) Get all listings](#1-get-all-listings) |
| `GET /v1/listings/search` | [2) Search listings](#2-search-listings) |
| `GET /v1/listings/{id}` | [3) Get single listing](#3-get-single-listing) |
| `POST /v1/listings` | [4) Create listing](#4-create-listing) |
| `PATCH /v1/listings/{id}` | [5) Update listing](#5-update-listing) |
| `POST /v1/listings/{id}/publish` | [6) Publish listing](#6-publish-listing) |
| `POST /v1/listings/{id}/unpublish` | [7) Unpublish listing](#7-unpublish-listing) |
| `POST /v1/listings/{id}/mark-sold` | [8) Mark listing as sold](#8-mark-listing-as-sold) |
| `POST /v1/listings/{id}/takedown` | [9) Takedown listing (operator)](#9-takedown-listing-operator) |
| `DELETE /v1/listings/{id}` | [10) Delete listing](#10-delete-listing) |
| `POST /v1/listings/{id}/images` | [11) Upload listing images](#11-upload-listing-images) |
| `DELETE /v1/listings/{id}/images/{imageId}` | [12) Delete listing image](#12-delete-listing-image) |
| `PATCH /v1/listings/{id}/images/reorder` | [13) Reorder listing images](#13-reorder-listing-images) |
| `GET /v1/users/me/listings` | [14) Get own listings](#14-get-own-listings) |
| `GET /v1/users/{id}/listings` | [15) Get seller’s public listings](#15-get-sellers-public-listings) |

## Source API design

## Purpose

- Allow sellers to create and manage listings
- Allow any user to browse and search listings
- Allow operators to manage and moderate listings
- Support configurable listing types and JSONB attributes per tenant

## Endpoints

### 1) Get all listings

`GET /v1/listings`

#### Parameters

| Parameter | Default | Constraints | Description |
| --- | --- | --- | --- |
| page | 1 | ≥ 1 | Page number |
| per_page | 20 | 1 – 100 | Results per page |
| sort | created_at | created_at, price, updated_at | Field to sort by |
| order | desc | asc, desc | Sort direction |
| category_id | — | Valid category UUID | Filter by category |
| min_price | — | ≥ 0 | Minimum price filter |
| max_price | — | ≥ 0 | Maximum price filter |
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
      "title": "Sony WH-1000XM5",
      "slug": "sony-wh-1000xm5",
      "price": "4500.00",
      "currency": "ZAR",
      "listing_type": "PHYSICAL_GOOD",
      "status": "ACTIVE",
      "category": {
        "id": "uuid",
        "name": "Electronics"
      },
      "seller": {
        "id": "uuid",
        "display_name": "John Doe",
        "avatar_url": "https://...",
        "seller_rating": 4.8
      },
      "primary_image_url": "https://...",
      "created_at": "2026-03-18T12:59:00+02:00"
    }
  ],
  "meta": {
    "request_id": "uuid",
    "pagination": {
      "page": 1,
      "per_page": 20,
      "total": 284,
      "total_pages": 15
    }
  }
}
```

#### Success

200 OK

#### Error

400 Bad Request

Error: INVALID_PARAMETER ← invalid listing_type or negative price values

### 2) Search listings

`GET /v1/listings/search`

#### Parameters

| Parameter | Default | Constraints | Description |
| --- | --- | --- | --- |
| q | — | Required, min 2 chars | Search query |
| page | 1 | ≥ 1 | Page number |
| per_page | 20 | 1 – 100 | Results per page |
| sort | created_at | created_at, price, updated_at | Field to sort by |
| order | desc | asc, desc | Sort direction |
| category_id | — | Valid category UUID | Filter by category |
| min_price | — | ≥ 0 | Minimum price filter |
| max_price | — | ≥ 0 | Maximum price filter |
| listing_type | — | PHYSICAL_GOOD, DIGITAL_GOOD, SERVICE, VEHICLE, CUSTOM | Filter by type |

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
      "title": "Sony WH-1000XM5",
      "slug": "sony-wh-1000xm5",
      "price": "4500.00",
      "currency": "ZAR",
      "listing_type": "PHYSICAL_GOOD",
      "status": "ACTIVE",
      "category": {
        "id": "uuid",
        "name": "Electronics"
      },
      "seller": {
        "id": "uuid",
        "display_name": "John Doe",
        "avatar_url": "https://...",
        "seller_rating": 4.8
      },
      "primary_image_url": "https://...",
      "relevance_score": 0.92,
      "created_at": "2026-03-01T00:00:00Z"
    }
  ],
  "meta": {
    "request_id": "uuid",
    "pagination": {
      "page": 1,
      "per_page": 20,
      "total": 14,
      "total_pages": 1
    }
  }
}
```

#### Success

200 OK

#### Error

400 Bad request

Error: INVALID_PARAMETER

### 3) Get single listing

`GET /v1/listings/{id}`

#### Permissions

None — inactive or deleted listings return 404 for non-operators

#### Request

No body

#### Response

```json
{
  "data": {
    "id": "uuid",
    "title": "Sony WH-1000XM5",
    "slug": "sony-wh-1000xm5",
    "description": "Barely used, excellent condition.",
    "price": "4500.00",
    "currency": "ZAR",
    "listing_type": "PHYSICAL_GOOD",
    "status": "ACTIVE",
    "attributes": {
      "condition": "used",
      "quantity": 1
    },
    "category": {
      "id": "uuid",
      "name": "Electronics"
    },
    "seller": {
      "id": "uuid",
      "display_name": "John Doe",
      "avatar_url": "https://...",
      "seller_rating": 4.8
    },
    "images": [
      {
        "id": "uuid",
        "url": "https://...",
        "sort_order": 1
      },
      {
        "id": "uuid",
        "url": "https://...",
        "sort_order": 2
      }
    ],
    "created_at": "2026-03-18T13:51:00+02:00",
    "updated_at": "2026-03-18T13:51:00+02:00"
  },
  "meta": {
    "request_id": "uuid"
  }
}
```

#### Success

200 OK

#### Errors

404 Not Found

Error: LISTING_NOT_FOUND

### 4) Create listing

`POST /v1/listings`

#### Permissions

LISTING_CREATE

#### Request

```json
{
  "title": "Sony WH-1000XM5",
  "description": "Barely used, excellent condition.",
  "price": "4500.00",
  "currency": "ZAR",
  "category_id": "uuid",
  "listing_type": "PHYSICAL_GOOD",
  "attributes": {
    "condition": "used",
    "quantity": 1
  }
}
```

#### Response

```json
{
  "data": {
    "id": "uuid",
    "title": "Sony WH-1000XM5",
    "slug": "sony-wh-1000xm5",
    "description": "Barely used, excellent condition.",
    "price": "4500.00",
    "currency": "ZAR",
    "listing_type": "PHYSICAL_GOOD",
    "status": "DRAFT",
    "attributes": {
      "condition": "used",
      "quantity": 1
    },
    "category": {
      "id": "uuid",
      "name": "Electronics"
    },
    "images": [],
    "created_at": "2026-03-18T09:00:00Z"
  },
  "meta": {
    "request_id": "uuid"
  }
}
```

#### Success

201 Created

#### Error

403 Forbidden

Error: INSUFFICIENT_PERMISSIONS

404 Not Found

Error: CATEGORY_NOT_FOUND

422 Unprocessable Entity

Error: INVALID_LISTING_TYPE ← type not supported by this tenant

422 Unprocessable Entity

Error: INVALID_ATTRIBUTE ← JSONB attribute fails tenant validation rules

### 5) Update listing

`PATCH /v1/listings/{id}`

#### Permissions

LISTING_UPDATE_OWN

#### Request

```json
{
  "title": "Sony WH-1000XM5 — Price Drop",
  "price": "3999.00",
  "attributes": {
    "condition": "used",
    "quantity": 1
  }
}
```

#### Response

Full listing object, same shape as GET /v1/listings/{id}

#### Success

200 OK

#### Error

403 Forbidden

Error: INSUFFICIENT_PERMISSIONS

404 Not Found

Error: LISTING_NOT_FOUND

422 Unprocessable Entity

Error: INVALID_ATTRIBUTE

422 Unprocessable Entity

Error: LISTING_NOT_EDITABLE ← listing is SOLD or taken down, cannot be edited

### 6) Publish listing

`POST /v1/listings/{id}/publish`

#### Permissions

LISTING_UPDATE_OWN

Transitions listing from `DRAFT` to `ACTIVE`. Validates that all required attributes per `TenantListingConfig` are present before allowing publish.

#### Request

No body

#### Response

Full listing object with `status: ACTIVE`

#### Success

200 OK

#### Errors

403 Forbidden

Error: INSUFFICIENT_PERMISSIONS

404 Not Found

Error: LISTING_NOT_FOUND

422 Unprocessable Entity

Error: LISTING_MISSING_REQUIRED_ATTRIBUTES ← tenant config requires fields not yet filled

422 Unprocessable Entity

Error: LISTING_MISSING_IMAGES ← if tenant config requires at least one image

422 Unprocessable Entity

Error: LISTING_ALREADY_ACTIVE

### 7) Unpublish listing

`POST /v1/listings/{id}/unpublish`

#### Permissions

LISTING_UPDATE_OWN

Transitions listing from `ACTIVE` back to `DRAFT`. Blocked if the listing has an active order against it.

#### Request

No body

#### Response

Full listing object with `status: DRAFT`

#### Success

200 OK

#### Errors

403 Forbidden

Error: INSUFFICIENT_PERMISSIONS

404 Not Found

Error: LISTING_NOT_FOUND

422 Unprocessable Entity

Error: LISTING_ALREADY_DRAFT

422 Unprocessable Entity

Error: LISTING_HAS_ACTIVE_ORDER ← cannot unpublish while an order is in progress

### 8) Mark listing as sold

`POST /v1/listings/{id}/mark-sold`

#### Permissions

LISTING_UPDATE_OWN

Allows a seller to manually mark a listing as sold outside of the order flow — for example, if a transaction was completed in person. Transitions status to SOLD.

#### Request

```json
{
  "reason": "Sold in person"
}
```

#### Response

Full listing object with `status: SOLD`

#### Success

200 OK

#### Errors

403 Forbidden

Error: INSUFFICIENT_PERMISSIONS

404 Not Found

Error: LISTING_NOT_FOUND

422 Unprocessable Entity

Error: LISTING_ALREADY_SOLD

422 Unprocessable Entity

Error: LISTING_HAS_ACTIVE_ORDER

### 9) Takedown listing (operator)

`POST /v1/listings/{id}/takedown`

#### Permissions

LISTING_TAKEDOWN_ANY

Operator action. Removes a listing from public view regardless of its current status. Transitions to TAKEN_DOWN. The seller cannot reverse this — only an operator can reinstate.

#### Request

```json
{
  "reason": "PROHIBITED_ITEM",
  "details": "Listing violates marketplace prohibited items policy."
}
```

#### Response

Full listing object with `status: TAKEN_DOWN`

#### Success

200 OK

#### Errors

403 Forbidden

Error: INSUFFICIENT_PERMISSIONS

404 Not Found

Error: LISTING_NOT_FOUND

422 Unprocessable Entity

Error: LISTING_ALREADY_TAKEN_DOWN

### 10) Delete listing

`DELETE /v1/listings/{id}`

#### Permissions

LISTING_DELETE_OWN

Soft delete. Sets `deleted_at` timestamp. Listing disappears from all public views. Blocked if the listing has an active order.

#### Request

No body

#### Response

No body

#### Success

204 No Content

#### Errors

403 Forbidden

Error: INSUFFICIENT_PERMISSIONS

404 Not Found

Error: LISTING_NOT_FOUND

422 Unprocessable Entity

Error: LISTING_HAS_ACTIVE_ORDER

### 11) Upload listing images

`POST /v1/listings/{id}/images`

#### Permissions

LISTING_UPDATE_OWN

Accepts multipart/form-data. Generates a presigned S3/R2 URL for direct upload and returns the image record. The client uploads directly to object storage — images never pass through the engine.

#### Request

**Unspecified in source:** no upload request fields or example were supplied.

#### Response

```json
{
  "data": {
    "id": "uuid",
    "listing_id": "uuid",
    "upload_url": "https://r2.../presigned-upload-url",
    "url": "https://r2.../public-image-url",
    "sort_order": 1
  },
  "meta": {
    "request_id": "uuid"
  }
}
```

#### Success

201 Created

#### Errors

403 Forbidden

Error: INSUFFICIENT_PERMISSIONS

404 Not Found

Error: LISTING_NOT_FOUND

422 Unprocessable Entity

Error: IMAGE_LIMIT_EXCEEDED ← tenant config defines max images per listing

### 12) Delete listing image

`DELETE /v1/listings/{id}/images/{imageId}`

#### Permissions

LISTING_UPDATE_OWN

#### Request

No body

#### Response

No body

#### Success

204 No Content

#### Errors

403 Forbidden

Error: INSUFFICIENT_PERMISSIONS

404 Not Found

Error: LISTING_NOT_FOUND

404 Not Found

Error: IMAGE_NOT_FOUND

### 13) Reorder listing images

`PATCH /v1/listings/{id}/images/reorder`

#### Permissions

LISTING_UPDATE_OWN

#### Request

```json
{
  "images": [
    {
      "id": "uuid",
      "sort_order": 1
    },
    {
      "id": "uuid",
      "sort_order": 2
    },
    {
      "id": "uuid",
      "sort_order": 3
    }
  ]
}
```

#### Response

```json
{
  "data": {
    "images": [
      {
        "id": "uuid",
        "url": "https://...",
        "sort_order": 1
      },
      {
        "id": "uuid",
        "url": "https://...",
        "sort_order": 2
      },
      {
        "id": "uuid",
        "url": "https://...",
        "sort_order": 3
      }
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

404 Not Found

Error: LISTING_NOT_FOUND

422 Unprocessable Entity

Error: IMAGE_ID_MISMATCH ← submitted image IDs do not match listing's images

### 14) Get own listings

`GET /v1/users/me/listings`

#### Parameters

| Parameter | Default | Constraints | Description |
| --- | --- | --- | --- |
| page | 1 | ≥ 1 | Page number |
| per_page | 20 | 1 – 100 | Results per page |
| status | ALL | DRAFT, ACTIVE, SOLD, TAKEN_DOWN, ALL | Filter by status |

#### Permissions

LISTING_READ

Authenticated sellers only

#### Request

No body

#### Response

Paginated listing list — same shape as GET /v1/listings but includes all statuses visible to the owner

#### Success

200 OK

#### Errors

401 Unauthorized

Error: INVALID_TOKEN

### 15) Get seller’s public listings

`GET /v1/users/{id}/listings`

#### Parameters

| Parameter | Default | Constraints | Description |
| --- | --- | --- | --- |
| page | 1 | ≥ 1 | Page number |
| per_page | 20 | 1 – 100 | Results per page |
| status | — | Valid UUID | Filter by category |

#### Permissions

None — returns `ACTIVE` listings only

#### Request

No body

#### Response

Paginated listing list — same shape as GET /v1/listings

#### Success

200 OK

#### Errors

404 Not Found

Error: PROFILE_NOT_FOUND ← seller does not exist or is not a seller
