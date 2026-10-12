# ADR-012: Pluggable Authentication Providers

**Status:** Accepted  
**Date:** 2026-10-12  
**Deciders:** Prince Chabalala  
**Technical Story:** Osporo's authentication layer is tightly coupled to its own JWT implementation. Tenants operating existing identity infrastructure should not be forced to migrate their users to Osporo Auth. Authentication must be decoupled so tenants can bring their own provider while Osporo retains full control of authorization.

---

## Context and Problem Statement

In the initial implementation, Osporo owns the full authentication lifecycle — user registration, password hashing, JWT issuance, and token validation. This works for greenfield deployments but creates a hard barrier for tenants who already operate an identity provider such as Clerk, Auth0, or Google Workspace.

The problem is that authentication (proving who you are) and authorization (deciding what you can do) are conflated in the current design. Osporo should only need to own the second. The first can be delegated to any standards-compliant provider, provided Osporo can resolve the resulting identity to an internal user and apply its permission model from that point forward.

---

## Decision Drivers

- Tenants should be able to use existing identity infrastructure without migrating users
- Osporo must always own authorization — roles, permissions, and tenant scoping are non-negotiable internal concerns
- The abstraction must be extensible — adding a new provider should not require changes to core auth logic
- The default experience (Osporo Auth) must remain unchanged for tenants who do not configure an external provider
- Identity resolution must be deterministic — a verified external identity always maps to exactly one Osporo user per tenant

---

## Considered Options

**Option A — Tight coupling (current state)**  
Osporo owns authentication entirely. All tenants must use Osporo Auth. No external providers supported.

**Option B — Pluggable AuthenticationProvider interface**  
Authentication is abstracted behind an interface. Osporo ships a default implementation. Tenants configure their provider via `TenantAuthConfig`. The filter delegates to the correct provider at runtime.

**Option C — Forward all authentication to an external IdP**  
Osporo abandons its own auth entirely and requires every tenant to use an external provider. No default Osporo Auth.

---

## Decision Outcome

**Option B — Pluggable AuthenticationProvider interface.**

Option A blocks legitimate enterprise use cases and limits adoption. Option C removes the out-of-the-box experience that makes Osporo self-hostable without additional infrastructure. Option B satisfies both — tenants who want simplicity get Osporo Auth by default, tenants who have existing identity infrastructure can plug it in without disruption.

---

## Architecture

### The Interface

```java
public interface AuthenticationProvider {
    VerifiedIdentity verify(String token, TenantAuthConfig config);
}
```

Every provider receives the raw Bearer token and the tenant's auth configuration. It is responsible for validating the token against its own rules (signature verification, expiry, issuer) and returning a `VerifiedIdentity` if the token is valid. It throws `InvalidTokenException` if it is not.

```java
public record VerifiedIdentity(
    String externalUserId,
    String email,
    String providerName
) {}
```

### Provider Implementations

| Provider | Class | Description |
|---|---|---|
| `OSPORO` | `OsporoAuthenticationProvider` | Validates Osporo-issued JWTs using `JwtService`. Default. |
| `CLERK` | `ClerkAuthenticationProvider` | Validates Clerk JWTs against Clerk's JWKS endpoint. |
| `OIDC` | `OidcAuthenticationProvider` | Validates any OIDC-compliant JWT using the issuer's JWKS endpoint. Covers Google, Auth0, and others. |

New providers are registered as Spring `@Component` implementations of `AuthenticationProvider`. No changes to core auth logic are required.

### Provider Factory

```java
@Component
public class AuthenticationProviderFactory {

    private final Map<String, AuthenticationProvider> providers;

    public AuthenticationProviderFactory(List<AuthenticationProvider> providers) {
        this.providers = providers.stream()
            .collect(Collectors.toMap(
                p -> p.getClass().getAnnotation(ProviderType.class).value(),
                p -> p
            ));
    }

    public AuthenticationProvider forConfig(TenantAuthConfig config) {
        String providerKey = config != null ? config.getProvider() : "OSPORO";
        AuthenticationProvider provider = providers.get(providerKey);
        if (provider == null) {
            throw new OsporoException(ErrorCode.UNSUPPORTED_AUTH_PROVIDER);
        }
        return provider;
    }
}
```

### TenantAuthConfig Entity

