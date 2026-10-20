# Orders API

Source: **Osporo API v1.docx**, Order Domain. Converted and checked against the repository on 2026-10-19.

**Status:** Planned API; no controller implements these routes.

Read [API standards](../API_STANDARDS.md) for the current wire contract and shared rules, and the [domain index](README.md) for source interpretation. All routes remain under `/v1`. The endpoint descriptions and examples in **Source API design** document the original design, not proof of availability.

## Implementation notes and open decisions

No order controller, order state machine, checkout service, or Stripe webhook handler implements these routes. The source state diagram, event log, scheduling windows, and provider interactions remain planned.

Open details from the source:

- Create-order request is explicitly `/* TO DO */`; its fields, validation, delivery selection, and address requirements are unspecified. The response example is not a substitute for that request contract.
- The diagram calls `COMPLETED` terminal while also allowing `COMPLETED → REFUNDED`. Its PAID cancellation arrow permits buyers, but the cancel endpoint restricts paid cancellation to operators. Both source descriptions are retained pending a decision.
- `IN_PERSON` fulfillment is absent from this state diagram although it appears in ADR-007. Service completion and escrow-release timing also need one consistent definition.
- `LISTING_NOT_ACTIVE` is not in the current `ErrorCode` enum. Existing constants do not establish implemented errors or transitions.
- Lifecycle mutations reuse read permissions in the design. Before implementation, confirm the participant/action policy and tenant ownership checks; do not infer mutation authority from read access alone.
- The source webhook URL includes `{tenantId}` and has no JWT. Its tenant routing is an exception to the ordinary authenticated-domain rule and must be bound to verified provider configuration and event ownership, not trusted solely from the URL.
- The source says to return 200 for every webhook outcome. Do not treat that as an approved production retry policy: before implementation, define signature-failure handling and durable acceptance/retry behavior. Acknowledging a transient processing failure before durable acceptance can lose an event. Preserve the source proposal below for review.
- Idempotency headers are planned, not enforced; implementation must define scoped keys, duplicate payload handling, concurrency, and transaction boundaries.

Related designs: [payments](payments.md), [escrow](escrow.md), [ADR-004](../adrs/ADR-004-stripe-connect-for-payments-and-escrow.md).

## Endpoint index

