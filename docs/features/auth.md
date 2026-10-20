# Authentication API

**Status:** Implemented. Last reviewed against source and existing tests: 2026-10-20.

This reference describes the current authentication API and supersedes the auth design in **Osporo API v1.docx**. Read [API standards](../API_STANDARDS.md) for shared conventions and the [domain index](README.md) for the other domains. All auth routes remain under `/v1`.

## Endpoint index

| Endpoint | Operation | Success |
| --- | --- | --- |
| `POST /v1/auth/register` | [1) Register](#1-register) | 201 Created |
| `POST /v1/auth/login` | [2) Login](#2-login) | 200 OK |
| `POST /v1/auth/refresh` | [3) Refresh](#3-refresh) | 200 OK |
| `POST /v1/auth/logout` | [4) Logout](#4-logout) | 204 No Content |

## Shared request and response contract

Send request bodies as `application/json`. None of these routes requires an access token or a particular permission. They do not define path or query parameters.

For registration and login without a Bearer token, select the tenant using `X-Tenant-ID` with a UUID:

```http
Content-Type: application/json
X-Tenant-ID: 550e8400-e29b-41d4-a716-446655440000
```

The UUID must identify an actual tenant; example UUIDs do not provision one. A missing, blank, or malformed tenant header leaves tenant context unset, so registration and login return 400 `TENANT_CONTEXT_MISSING` for an otherwise valid request.

If a valid Bearer token is supplied, its `tenant_id` claim supplies tenant context instead of the header. A conflicting header is ignored rather than explicitly rejected. If a Bearer token fails validation, the filter clears authentication and continues without falling back to the tenant header. On these public auth routes, that does not itself guarantee a 401: registration/login still need tenant context, while refresh/logout use the body token independently.

Success bodies use `{data, meta}`. Metadata is `{"requestId": "<uuid>", "pagination": null}`. The UUID is generated when metadata is constructed; it is not propagated as a shared `X-Request-ID` response header or error field. A 204 response has no body.

Controller-handled errors use `{error: {code, message, details}}`, with no request ID. Clients should branch on `code`, not message text. Non-validation errors have `details: []`; validation details contain `field` and `message`. Validation message wording and detail ordering are not stable contracts.

## 1) Register

`POST /v1/auth/register`

Creates a user in the resolved tenant. The tenant must exist and have status `ACTIVE`. The service assigns the tenant's configured default role, falling back to `BUYER` when no default role configuration exists. The caller cannot choose roles in the registration DTO. Registration returns the user summary without issuing tokens.

### Request

| Field | Validation |
| --- | --- |
| `email` | Required, nonblank, valid email address |
| `password` | Required, nonblank, at least eight characters |

```json
{
  "email": "user@gmail.com",
  "password": "password123"
}
```

There is no special-character password requirement. The service stores a password hash and lowercases and strips the email before saving. Email uniqueness is scoped to the tenant. The current duplicate precheck uses the supplied email before normalization; callers should not assume case or whitespace variants always produce the same conflict response. The database also enforces `(tenant_id, email)` uniqueness.

### Response

201 Created. IDs are illustrative; the example assumes the default role is `BUYER`.

```json
{
  "data": {
    "id": "d1a4ae11-5c9f-4a90-8d92-d54c39e32485",
    "email": "user@gmail.com",
    "roles": ["BUYER"]
  },
  "meta": {
    "requestId": "bc5c0be4-dd58-48ca-b657-3d6120273418",
    "pagination": null
  }
}
```

### Errors

| Status | Code | Condition |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | A request field fails DTO validation |
| 400 | `TENANT_CONTEXT_MISSING` | Tenant context is unset |
| 403 | `TENANT_SUSPENDED` | The resolved tenant is not active |
| 404 | `TENANT_NOT_FOUND` | The resolved tenant does not exist |
| 409 | `EMAIL_ALREADY_EXISTS` | The tenant-scoped duplicate precheck finds the supplied email |

Example validation response for a password shorter than eight characters:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "The request contains invalid fields.",
    "details": [
      { "field": "password", "message": "size must be between 8 and 2147483647" }
    ]
  }
}
```

## 2) Login

`POST /v1/auth/login`

Exchanges tenant-scoped credentials for an access token and refresh token. The email is lowercased and stripped for lookup. An unknown email and a password mismatch produce the same authentication error. After checking credentials, the service rejects a suspended user.

### Request

Use the tenant header described above. Validation is the same as registration: nonblank valid email and nonblank password of at least eight characters. A shorter password fails validation before credential verification.

```json
{
  "email": "user@gmail.com",
  "password": "password123"
}
```

### Response

200 OK. Token values are placeholders. `expires_in` is the configured access-token lifetime in seconds; 900 is an example, not a fixed default.

```json
{
  "data": {
    "access_token": "<signed-access-jwt>",
    "refresh_token": "<opaque-refresh-token>",
    "expires_in": 900
  },
  "meta": {
    "requestId": "bc5c0be4-dd58-48ca-b657-3d6120273418",
    "pagination": null
  }
}
```

### Errors

| Status | Code | Condition |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | A request field fails DTO validation |
| 400 | `TENANT_CONTEXT_MISSING` | Tenant context is unset |
| 401 | `AUTHENTICATION_FAILED` | No user matches the email in this tenant, or the password does not match |
| 403 | `ACCOUNT_SUSPENDED` | Credentials match a suspended user |

```json
{
  "error": {
    "code": "AUTHENTICATION_FAILED",
    "message": "Email or password is invalid.",
    "details": []
  }
}
```

```json
{
  "error": {
    "code": "ACCOUNT_SUSPENDED",
    "message": "Your account has been suspended.",
    "details": []
  }
}
```

Login does not explicitly check tenant existence or status, so it does not establish registration's `TENANT_NOT_FOUND` or `TENANT_SUSPENDED` contract. It also does not explicitly check the user's soft-deletion timestamp.

## 3) Refresh

`POST /v1/auth/refresh`

Exchanges a valid refresh token for a new access token and refresh token. Neither an access token nor `X-Tenant-ID` is required. The tenant and user are resolved from the stored refresh-token record; the refresh token itself is opaque, not a JWT containing tenant claims. A supplied tenant header or access-token tenant is not compared with the stored refresh token's tenant.

### Request

`refresh_token` is required and must be nonblank.

```json
{
  "refresh_token": "<opaque-refresh-token>"
}
```

The service rejects an unknown, invalidated, or expired token and a token whose associated user cannot be found within its stored tenant. It rejects suspended users, invalidates the old refresh token, and issues a new pair within the transaction. Access-token expiry is not a prerequisite for refreshing.

### Response

200 OK. The response has the same fields as login; replace the saved refresh token with the newly returned value.

```json
{
  "data": {
    "access_token": "<new-signed-access-jwt>",
    "refresh_token": "<new-opaque-refresh-token>",
    "expires_in": 900
  },
  "meta": {
    "requestId": "bc5c0be4-dd58-48ca-b657-3d6120273418",
    "pagination": null
  }
}
```

### Errors

| Status | Code | Condition |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | `refresh_token` is missing, null, empty, or whitespace-only |
| 401 | `INVALID_TOKEN` | Token is unknown, invalidated, expired, or its user cannot be found in the stored tenant |
| 403 | `ACCOUNT_SUSPENDED` | The associated user is suspended |

```json
{
  "error": {
    "code": "INVALID_TOKEN",
    "message": "Token is invalid or has expired.",
    "details": []
  }
}
```

The suspended-account error has the same shape as login. Refresh does not explicitly recheck tenant status or user soft deletion. Sequential reuse of an invalidated token is rejected; concurrent reuse protection and automatic revocation for suspicious activity are not established by the current implementation.

## 4) Logout

`POST /v1/auth/logout`

Invalidates the supplied refresh token if a matching record exists. It requires neither an access token nor `X-Tenant-ID`. Token lookup does not compare the stored tenant with any supplied request tenant context.

### Request

`refresh_token` is required and must be nonblank.

```json
{
  "refresh_token": "<opaque-refresh-token>"
}
```

### Response

204 No Content, with no response body or envelope. Unknown nonblank tokens and already-invalidated tokens also succeed. The service does not require an unexpired token to invalidate it.

### Errors

400 `VALIDATION_ERROR` if `refresh_token` is missing, null, empty, or whitespace-only. The tolerant handling of unknown tokens does not bypass request validation.

Logout invalidates only the supplied refresh token. It does not revoke every refresh token for the user, revoke an existing access token, or create/end a server-side HTTP session. Existing access tokens remain usable until expiry under the current stateless validation flow.

## Token configuration and authorization

`JWT_ACCESS_TOKEN_EXPIRY` and `JWT_REFRESH_TOKEN_EXPIRY` configure lifetimes in seconds through [application properties](../../src/main/resources/application.properties); neither has a default in those properties. The integration tests use 900 and 2,592,000 seconds respectively. `JWT_SECRET` supplies the Base64-encoded signing key.

Access JWTs contain `sub` (user UUID), `tenant_id`, `roles`, `permissions`, `iat`, and `exp`. On login and refresh, permissions are resolved as the deduplicated union of the user's roles' bundles for the tenant. Roles with no configuration contribute no permissions. The authentication filter validates signed claims and maps permission strings to Spring authorities without reloading the user's status or role bundles on each request. Role or status changes therefore do not immediately change an existing token's authorities.

Refresh tokens are generated from 32 random bytes, encoded with URL-safe Base64 without padding, and persisted with their tenant, user, expiry, and invalidation state. Each successful refresh issues a new token with the configured refresh lifetime. This does not guarantee an indefinite session.

## Error handling limits and verification

The endpoint error tables describe explicit validation and service outcomes. Unexpected exceptions handled by controller advice return 500 `INTERNAL_SERVER_ERROR`. Malformed JSON and other MVC failures have no dedicated mappings; do not assume every invalid HTTP request produces 400 `VALIDATION_ERROR`. Security filter failures also do not share a configured JSON authentication entry point; see [API standards](../API_STANDARDS.md).

This reference was checked against [AuthController](../../src/main/java/com/osporo/engine/auth/AuthController.java), [AuthService](../../src/main/java/com/osporo/engine/auth/AuthService.java), [auth DTOs](../../src/main/java/com/osporo/engine/auth/dto/), [AuthMapper](../../src/main/java/com/osporo/engine/auth/AuthMapper.java), [JwtService](../../src/main/java/com/osporo/engine/auth/JwtService.java), [the JWT filter](../../src/main/java/com/osporo/engine/shared/security/JwtAuthenticationFilter.java), [response types](../../src/main/java/com/osporo/engine/shared/response/), and [exception handling](../../src/main/java/com/osporo/engine/shared/exception/GlobalExceptionHandler.java).

Existing [HTTP integration tests](../../src/test/java/com/osporo/engine/auth/AuthServiceIntegrationTest.java) cover registration, duplicate email, validation, missing tenant header, login success/failure, refresh rotation, and logout invalidation. [Service unit tests](../../src/test/java/com/osporo/engine/auth/AuthServiceTest.java) cover additional tenant, credential, suspension, and refresh failures. [JWT tests](../../src/test/java/com/osporo/engine/auth/JwtServiceTest.java) cover lifetime units. These test definitions do not cover every behavior described above or establish a complete tenant-isolation boundary; their presence is not a claim that tests were run for this documentation change.
