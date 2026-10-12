# ADR-011: Moderation Permission Split — MODERATION_REVIEW and MODERATION_CONFIG

## Status
Accepted

## Date
2026-03-14

## Context

The moderation system introduced in ADR-010 has two distinct operational surfaces:

**The review queue** — processing blocked messages and reported content. A moderator sees flagged items, reads the blocked content, and approves or rejects. This is an operational task performed frequently, often by staff members assigned to moderation duties.

**The filter configuration** — defining which filter rules are active, setting auto-suspension thresholds, and deciding whether blocked messages require human review or are silently dropped. This is an administrative task performed infrequently, with consequences that affect every user on the marketplace.

The question is whether these two surfaces should be controlled by a single permission or separate permissions.

A single `MODERATION` permission that covers both surfaces is simpler to configure. A marketplace owner assigns it and the holder can both process the queue and change the rules.

Two separate permissions — `MODERATION_REVIEW` and `MODERATION_CONFIG` — allow a marketplace owner to assign queue processing duties to staff without granting them the ability to change the rules that determine what reaches the queue.

## Decision

**`MODERATION_REVIEW` and `MODERATION_CONFIG` are separate permission constants.**

`MODERATION_REVIEW` grants the ability to:
- View the moderation queue
- Read blocked message content submitted for review
- Approve or reject blocked messages
- View flagged listings and reported users in the moderation queue
- Take moderation action (remove listing, suspend user) on queued items

`MODERATION_CONFIG` grants the ability to:
- View the current `TenantModerationConfig`
- Enable or disable filter rules
- Add or modify custom filter patterns
- Set the auto-suspension threshold
- Toggle the `require_review` flag

### Default Role Assignments

These defaults are applied to Osporo's predefined role bundles in `TenantRoleConfig`. Tenants can override them:

```
MARKETPLACE_OWNER    MODERATION_REVIEW + MODERATION_CONFIG
MARKETPLACE_STAFF    neither by default
SELLER               neither
BUYER                neither
```

A marketplace owner who wants a staff member to process the review queue grants them `MODERATION_REVIEW` via the role configuration. A marketplace owner who also wants a staff member to manage filter rules grants them both. The owner's own permission ceiling bounds what they can grant — an owner cannot grant permissions they do not hold.

## Alternatives Considered

### Single MODERATION permission covering both surfaces

Rejected. Conflating queue review with filter configuration removes a meaningful access control boundary. The filter configuration determines what content ever reaches human review — a staff member who can change the rules can effectively disable the moderation system entirely by turning off all filter rules. That is a materially different capability from processing the queue and should require explicit, separate delegation by the marketplace owner.

### Three-way split: MODERATION_READ, MODERATION_REVIEW, MODERATION_CONFIG

Evaluated. A separate read permission (view queue without taking action) could be useful for audit or oversight roles. Deferred — if a tenant use case requires a read-only moderation observer role, `MODERATION_READ` can be added to the permission enum without affecting existing configurations. The two-permission model covers all identified MVP use cases.

## Consequences

### Positive
- Marketplace owners can delegate queue processing to staff without exposing filter configuration.
- The separation makes the security model explicit — changing rules and processing their output are distinct responsibilities.
- Follows the principle of least privilege: staff get only what they need to perform their assigned function.
- Consistent with the broader permission model defined in ADR-008, where `_OWN` vs `_ANY` and similar scoped variants are the pattern for expressing meaningful access boundaries.

### Negative
- Slightly more configuration surface for marketplace owners who want to grant full moderation access — they must assign two permissions rather than one. This is a minor friction acceptable given the security benefit.

## Revisit Criteria

This decision should be revisited when:
- A tenant use case requires a read-only moderation observer role, at which point `MODERATION_READ` is added as a third permission.
- The moderation surface expands significantly (e.g. AI-assisted moderation decisions, appeal workflows) and the two-permission model becomes insufficient to express the access boundaries needed.
