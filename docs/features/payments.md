# Payments API

Source: **Osporo API v1.docx**, Payment Domain. Converted and checked against the repository on 2026-10-19.

**Status:** Planned API; no controller implements these routes.

Read [API standards](../API_STANDARDS.md) for the current wire contract and shared rules, and the [domain index](README.md) for source interpretation. All routes remain under `/v1`. The endpoint descriptions and examples in **Source API design** document the original design, not proof of availability.

## Implementation notes and open decisions

No payment controller, provider adapter, onboarding workflow, or payment-summary service implements these routes. Stripe URLs and provider fields are examples, not operational integrations.

Open details from the source:

- Seller-only access is expressed with `LISTING_CREATE`; confirm the intended capability before implementation, since tenant-configured permissions need not map to one role.
- Onboarding is described as called once, but expired-link replacement and incomplete-onboarding retries are unspecified.
- Summary date bounds, inclusivity, default period, and handling of multiple currencies require definition. The source only defines the `from > to` error.
- Provider availability and permitted delayed-transfer behavior must be validated for a real deployment. This documentation does not establish that Stripe or another provider supports a particular tenant or jurisdiction.

Related designs: [orders and webhook events](orders.md), [escrow](escrow.md), [ADR-004](../adrs/ADR-004-stripe-connect-for-payments-and-escrow.md).

## Endpoint index

| Endpoint | Operation |
| --- | --- |
| `POST /v1/payments/onboarding` | [1) Get onboarding URL](#1-get-onboarding-url) |
| `GET /v1/payments/onboarding/status` | [2) Get onboarding status](#2-get-onboarding-status) |
| `GET /v1/payments/me` | [3) Get own payment summary](#3-get-own-payment-summary) |
| `GET /v1/payments/{orderId}` | [4) Get payment details for an order](#4-get-payment-details-for-an-order) |

## Source API design

## Purpose

- Allow sellers to onboard onto the payment provider
- Allow sellers to view their earnings and payout status
- Allow any order participant to view payment details for their order

## Endpoints

### 1) Get onboarding URL

`POST /v1/payments/onboarding`

#### Permissions

LISTING_CREATE — sellers only

Generates a Stripe Connect onboarding URL. The seller is redirected to this URL to complete identity verification and bank account setup. Called once per seller during their initial setup.

#### Request

No body

#### Response

```json
{
  "data": {
    "onboarding_url": "https://connect.stripe.com/setup/...",
    "expires_at": "2026-03-18T10:00:00Z"
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

422 Unprocessable Entity

Error: SELLER_ALREADY_ONBOARDED    ←  seller has already completed onboarding

### 2) Get onboarding status

`GET /v1/payments/onboarding/status`

#### Permissions

LISTING_CREATE — sellers only

Returns the seller's current Stripe Connect onboarding status. Used by the frontend to determine whether a seller can receive payouts before allowing them to publish listings.

#### Request

No body

#### Response

```json
{
  "data": {
    "onboarded": true,
    "payouts_enabled": true,
    "charges_enabled": true,
    "details_submitted": true
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

### 3) Get own payment summary

`GET /v1/payments/me`

| Parameter | Default | Constraints | Description |
| --- | --- | --- | --- |
| from | — | ISO 8601 date | Start of period |
| to | — | ISO 8601 date | End of period |

#### Permissions

LISTING_CREATE — sellers only

Returns an earnings summary for the authenticated seller. Shows total earned, pending escrow, and available for payout within the requested period.

#### Request

No body

#### Response

```json
{
  "data": {
    "currency": "ZAR",
    "total_earned": "42750.00",
    "pending_escrow": "8550.00",
    "available_for_payout": "34200.00",
    "period": {
      "from": "2026-01-01T00:00:00Z",
      "to": "2026-03-18T00:00:00Z"
    }
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

400 Bad Request

Error: INVALID_PARAMETER           ←  from is after to

### 4) Get payment details for an order

`GET /v1/payments/{orderId}`

#### Permissions

ORDER_READ_OWN for buyer or seller on that order.

ORDER_READ_ANY for operators.

Non-participants receive 404.

#### Request

No body

#### Response

```json
{
  "data": {
    "order_id": "uuid",
    "status": "CAPTURED",
    "subtotal": "4500.00",
    "platform_fee": "225.00",
    "seller_payout": "4275.00",
    "currency": "ZAR",
    "payment_method": "card",
    "payment_provider": "STRIPE",
    "stripe_payment_intent_id": "pi_...",
    "paid_at": "2026-03-18T09:02:14Z"
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