```sql
CREATE TABLE tenant_auth_config (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL REFERENCES tenants(id) UNIQUE,
    provider        TEXT        NOT NULL DEFAULT 'OSPORO',
    provider_config JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT valid_auth_provider
        CHECK (provider IN ('OSPORO', 'CLERK', 'OIDC'))
);
```

`provider_config` is a JSONB blob whose shape depends on the provider:

```json
// CLERK
{
  "jwks_url": "https://clerk.your-domain.com/.well-known/jwks.json"
}

// OIDC (Google, Auth0, etc.)
{
  "issuer": "https://accounts.google.com",
  "jwks_url": "https://www.googleapis.com/oauth2/v3/certs",
  "audience": "your-client-id"
}

// OSPORO — no config required
{}
```

### Identity Resolution

After a provider returns a `VerifiedIdentity`, Osporo resolves it to an internal `User`. This is handled by `IdentityResolutionService`:

```
VerifiedIdentity { externalUserId, email, providerName }
→ Look up User by (tenant_id, auth_provider, external_provider_id)
→ Found     → return existing User
→ Not found → auto-provision new User with default role (if TenantAuthConfig.allowAutoProvisioning = true)
            → throw 401 (if auto-provisioning is disabled)
```

This requires two new columns on the `users` table:

```sql
ALTER TABLE users ADD COLUMN auth_provider        TEXT NOT NULL DEFAULT 'OSPORO';
ALTER TABLE users ADD COLUMN external_provider_id TEXT;

ALTER TABLE users ADD CONSTRAINT uq_user_external_identity
    UNIQUE (tenant_id, auth_provider, external_provider_id);
```

Users authenticated via Osporo Auth have `auth_provider = 'OSPORO'` and `external_provider_id = NULL`. Their identity is the Osporo `user_id` itself, resolved from the JWT `sub` claim as before.

### Updated Filter Flow

```
Request arrives with Bearer token
→ Read X-Tenant-ID or extract tenant from token header claim
→ Load TenantAuthConfig for tenant (defaults to OSPORO if none configured)
→ Resolve AuthenticationProvider via AuthenticationProviderFactory
→ provider.verify(token, config) → VerifiedIdentity
→ identityResolutionService.resolve(identity, tenantId) → User
→ Load roles and permissions from TenantRoleConfig
→ Build SecurityContext as before
→ Authorization proceeds unchanged
```

Everything downstream of identity resolution — permission loading, `SecurityContext` population, `@PreAuthorize` evaluation — is completely unchanged regardless of which provider authenticated the user.

---

## Consequences

### Positive

- Tenants with existing identity infrastructure can onboard without migrating users
- Osporo retains full ownership of authorization — no external provider can influence roles or permissions
- Adding a new provider requires implementing one interface and registering one Spring component
- The default experience is unchanged — tenants without a `TenantAuthConfig` row continue to use Osporo Auth transparently
- The `JwtAuthenticationFilter` becomes provider-agnostic — it delegates rather than validates directly

### Negative

- `JwtAuthenticationFilter` gains a dependency on `TenantAuthConfig` which requires a database read on every authenticated request. This should be mitigated with a short-lived cache on `TenantAuthConfig` lookups.
- External JWKS validation introduces a network dependency. OIDC and Clerk providers must cache the JWKS response and handle rotation gracefully.
- Auto-provisioning introduces a code path where a `User` record is created during a standard API request rather than a registration flow. This must be handled transactionally and tested explicitly.

---

## Implementation Order

This ADR documents the intended architecture. Implementation is deferred until the core domain modules are complete and Osporo Auth is proven stable. The correct implementation sequence is:

1. `TenantAuthConfig` migration and entity
2. `AuthenticationProvider` interface and `OsporoAuthenticationProvider` refactor
3. `AuthenticationProviderFactory`
4. `IdentityResolutionService` and `users` table migration
5. `JwtAuthenticationFilter` refactor to delegate to factory
6. `ClerkAuthenticationProvider`
7. `OidcAuthenticationProvider`

---

## References

- ADR-008: Permission-Based Authorization Model
- ADR-009: Tenant-Scoped User Identity
- [OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html)
- [Clerk JWT Verification](https://clerk.com/docs/backend-requests/handling/manual-jwt)
