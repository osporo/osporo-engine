# Escrow API

Source: **Osporo API v1.docx**, Escrow Domain. Converted and checked against the repository on 2026-10-19.

**Status:** Planned API; no controller implements these routes.

Read [API standards](../API_STANDARDS.md) for the current wire contract and shared rules, and the [domain index](README.md) for source interpretation. All routes remain under `/v1`. The endpoint descriptions and examples in **Source API design** document the original design, not proof of availability.

## Implementation notes and open decisions

No escrow controller, hold service, release scheduler, or provider transfer integration implements these routes. “Escrow” below names the proposed application hold workflow; it is not evidence of a live financial or legally regulated escrow service.

Open details from the source:

- Manual release reuses `ORDER_REFUND`; confirm whether that permission should authorize payout as well as refunds before implementing it.
- Manual release has a response example but no success status in the source. Do not infer one as a settled contract.
- Define release eligibility, duplicate/concurrent release handling, failure recovery, and how refund or dispute states block release. The proposed idempotency header is not currently enforced.
- Timers and hold durations must be reconciled with [orders](orders.md) and validated provider capabilities.

Related design: [ADR-004](../adrs/ADR-004-stripe-connect-for-payments-and-escrow.md).

## Endpoint index

| Endpoint | Operation |
| --- | --- |
| `GET /v1/escrow/{orderId}` | [1) Get escrow details](#1-get-escrow-details) |
| `POST /v1/escrow/{orderId}/release` | [2) Release escrow manually](#2-release-escrow-manually) |

## Source API design

## Purpose

- Allow participants to view escrow hold status for their orders
- Allow operators to manually release escrow when necessary

## Endpoints

### 1) Get escrow details

`GET /v1/escrow/{orderId}`

#### Permissions

ORDER_READ_OWN for buyer or seller on that order. ORDER_READ_ANY for operators.

Non-participants receive 404.

#### Request

No body

#### Response

```json
{
  "data": {
    "id": "uuid",
    "order_id": "uuid",
    "status": "HOLDING",
    "amount": "4275.00",
    "currency": "ZAR",
    "release_after": "2026-03-25T09:02:14Z",
    "released_at": null,
    "created_at": "2026-03-18T09:02:14Z"
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

Error: ORDER_NOT_FOUND

404 Not Found

Error: ESCROW_NOT_FOUND ← order exists but no escrow hold was created

### 2) Release escrow manually

`POST /v1/escrow/{orderId}/release`

#### Permissions

ORDER_REFUND — operators only

Manually releases the escrow hold to the seller ahead of the auto-release date. Used when an operator is satisfied the transaction is complete but the auto-release timer has not yet fired. Triggers a Stripe transfer to the seller.

#### Headers

```http
Idempotency-Key: <client-generated-uuid> ← required
```

#### Request

```json
{
  "reason": "Buyer confirmed receipt via support channel."
}
```

#### Response

```json
{
  "data": {
    "id": "uuid",
    "order_id": "uuid",
    "status": "RELEASED",
    "amount": "4275.00",
    "currency": "ZAR",
    "released_at": "2026-03-19T10:00:00Z",
    "release_reason": "Buyer confirmed receipt via support channel."
  },
  "meta": {
    "request_id": "uuid"
  }
}
```

#### Errors

403 Forbidden

Error: INSUFFICIENT_PERMISSIONS

404 Not Found

Error: ESCROW_NOT_FOUND

422 Unprocessable Entity

Error: ESCROW_ALREADY_RELEASED

422 Unprocessable Entity

Error: ESCROW_ALREADY_REFUNDED
