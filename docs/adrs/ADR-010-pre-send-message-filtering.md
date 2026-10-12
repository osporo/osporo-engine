# ADR-010: Pre-Send Message Filtering with Tenant-Configurable Moderation Rules

## Status
Accepted

## Date
2026-03-14

## Context

Marketplace messaging introduces a trust risk: participants may attempt to move transactions off-platform to avoid fees, circumvent escrow protection, or commit fraud. The most common vectors are sharing bank account numbers directly in chat, exchanging phone numbers to complete a cash transaction, or sending email addresses to continue negotiation outside the platform.

The conventional approach is to allow all messages through and flag suspicious content after delivery for moderator review. This is insufficient for financial fraud vectors. A seller who successfully delivers their banking details to a buyer has already achieved their goal — the damage is done whether or not the message is subsequently flagged and removed. Post-send flagging addresses moderation of policy violations but does not prevent the fraud it is designed to stop.

At the same time, the filtering mechanism must be configurable per tenant. A marketplace designed for in-person meetups — where exchanging phone numbers to arrange a pickup is a normal and legitimate part of the transaction — should not have phone number filtering active. A digital goods marketplace where all transactions complete on-platform has no reason to permit off-platform contact sharing. The same engine must serve both configurations without code changes.

## Decision

**Messages are filtered synchronously before persistence. A message that triggers an active filter rule is never written to the messages table and never delivered to the recipient.**

### Message Flow

```
User submits message
  → MessageService.send() called
  → FilterService.evaluate(content, tenantId) called synchronously
  → PASS: message persisted, delivered to thread
  → FAIL: ModerationQueue record created, message not persisted
           sender receives acknowledgement that message was not delivered
           (optional: reason at tenant's discretion)
```

The filter runs inside the same transaction as message persistence. If the filter blocks the message, the transaction commits only the ModerationQueue record. Nothing is written to the messages table.

### ModerationQueue Entity

```
ModerationQueue
───────────────
id               UUID        PK
tenant_id        UUID        FK → Tenant
item_type        ENUM        BLOCKED_MESSAGE, FLAGGED_LISTING, REPORTED_USER
content          TEXT        the blocked content only — no thread context
thread_id        UUID        FK → MessageThread, nullable
sender_id        UUID        FK → User
reason           VARCHAR     which filter rule triggered
status           ENUM        PENDING, APPROVED, REJECTED
reviewed_by      UUID        FK → User, nullable
reviewed_at      TIMESTAMP   nullable
created_at       TIMESTAMP
```

The `content` field stores only the text of the blocked message. No surrounding messages, no thread history, no participant context beyond `sender_id` is accessible to the moderator through this entity. This is enforced at the data layer — the moderation API endpoint returns only what this entity exposes, and this entity does not join to the thread's message history.

If a moderator approves a blocked message, the service writes it to the messages table and delivers it. If they reject it, the record remains as an audit trail and the sender's violation count is incremented on their User record for threshold-based auto-suspension.

### TenantModerationConfig Entity

```
TenantModerationConfig
──────────────────────
id                      UUID        PK
tenant_id               UUID        FK → Tenant
filter_rules            JSONB       array of {rule_name, pattern, enabled}
auto_suspend_threshold  INT         violations before automatic suspension
require_review          BOOLEAN     true: blocked messages enter review queue
                                    false: blocked messages are silently dropped
created_at              TIMESTAMP
updated_at              TIMESTAMP
```

The `filter_rules` JSONB array contains the active ruleset for the tenant. Osporo ships a default ruleset covering the most common fraud vectors. Tenants can enable, disable, or extend individual rules without code changes.

### Default Filter Rules

```
BANK_ACCOUNT_NUMBER    enabled by default
PHONE_NUMBER           enabled by default
EMAIL_ADDRESS          enabled by default
```

A marketplace for in-person meetups disables `PHONE_NUMBER`. All other rules remain active. The tenant does not require a deployment or code change to make this adjustment.

### require_review Flag

Some tenants want human review of all blocked content before it is permanently dropped — ensuring no legitimate message is lost to an overly aggressive filter. Others want fully automated filtering with no moderation queue overhead. The `require_review` flag supports both:

- `true`: blocked messages enter the `ModerationQueue` for human review. A moderator can approve or reject.
- `false`: blocked messages are dropped immediately. No ModerationQueue record is created. The sender is notified the message was not delivered.

## Alternatives Considered

### Post-send flagging

Evaluated and rejected. Flagging a message after it has been delivered does not prevent the fraud it is designed to catch. A seller who has transmitted banking details to a buyer has achieved the off-platform contact regardless of subsequent moderation action. Post-send flagging is appropriate for content policy enforcement (hate speech, spam) but not for financial fraud prevention where the act of delivery is the harm.

### Server-side content scanning without blocking

Evaluated and rejected. Scanning without blocking is equivalent to post-send flagging in its fraud prevention capability. It adds infrastructure cost without changing the outcome.

### Filtering only at the API gateway level

Evaluated and rejected. Gateway-level filtering operates on HTTP request payloads without tenant context. The filtering rules are tenant-configurable — a rule enabled for one tenant may be disabled for another. This logic belongs in the domain service layer where tenant configuration is available, not in the gateway.

## Consequences

### Positive
- Financial fraud vectors are blocked before the damage is done, not after.
- Filter rules are tenant-configurable without code changes or deployments.
- Moderator privacy exposure is minimised — the review queue contains only the blocked content, not conversation context.
- The `require_review` flag gives tenants control over the tradeoff between automation and human oversight.

### Negative
- Synchronous filtering adds latency to every message send operation. For simple regex-based rules this is negligible. For more complex pattern matching at high volume, this warrants performance testing.
- A sufficiently motivated bad actor can work around text-based filters (character substitution, encoding). This is a known limitation of pattern matching. It raises the effort required to commit fraud rather than eliminating the possibility.
- False positives — legitimate messages blocked by an overly broad rule — create friction for honest users. Tenants must tune their filter rules carefully. The `require_review` flag provides a safety net.

## Revisit Criteria

This decision should be revisited when:
- Message volume reaches a level where synchronous filtering measurably impacts API response times, at which point async pre-send filtering via a queue (with optimistic hold) should be evaluated.
- A machine learning-based content classification approach becomes viable and cost-effective for the tenant tier that would benefit from it.
