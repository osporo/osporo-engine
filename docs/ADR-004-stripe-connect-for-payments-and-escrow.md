# ADR-004: Stripe Connect for Payments and Escrow

**Status:** Accepted  
**Date:** 2026-03-12  
**Author:** Wahina Prince Chabalala  
**Deciders:** Wahina Prince Chabalala  

---

## Context

Osporo is a marketplace platform, not a storefront. The defining financial
characteristic of a marketplace is that money flows from a buyer, through the
platform, to a seller — and the platform takes a commission along the way. This
is structurally different from a standard e-commerce payment where money simply
moves from buyer to merchant.

This payment topology — often called a split payment or marketplace payment —
requires specific infrastructure:

1. The buyer's payment is captured and held
2. The platform deducts its commission
3. The seller receives the remainder
4. Funds are held in escrow until the transaction is confirmed, protecting the buyer

Building this infrastructure from scratch — PCI compliance, fund holding, split
payouts, seller onboarding with KYC, dispute management, international payouts —
is a multi-year engineering effort and a regulated financial activity. The decision
of whether to build or buy this capability is one of the highest-stakes decisions
in the platform.

---

## Decision

**Osporo will use Stripe Connect with delayed transfers as the default
implementation of an abstracted PaymentProvider interface.**

The payment module exposes a `PaymentProvider` interface that all order
and escrow logic calls exclusively. No domain code calls Stripe directly.
Stripe Connect is the default implementation of this interface. Additional
providers — PayFast, Adyen, or others — can be implemented against the
same interface and configured per tenant via TenantSettings.
```java
public interface PaymentProvider {
    CheckoutSession createCheckout(CheckoutRequest request);
    void triggerPayout(PayoutRequest request);
    void issueRefund(RefundRequest request);
    WebhookEvent parseWebhook(String payload, String signature);
    SellerOnboardingUrl onboardSeller(String sellerId);
}
```

The active provider is resolved from TenantSettings at runtime. A South
African marketplace tenant can configure PayFast. A global tenant
configures Stripe. The engine does not change — only the configuration
does.

Stripe Connect with delayed transfers implements the default flow:
Buyer pays → Stripe Checkout session
→ payment_intent captured to platform Stripe account
→ Stripe webhook: payment_intent.succeeded
→ Order status: PENDING_PAYMENT → PAID
→ EscrowHold created (status: HOLDING)
Order completes → platform triggers Stripe Transfer
→ transfer amount = order.seller_payout
→ application_fee = order.platform_fee
→ EscrowHold status: HOLDING → RELEASED
→ Order status: → COMPLETED

No card data touches Osporo's servers at any point. The platform never
handles, stores, or transmits raw payment credentials.

---

## Reasons For This Decision

### 1. PCI compliance is not optional and not cheap to build

Any system that handles raw card data must achieve PCI DSS compliance — an audit
process that costs tens of thousands of dollars annually for small companies and
requires continuous security controls. Stripe's architecture means Osporo operates
at PCI SAQ-A level — the lowest possible compliance burden — because card data
goes directly from the browser to Stripe's servers without ever touching Osporo's
infrastructure.

Building a payment system that requires PCI DSS SAQ-D compliance to replace this
is not a viable option for a bootstrapped product.

### 2. Marketplace payments are a solved, regulated problem

Splitting payments between a platform and multiple sellers, holding funds in
escrow, and disbursing payouts to sellers in multiple countries involves money
transmission regulation. In many jurisdictions, facilitating these flows without
the appropriate licences is illegal. Stripe Connect holds the necessary licences
across all markets it operates in. Using Stripe means Osporo operates under
Stripe's licences, not as an independent money transmitter.

The alternative — applying for money transmission licences in each jurisdiction
— is a multi-year legal process and not a viable path for a product at this stage.

### 3. Seller onboarding with KYC is handled by Stripe

Marketplace regulations require identity verification of sellers receiving payouts
above certain thresholds. Stripe Connect Express handles KYC (Know Your Customer)
verification of sellers through its onboarding flow. Osporo gates listing
purchases behind `stripe_connect_id NOT NULL` on the seller — if a seller hasn't
completed Stripe Connect onboarding, their listings cannot be purchased.

Building KYC infrastructure from scratch would require integrating identity
verification providers, storing and processing identity documents, and managing
regulatory requirements per jurisdiction. Stripe's Connect Express flow handles
all of this.

### 4. Delayed transfers implement escrow without custom fund holding

