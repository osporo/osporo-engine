# Moderation API

Source: **Osporo API v1.docx**, Moderation Domain. Converted and checked against the repository on 2026-10-19.

**Status:** Planned API; no controller implements these routes.

Read [API standards](../API_STANDARDS.md) for the current wire contract and shared rules, and the [domain index](README.md) for source interpretation. All routes remain under `/v1`. The endpoint descriptions and examples in **Source API design** document the original design, not proof of availability.

## Implementation notes and open decisions

No moderation controller, queue service, report service, or configurable filter implementation exposes these routes. All queue actions, automatic suspensions, and configuration changes below remain planned.

Open details from the source:

- The queue parameter table appears copied from orders: `role=BUYER` and payment/order statuses conflict with the queue response's `PENDING` status. Retained for review; these are not settled moderation filters.
- Configuration appears at both `/v1/moderation/config` here and `/v1/tenant/moderation-config` in [tenants](tenants.md), with different response fields. No canonical route or alias policy is decided by this conversion.
- Approving a blocked message says to deliver it later; reconcile that with the rewrite-before-send flow in [ADR-010](../adrs/ADR-010-pre-send-message-filtering.md). Only blocked content and sender may be visible to reviewers, not full thread history.
- Rejection behavior describes message senders but queue items may also be listing/user reports. Define review side effects for each item type and avoid treating a report as proof of a violation.
- Report submission says any authenticated user, while also requiring `REPORT_CREATE`; the permission requirement must be explicit in implementation.
- Automatic suspension needs an explicit policy consistent with the user hierarchy and tenant boundaries. Review and configuration permissions remain distinct under [ADR-011](../adrs/ADR-011-moderation-permission-split.md).

Editorial corrections: a stray tab in the single-item path and the `Successs` heading are repaired.

## Endpoint index