| Endpoint | Operation |
| --- | --- |
| `POST /v1/orders` | [1) Create order](#1-create-order) |
| `GET /v1/orders/{id}` | [2) Get single order](#2-get-single-order) |
| `GET /v1/users/me/orders` | [3) Get own orders](#3-get-own-orders) |
| `GET /v1/orders` | [4) Get all orders (operator)](#4-get-all-orders-operator) |
| `POST /v1/orders/{id}/cancel` | [5) Cancel order](#5-cancel-order) |
| `POST /v1/orders/{id}/ship` | [6) Ship order](#6-ship-order) |
| `POST /v1/orders/{id}/mark-delivered` | [7) Mark order delivered](#7-mark-order-delivered) |
| `POST /v1/orders/{id}/confirm` | [8) Confirm order received](#8-confirm-order-received) |
| `POST /v1/orders/{id}/refund` | [9) Refund order](#9-refund-order) |
| `POST /v1/webhooks/stripe/{tenantId}` | [10) Stripe webhook receiver](#10-stripe-webhook-receiver) |

## Source API design

## Purpose

- Allow buyers to place and manage orders
- Allow sellers to fulfil orders
- Allow operators to oversee and intervene on orders
- Drive the order state machine based on delivery method
- Handle Stripe webhook events that trigger state transitions

## State Machine Reference

Before the endpoints, the state machine needs to be explicit. Every lifecycle endpoint maps to a transition.

```text
PENDING_PAYMENT  ──── payment confirmed (Stripe webhook) ────► PAID
PENDING_PAYMENT  ──── buyer cancels ─────────────────────────► CANCELLED

PAID  ──── delivery: SHIPPING ───────────────────────────────► SHIPPED
PAID  ──── delivery: DIGITAL_DELIVERY ───────────────────────► COMPLETED (automatic)
PAID  ──── delivery: SERVICE_COMPLETION ─────────────────────► IN_PROGRESS
PAID  ──── buyer/operator cancels ───────────────────────────► REFUNDED

SHIPPED  ──── buyer confirms receipt ────────────────────────► COMPLETED
SHIPPED  ──── auto-release after 7 days ─────────────────────► COMPLETED

IN_PROGRESS  ──── seller marks delivered ────────────────────► COMPLETED
IN_PROGRESS  ──── auto-release after configured period ──────► COMPLETED

COMPLETED  ──── operator refunds ────────────────────────────► REFUNDED
```

Terminal states: COMPLETED, CANCELLED, REFUNDED

## Endpoints

### 1) Create order

`POST /v1/orders`

#### Permissions

ORDER_CREATE

Creating an order initialises the record and creates a Stripe checkout session. The order is in PENDING_PAYMENT until Stripe confirms payment via webhook.

#### Headers

```http
Idempotency-Key: <client-generated-uuid> ← required
```

#### Request

**Unspecified in source:** the order-creation request body is marked `/* TO DO */`.

#### Response

```json
{
  "data": {
    "id": "uuid",
    "listing_id": "uuid",
    "buyer_id": "uuid",
    "seller_id": "uuid",
    "status": "PENDING_PAYMENT",
    "delivery_method": "SHIPPING",
    "subtotal": "4500.00",
    "platform_fee": "225.00",
    "seller_payout": "4275.00",
    "currency": "ZAR",
    "checkout_url": "https://checkout.stripe.com/...",
    "shipping_address": {
      "line_1": "12 Example Street",
      "line_2": null,
      "city": "Pretoria",
      "province": "Gauteng",
      "postal_code": "0001",
      "country": "ZA"
    },
    "created_at": "2026-03-18T09:00:00Z"
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

Error: LISTING_NOT_ACTIVE          ←  listing is not in ACTIVE status

422 Unprocessable Entity

Error: SELLER_NOT_ONBOARDED        ←  seller has not completed Stripe Connect onboarding

422 Unprocessable Entity

Error: CANNOT_ORDER_OWN_LISTING    ←  buyer and seller are the same user

422 Unprocessable Entity

Error: INVALID_DELIVERY_METHOD     ←  delivery method not supported by this listing type

### 2) Get single order

`GET /v1/orders/{id}`

#### Permissions

ORDER_READ_OWN for buyer or seller on that order. ORDER_READ_ANY for operators.

Non-participants receive 404, not 403 — same security-through-obscurity principle as the user profile endpoint.

#### Request

No body

#### Response

```json
{
  "data": {
    "id": "uuid",
    "listing": {
      "id": "uuid",
      "title": "Sony WH-1000XM5",
      "primary_image_url": "https://..."
    },
    "buyer": {
      "id": "uuid",
      "display_name": "Jane Doe"
    },
    "seller": {
      "id": "uuid",
      "display_name": "John Doe"
    },
    "status": "SHIPPED",
    "delivery_method": "SHIPPING",
    "subtotal": "4500.00",
    "platform_fee": "225.00",
    "seller_payout": "4275.00",
    "currency": "ZAR",
    "shipping_address": {
      "line_1": "12 Example Street",
      "line_2": null,
      "city": "Pretoria",
      "province": "Gauteng",
      "postal_code": "0001",
      "country": "ZA"
    },
    "events": [
      {
        "event_type": "ORDER_CREATED",
        "created_at": "2026-03-18T09:00:00Z"
      },
      {
        "event_type": "PAYMENT_CONFIRMED",
        "created_at": "2026-03-18T09:02:14Z"
      },
      {
        "event_type": "ORDER_SHIPPED",
        "created_at": "2026-03-18T10:30:00Z"
      }
    ],
    "created_at": "2026-03-18T09:00:00Z",
    "updated_at": "2026-03-18T10:30:00Z"
  },
  "meta": {
    "request_id": "uuid"
  }
}
```

The `events` array is the `OrderEvent` audit log — append-only, surfaced directly on the order response so clients can show a timeline without a separate request.

#### Success

200 OK

#### Errors

404 Not Found

Error: ORDER_NOT_FOUND

### 3) Get own orders

`GET /v1/users/me/orders`

#### Parameters

| Parameter | Default | Constraints | Description |
| --- | --- | --- | --- |
| page | 1 | ≥ 1 | Page number |
| per_page | 20 | 1 - 100 | Results per page |
| role | BUYER | BUYER, SELLER | View as buyer or seller |
| status | ALL | PENDING_PAYMENT, PAID, SHIPPED, IN_PROGRESS, COMPLETED, CANCELLED, REFUNDED, ALL | Filter by status |
| sort | created_at | created_at, updated_at | Sort field |
| order | desc | asc, desc | Sort direction |

#### Permissions

ORDER_READ_OWN

The role parameter determines whether the caller sees orders where they are the buyer or orders where they are the seller. A user who is both buyer and seller on the tenant uses this parameter to switch views.

#### Request

No body

#### Response

```json
{
  "data": [
    {
      "id": "uuid",
      "listing": {
        "id": "uuid",
        "title": "Sony WH-1000XM5",
        "primary_image_url": "https://..."
      },
      "counterparty": {
        "id": "uuid",
        "display_name": "John Doe"
      },
      "status": "SHIPPED",
      "delivery_method": "SHIPPING",
      "subtotal": "4500.00",
      "currency": "ZAR",
      "created_at": "2026-03-18T09:00:00Z"
    }
  ],
  "meta": {
    "request_id": "uuid",
    "pagination": {
      "page": 1,
      "per_page": 20,
      "total": 8,
      "total_pages": 1
    }
  }
}
```

`counterparty` is the other party in the transaction — the seller when viewing as buyer, the buyer when viewing as seller. This avoids duplicating buyer/seller fields when only one is relevant per view.

#### Success

200 OK

#### Errors

401 Unauthorized

Error: INVALID_TOKEN

### 4) Get all orders (operator)

`GET /v1/orders`

#### Parameters

| Parameter | Default | Constraints | Description |
| --- | --- | --- | --- |
| page | 1 | ≥ 1 | Page number |
| per_page | 20 | 1 - 100 | Results per page |
| status | ALL | PENDING_PAYMENT, PAID, SHIPPED, IN_PROGRESS, COMPLETED, CANCELLED, REFUNDED, ALL | Filter by status |
| buyer_id | — | Valid UUID | Filter by buyer |
| seller_id | — | Valid UUID | Filter by seller |
| sort | created_at | created_at, updated_at | Sort field |
| order | desc | asc, desc | Sort direction |

#### Permissions

ORDER_READ_ANY

#### Request

No body

#### Response

Same shape as GET /v1/users/me/orders but includes both buyer and seller fields rather than a single counterparty.

#### Success

200 OK

#### Errors

403 Forbidden

Error: INSUFFICIENT_PERMISSIONS

### 5) Cancel order

`POST /v1/orders/{id}/cancel`

#### Permissions

ORDER_READ_OWN for buyer. ORDER_READ_ANY for operator.

A buyer can only cancel while the order is in PENDING_PAYMENT. Once payment is confirmed, cancellation requires operator intervention. Operators can cancel a PAID order, which triggers a refund automatically.

#### Request

```json
{
  "reason": "Changed my mind before completing payment."
}
```

#### Response

```json
{
  "data": {
    "id": "uuid",
    "status": "CANCELLED",
    "cancelled_at": "2026-03-18T09:15:00Z",
    "cancellation_reason": "Changed my mind before completing payment."
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

Error: ORDER_NOT_FOUND

422 Unprocessable Entity

Error: INVALID_ORDER_TRANSITION    ←  order is not in a cancellable state for this caller

422 Unprocessable Entity

Error: ORDER_ALREADY_CANCELLED

### 6) Ship order

`POST /v1/orders/{id}/ship`

#### Permissions

ORDER_READ_OWN — seller only

Transitions order from PAID to SHIPPED. Only valid for orders with delivery_method: SHIPPING.

#### Request

```json
{
  "tracking_number": "CK123456789ZA",
  "courier": "Courier Guy"
}
```

#### Response

```json
{
  "data": {
    "id": "uuid",
    "status": "SHIPPED",
    "tracking_number": "CK123456789ZA",
    "courier": "Courier Guy",
    "shipped_at": "2026-03-18T11:00:00Z"
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

Error: ORDER_NOT_FOUND

422 Unprocessable Entity

Error: INVALID_ORDER_TRANSITION    ←  order is not in PAID status

422 Unprocessable Entity

Error: INVALID_DELIVERY_METHOD     ←  delivery method is not SHIPPING

### 7) Mark order delivered

`POST /v1/orders/{id}/mark-delivered`

#### Permissions

ORDER_READ_OWN — seller only

Transitions order from IN_PROGRESS to COMPLETED for SERVICE_COMPLETION delivery method. Signals to the buyer that the service has been delivered. Triggers the escrow auto-release timer if the buyer does not dispute within the configured window.

#### Request

```json
{
  "delivery_note": "Project files have been uploaded to the shared folder."
}
```

#### Response

```json
{
  "data": {
    "id": "uuid",
    "status": "COMPLETED",
    "delivery_note": "Project files have been uploaded to the shared folder.",
    "delivered_at": "2026-03-18T14:00:00Z"
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

Error: ORDER_NOT_FOUND

422 Unprocessable Entity

Error: INVALID_ORDER_TRANSITION    ←  order is not in IN_PROGRESS status

422 Unprocessable Entity

Error: INVALID_DELIVERY_METHOD     ←  delivery method is not SERVICE_COMPLETION

### 8) Confirm order received

`POST /v1/orders/{id}/confirm`

#### Permissions

ORDER_READ_OWN — buyer only

Buyer explicitly confirms they have received the order. Transitions SHIPPED to COMPLETED and triggers immediate escrow release rather than waiting for the 7-day auto-release window.

#### Request

No body

#### Response

```json
{
  "data": {
    "id": "uuid",
    "status": "COMPLETED",
    "confirmed_at": "2026-03-19T08:00:00Z"
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

Error: ORDER_NOT_FOUND

422 Unprocessable Entity

Error: INVALID_ORDER_TRANSITION    ←  order is not in SHIPPED status

### 9) Refund order

`POST /v1/orders/{id}/refund`

#### Permissions

ORDER_REFUND

Operator-initiated refund. Triggers a Stripe refund and transitions the order to REFUNDED. Escrow hold is released back to the buyer rather than to the seller.

#### Headers

```http
Idempotency-Key: <client-generated-uuid> ← required
```

#### Request

```json
{
  "reason": "ITEM_NOT_RECEIVED",
  "details": "Buyer reported item was not delivered after 14 days."
}
```

#### Response

```json
{
  "data": {
    "id": "uuid",
    "status": "REFUNDED",
    "refund_reason": "ITEM_NOT_RECEIVED",
    "refund_details": "Buyer reported item was not delivered after 14 days.",
    "refunded_at": "2026-03-20T09:00:00Z",
    "refund_amount": "4500.00",
    "currency": "ZAR"
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

Error: ORDER_NOT_FOUND

422 Unprocessable Entity

Error: REFUND_NOT_ELIGIBLE         ←  order is not in a refundable state

422 Unprocessable Entity

Error: ORDER_ALREADY_REFUNDED

### 10) Stripe webhook receiver

`POST /v1/webhooks/stripe/{tenantId}`

#### Permissions

None — validated by Stripe signature verification

#### Headers

```http
Stripe-Signature: t=…,v1=…
```

This endpoint is not called by clients — it is called by Stripe. It is documented here because it drives the most critical state transitions in the system.

#### Events handled

| Stripe Event | Order Transition |
| --- | --- |
| payment_intent.succeeded | PENDING_PAYMENT → PAID |
| payment_intent.payment_failed | PENDING_PAYMENT → CANCELLED |
| charge.refunded | COMPLETED or PAID → REFUNDED |
| account.updated | Seller onboarding status updated |

#### Response

`200 OK` with empty body — always. Stripe retries any non-200 response. The endpoint must return 200 even if the event is unrecognised or already processed.

#### Errors

Source proposal: internal errors are logged rather than returned to Stripe.

| Signature verification failure | → | log and return 200, do not process |
| --- | --- | --- |
| Duplicate event (idempotency) | → | log and return 200, do not re-process |
| Unrecognised event type | → | log and return 200, ignore |