Stripe's delayed transfer capability means the platform captures the buyer's
payment immediately but does not transfer to the seller until the platform
explicitly initiates the transfer. This is Osporo's escrow model — funds are held
at the Stripe platform level until the order is confirmed.

The `EscrowHold` table in Osporo's database is an application-level record that
tracks the state of this hold and drives the release logic. The actual fund holding
is Stripe's responsibility. Building fund holding infrastructure independently would
require a bank relationship, reserve accounts, and financial regulatory compliance.

### 5. Webhook-driven state machine

Stripe's webhook system provides reliable delivery of payment events to the
platform. The order state machine is driven by these webhooks:

```
payment_intent.succeeded    → Order: PENDING_PAYMENT → PAID
payment_intent.payment_failed → Order: PENDING_PAYMENT → CANCELLED
account.updated             → Seller Connect onboarding status change
```

Webhook handlers are idempotent — duplicate delivery of the same event produces
no side effects. Stripe's webhook delivery includes retry logic with exponential
backoff and a webhook dashboard for debugging failed deliveries.

---

## Alternatives Considered

### PayFast (South African payment provider)

**Deferred, not rejected.** PayFast does not have a marketplace Connect
equivalent that handles split payments, seller onboarding, and
international payouts at the level Stripe does. It is not viable as the
default global provider. However, the PaymentProvider abstraction
exists specifically to accommodate regional providers like PayFast. A
PayFastPaymentProvider implementation is a concrete post-MVP addition
that makes Osporo viable for South African tenants who cannot or prefer
not to use Stripe. This is a first-class planned extension, not an edge
case.

### PayPal Marketplace Payments

**Rejected.** PayPal's marketplace API is mature but significantly more complex
to integrate correctly than Stripe Connect. Dispute resolution, chargeback
handling, and seller protection rules are less developer-friendly. Developer
experience and documentation quality strongly favour Stripe for a
developer-facing infrastructure product.

### Building a custom payment and escrow layer

**Rejected.** Requires PCI DSS SAQ-D compliance, money transmission licences per
jurisdiction, banking relationships for fund holding, and KYC infrastructure for
seller verification. Estimated multi-year effort and significant legal cost before
the first transaction can be processed. The risk profile is entirely inappropriate
for an early-stage product.

### Adyen Marketpay

**Rejected.** Adyen's marketplace product is enterprise-grade and priced
accordingly. Minimum volume requirements and contract-based pricing make it
unsuitable for a product at inception. A valid consideration at significant scale.

---

## Consequences

### Positive

- PCI compliance at SAQ-A level. No card data on Osporo's infrastructure.
- Stripe holds money transmission licences. Osporo is not a money transmitter.
- KYC and seller identity verification handled by Stripe Connect Express.
- Delayed transfers implement escrow without custom fund holding infrastructure.
- Reliable webhook delivery with retry logic and debugging tooling.
- International payouts to sellers in supported countries without Osporo
  managing currency conversion or cross-border transfer compliance.

### Negative

- Stripe's fees apply to every transaction. Platform commission must be modelled
  net of Stripe fees. At high volume, Stripe's per-transaction cost becomes a
  meaningful line item and direct payment processor relationships may become
  justified.
- Osporo's escrow behaviour is constrained by what Stripe's delayed transfer
  model supports. Complex escrow scenarios (milestone-based releases, partial
  releases) require workarounds or are not directly supported.
- Seller and buyer experience for disputes and chargebacks is partially governed
  by Stripe's policies, not entirely by Osporo's. Stripe can make policy changes
  that affect the platform's behaviour.
- Stripe is not available in all countries. Tenants in unsupported markets cannot
  use the payment infrastructure. This limits addressable market in certain regions.
- Stripe is not available in all countries. Tenants in unsupported
  markets cannot use the default payment provider. Mitigated by the
  PaymentProvider abstraction — regional providers can be implemented
  and configured per tenant without changes to the engine's domain logic.

---

## Revisit Criteria

This decision should be revisited when:

- Transaction volume reaches a level where Stripe's per-transaction fees
  justify the engineering and legal cost of a direct processor relationship
- A significant tenant segment requires a payment provider not yet
  implemented, at which point a new PaymentProvider implementation is the
  correct response — not a revision of this ADR
- Stripe makes a policy change that materially impacts the platform's
  escrow or commission model

---

## References

- ADR-005: Build vs Buy Decisions (payment infrastructure in build vs buy context)
- Stripe Connect documentation — Collect payments then pay out
- Stripe Connect Express onboarding documentation
- Stripe webhook best practices documentation
