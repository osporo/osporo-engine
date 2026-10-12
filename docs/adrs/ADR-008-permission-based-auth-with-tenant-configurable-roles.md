# ADR-008: Permission-Based Auth with Tenant-Configurable Role Bundles

**Status:** Accepted  
**Date:** 2026-03-14  
**Author:** Wahina Prince Chabalala  
**Deciders:** Wahina Prince Chabalala 

---

## Context

Osporo is infrastructure, not a single marketplace. Every tenant — a vehicle marketplace, a digital goods platform, a services marketplace — has different operational requirements. A vehicle marketplace may want staff who can verify listings but cannot suspend users. A digital goods marketplace may want no staff roles at all. A large general marketplace may need a full hierarchy of moderation staff with tightly scoped capabilities.

A hardcoded role-to-permission mapping in application code removes this flexibility entirely. If MARKETPLACE_STAFF is defined once in code with a fixed set of permissions, every tenant inherits that definition regardless of whether it fits their operational model.

The system must also handle the reality that the same user within a single tenant may need to operate as both a buyer and a seller. This is tenant-dependent — some marketplaces want separated buyer and seller accounts, others permit the same account to hold both roles. The auth model must support both configurations without treating one as the default.

The actors defined for the system are:

- **Guest** — unauthenticated, read-only access, not a data entity
- **Buyer** — registered user with purchasing capability
- **Seller** — registered user with listing capability
- **Marketplace Staff** — operator-level user with moderation or management capability
- **Marketplace Owner** — tenant-level administrator
- **Osporo Admin** — platform-level superadmin, outside tenant context

---

## Decision

**Osporo defines the complete set of valid permission constants. Tenants configure roles by bundling those permissions. Roles are not hardcoded in application code.**

### Permission Constants

All valid permissions are defined as a code-level enum in `com.osporo.engine.auth.Permission`. Tenants cannot define permissions that do not exist in this enum — they can only bundle existing permissions into named roles.

The initial permission set:

```
Listing permissions
  LISTING_CREATE
  LISTING_READ
  LISTING_UPDATE_OWN
  LISTING_DELETE_OWN
  LISTING_TAKEDOWN_ANY

Category permissions
  CATEGORY_CREATE
  CATEGORY_EDIT
  CATEGORY_DELETE

Order permissions
  ORDER_CREATE
  ORDER_READ_OWN
  ORDER_READ_ANY
  ORDER_REFUND

Message permissions
  MESSAGE_CREATE
  MESSAGE_READ_OWN

Moderation permissions
  MODERATION_REVIEW
  MODERATION_CONFIG
  REPORT_CREATE

User permissions
  USER_READ_ANY
  USER_SUSPEND
  USER_INVITE

Platform permission (Osporo system level only)
  PLATFORM_ADMIN
```

This list is extended as new API surface is added. Each new endpoint references one or more permissions from this enum.

### TenantRoleConfig Entity

```
TenantRoleConfig
────────────────
id               UUID        PK
tenant_id        UUID        FK → Tenant
role_name        VARCHAR     e.g. SELLER, BUYER, MARKETPLACE_STAFF
permissions      TEXT[]      array of Permission enum values
is_default       BOOLEAN     assigned automatically on registration
created_at       TIMESTAMP
```

A tenant can define any number of named roles. Each role maps to a subset of Osporo's permission constants. Roles are assigned to users via the `roles` array on the User entity.

### JWT Structure

At login, the engine resolves the user's roles for their tenant, looks up the permission set for each role from `TenantRoleConfig`, unions the sets, and writes the result into the JWT:

```json
{
  "sub": "user-uuid",
  "tenant_id": "tenant-uuid",
  "roles": ["SELLER", "BUYER"],
  "permissions": [
    "LISTING_CREATE",
    "LISTING_UPDATE_OWN",
    "LISTING_DELETE_OWN",
    "LISTING_READ",
    "ORDER_CREATE",
    "ORDER_READ_OWN",
    "MESSAGE_CREATE",
    "MESSAGE_READ_OWN",
    "REPORT_CREATE"
  ],
  "iat": 1234567890,
  "exp": 1234567890
}
```

Roles are included for readability and logging. Permissions are the authoritative set that the API enforces. Spring Security `@PreAuthorize` annotations reference permissions, not roles.

### Osporo Admin

The Osporo Admin operates outside tenant context. Their JWT carries no `tenant_id` and a `PLATFORM_ADMIN` permission that bypasses tenant-level access control entirely. This account type is provisioned by Osporo and is not configurable by tenants.

### Multi-Role Users

A user within a tenant may hold multiple roles simultaneously. Whether this is permitted — for example, the same account being both BUYER and SELLER — is a tenant configuration decision. Osporo supports it at the data and auth layer. A tenant that wants strict separation configures its registration flow to prevent dual assignment.

---

## Alternatives Considered

### Hardcoded role-to-permission mappings in application code

Rejected. This removes the flexibility that makes Osporo viable as infrastructure. Every tenant would inherit identical role definitions regardless of their operational model. Changing a role definition would require a code change and a deployment.

### RBAC with no permission constants — roles only

Rejected. If API endpoints check role names rather than permission names, adding a new role or changing a role's scope requires changes across every endpoint that references it. Permission constants decouple the endpoint definition from the role configuration.

### Full ABAC (Attribute-Based Access Control)

Rejected for MVP. ABAC allows policies like "a user can update a listing if they are the seller AND the listing status is ACTIVE AND the tenant's edit window has not expired." This level of granularity is correct for some future scenarios but introduces significant implementation complexity. The permission-based model handles all MVP use cases and can be extended toward ABAC for specific resource-level checks without a full architectural change.

## Consequences

### Positive
- Tenants have full control over what their roles mean within Osporo's defined boundaries.
- New permissions can be added without breaking existing role configurations.
- The JWT is self-contained — the API does not need a database lookup to resolve permissions on every request.
- `@PreAuthorize("hasAuthority('LISTING_CREATE')")` is the complete enforcement mechanism at the endpoint level.

### Negative
- Permission changes after JWT issuance require a token refresh to take effect. A user whose role is modified mid-session continues with their old permissions until their token expires.
- The permission enum grows as the API surface grows. Discipline is required to keep permission names consistent and well-scoped.
- Tenants must configure their role bundles correctly — misconfiguration (e.g. granting MODERATION_CONFIG to all users) is the tenant's operational responsibility, not an Osporo bug.

## Revisit Criteria

This decision should be revisited when:
- A tenant use case requires resource-level permission checks (e.g. a seller can only edit their own listings within a specific time window) that cannot be expressed as a simple permission constant — at which point selective ABAC policies can be layered on top of this model.
- The permission enum exceeds a manageable size and warrants grouping into permission namespaces.