| Endpoint | Operation |
| --- | --- |
| `GET /v1/moderation/queue` | [1) Get moderation queue](#1-get-moderation-queue) |
| `GET /v1/moderation/queue/{itemId}` | [2) Get single queue item](#2-get-single-queue-item) |
| `POST /v1/moderation/queue/{itemId}/approve` | [3) Approve queue item](#3-approve-queue-item) |
| `POST /v1/moderation/queue/{itemId}/reject` | [4) Reject queue item](#4-reject-queue-item) |
| `POST /v1/reports` | [5) Submit a report](#5-submit-a-report) |
| `GET /v1/moderation/config` | [6) Get moderation config](#6-get-moderation-config) |
| `PATCH /v1/moderation/config` | [7) Update moderation config](#7-update-moderation-config) |

## Source API design

## Purpose

- Allow authorised staff to review and action blocked content
- Allow any user to submit reports against listings or users
- Allow marketplace owners to configure moderation rules

## Endpoints

### 1) Get moderation queue

`GET /v1/moderation/queue`

#### Parameters

| Parameter | Default | Constraints | Description |
| --- | --- | --- | --- |
| page | 1 | ≥ 1 | Page number |
| per_page | 20 | 1 - 100 | Results per page |
| role | BUYER | BUYER, SELLER | View as buyer or seller |
| status | ALL | PENDING_PAYMENT, PAID, SHIPPED, IN_PROGRESS, COMPLETED, CANCELLED, REFUNDED, ALL | Filter by status |

#### Permissions

MODERATION_REVIEW

#### Request

No body

#### Response

```json
{
  "data": [
    {
      "id": "uuid",
      "item_type": "BLOCKED_MESSAGE",
      "content": "My bank account number is 1234567890.",
      "reason": "BANK_ACCOUNT_NUMBER",
      "sender": {
        "id": "uuid",
        "display_name": "Mark Doe"
      },
      "status": "PENDING",
      "created_at": "2026-03-18T11:30:00Z"
    }
  ],
  "meta": {
    "request_id": "uuid",
    "pagination": {
      "page": 1,
      "per_page": 20,
      "total": 3,
      "total_pages": 1
    }
  }
}
```

#### Success

200 OK

#### Errors

403 Forbidden

Error: INSUFFICIENT_PERMISSIONS

### 2) Get single queue item

`GET /v1/moderation/queue/{itemId}`

#### Permissions

MODERATION_REVIEW

#### Request

No body

#### Response

```json
{
  "data": {
    "id": "uuid",
    "item_type": "BLOCKED_MESSAGE",
    "content": "My bank account number is 1234567890.",
    "reason": "BANK_ACCOUNT_NUMBER",
    "sender": {
      "id": "uuid",
      "display_name": "Mark Doe"
    },
    "status": "PENDING",
    "created_at": "2026-03-18T11:30:00Z"
  },
  "meta": {
    "request_id": "uuid"
  }
}
```

No thread context is returned. The moderator sees only the blocked content and the sender — nothing else.

#### Success

200 OK

#### Errors

403 Forbidden

Error: INSUFFICIENT_PERMISSIONS

404 Not Found

Error: MODERATION_ITEM_NOT_FOUND

### 3) Approve queue item

`POST /v1/moderation/queue/{itemId}/approve`

#### Permissions

MODERATION_REVIEW

Approves a blocked message — delivers it to a thread. For flagged listings or reported user, marks the report as reviewed with no action taken.

#### Request

```json
{
  "note": "False positive — context is legitimate."
}
```

#### Response

```json
{
  "data": {
    "id": "uuid",
    "status": "APPROVED",
    "reviewed_by": "uuid",
    "reviewed_at": "2026-03-18T12:00:00Z",
    "note": "False positive — context is legitimate."
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

Error: MODERATION_ITEM_NOT_FOUND

422 Unprocessable Entity

Error: MODERATION_ITEM_ALREADY_REVIEWED

### 4) Reject queue item

`POST /v1/moderation/queue/{itemId}/reject`

#### Permissions

MODERATION_REVIEW

Rejects the blocked content — it is permanently dropped. Increments the sender's violation count. If the violation count reaches the tenant's auto_suspend_threshold, the sender is automatically suspended.

#### Request

```json
{
  "note": "Confirmed attempt to move payment off platform."
}
```

#### Response

```json
{
  "data": {
    "id": "uuid",
    "status": "REJECTED",
    "reviewed_by": "uuid",
    "reviewed_at": "2026-03-18T12:05:00Z",
    "note": "Confirmed attempt to move payment off platform.",
    "sender_violation_count": 2,
    "sender_auto_suspended": false
  },
  "meta": {
    "request_id": "uuid"
  }
}
```

`sender_auto_suspended` tells the reviewer whether the rejection triggered an automatic suspension so they are not surprised when they next look up that user.

#### Success

200 OK

#### Errors

403 Forbidden

Error: INSUFFICIENT_PERMISSIONS

404 Not Found

Error: MODERATION_ITEM_NOT_FOUND

422 Unprocessable Entity

Error: MODERATION_ITEM_ALREADY_REVIEWED

### 5) Submit a report

`POST /v1/reports`

#### Permissions

REPORT_CREATE

Any authenticated user can submit a report against a listing or another user. Reports feed into the moderation queue if require_review is enabled on the tenant.

#### Request

```json
{
  "target_type": "LISTING",
  "target_id": "uuid",
  "reason": "PROHIBITED_ITEM",
  "details": "This listing is selling counterfeit goods."
}
```

target_type is either LISTING or USER.

#### Response

```json
{
  "data": {
    "id": "uuid",
    "target_type": "LISTING",
    "target_id": "uuid",
    "reason": "PROHIBITED_ITEM",
    "details": "This listing is selling counterfeit goods.",
    "status": "PENDING",
    "created_at": "2026-03-18T12:10:00Z"
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

Error: LISTING_NOT_FOUND           ←  if target_type is LISTING

404 Not Found

Error: PROFILE_NOT_FOUND           ←  if target_type is USER

422 Unprocessable Entity

Error: REPORT_ALREADY_SUBMITTED    ←  caller has already submitted a report against this target

### 6) Get moderation config

`GET /v1/moderation/config`

#### Permissions

MODERATION_CONFIG

#### Request

No body

#### Response

```json
{
  "data": {
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

### 7) Update moderation config

`PATCH /v1/moderation/config`

#### Permissions

MODERATION_CONFIG

All fields optional — only supplied fields are updated

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

Only the filter rules included in the request are updated. Rules not included remain unchanged.

#### Response

Full moderation config — same shape as GET /v1/moderation/config

#### Success

200 OK

#### Errors

403 Forbidden

Error: INSUFFICIENT_PERMISSIONS

422 Unprocessable Entity

Error: INVALID_FILTER_RULE         ←  rule_name is not recognised by the engine
