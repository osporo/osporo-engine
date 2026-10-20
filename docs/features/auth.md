# Authentication API

Source: **Osporo API v1.docx**, Auth Domain. Converted and checked against the repository on 2026-10-19.

**Status:** Implemented routes; design differences noted below.

Read [API standards](../API_STANDARDS.md) for the current wire contract and shared rules, and the [domain index](README.md) for source interpretation. All routes remain under `/v1`. The endpoint descriptions and examples in **Source API design** document the original design, not proof of availability.

## Implementation notes and open decisions

The four routes exist in [AuthController](../../src/main/java/com/osporo/engine/auth/AuthController.java). The design below is preserved from the Word document; use these current implementation differences when calling the API:

- Registration and login select a tenant with `X-Tenant-ID` when no Bearer token is supplied. The header must be a UUID; `tenant_1` in the source is a placeholder, not a valid value.
- Success metadata currently contains `requestId` and `pagination: null`, rather than the source's `request_id`. Errors contain `code`, `message`, and `details` without a request ID. There is no shared `X-Request-ID` response header.
- Password validation is nonblank with at least eight characters; no special-character rule is implemented. The original registration error example below describes a different, unimplemented rule.
- Suspended-account errors currently have an empty `details` array, rather than the reason and suspension timestamp shown in the source.
- Registration can also return `TENANT_CONTEXT_MISSING` (400), `TENANT_NOT_FOUND` (404), or `TENANT_SUSPENDED` (403). Login requires tenant context too. All four bodies are validated, so missing or blank fields may produce `VALIDATION_ERROR` (400).
- Refresh tokens are opaque values: tenant context is stored in their database record, not decoded from a refresh-token claim. Refresh can also return `ACCOUNT_SUSPENDED` (403). Rotation does not guarantee indefinite sessions or concurrent replay protection.
- `expires_in` is the configured lifetime in seconds; 900 is an example. Logout invalidates a matching refresh token, not existing access tokens. An unknown nonblank token succeeds; a blank token still fails body validation.

Evidence: [auth service](../../src/main/java/com/osporo/engine/auth/AuthService.java), [DTOs](../../src/main/java/com/osporo/engine/auth/dto/), [integration tests](../../src/test/java/com/osporo/engine/auth/AuthServiceIntegrationTest.java).

## Endpoint index

| Endpoint | Operation |
| --- | --- |
| `POST /v1/auth/register` | [1) Register](#1-register) |
| `POST /v1/auth/login` | [2) Login](#2-login) |
| `POST /v1/auth/refresh` | [3) Refresh](#3-refresh) |
| `POST /v1/auth/logout` | [4) Logout](#4-logout) |

## Source API design

## Purpose

- Allow users to create an account with their tenant
- Serve users with tokens to securely browse the market
- Allow users to logout
- Ensure users have permission to make requests to secured endpoints

## Endpoints

### 1) Register

`POST /v1/auth/register`

The register endpoint allows a guest to create an account scoped to their tenant. The tenant id is injected by the client. Since the user doesn’t have a token at this stage, the tenant_id will be passed via the X-Tenant-ID header.

#### Parameters

No params

#### Permissions required

None

#### Request

Headers:

```http
X-Tenant-ID: tenant_1
```

Body:

```json
{
  "email": "user@gmail.com",
  "password": "password123"
}
```

#### Response

```json
{
  "data": {
    "id": "user-uuid",
    "email": "user@gmail.com",
    "roles": [
      "BUYER"
    ]
  },
  "meta": {
    "request_id": "uuid"
  }
}
```

#### Success

201 Created

#### Error

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "The request contains invalid fields",
    "request_id": "uuid",
    "details": [
      {
        "field": "email",
        "message": "Invalid email"
      },
      {
        "field": "password",
        "message": "Password must be > 6 chars and contain 1 special character"
      }
    ]
  }
}
```

400 Bad request

Error: VALIDATION_ERROR

409 Conflict

Error: EMAIL_ALREADY_EXISTS

### 2) Login

`POST /v1/auth/login`

The login endpoint allows a user to login to their account on the marketplace. Similar to the register endpoint, the user doesn’t have a token at this stage, therefore the tenant id will be passed via the X-TENANT-ID header.

#### Parameters

No params

#### Permissions required

None

#### Request

Headers:

```http
X-Tenant-ID: tenant_1
```

Body:

```json
{
  "email": "user@gmail.com",
  "password": "password123"
}
```

#### Response

```json
{
  "data": {
    "access_token": "server_generated_access_token",
    "refresh_token": "server_generated_refresh_token",
    "expires_in": 900
  },
  "meta": {
    "request_id": "uuid"
  }
}
```

#### Success

200 OK

#### Error

```json
{
  "error": {
    "code": "AUTHENTICATION_FAILED",
    "message": "Email or password is invalid",
    "request_id": "uuid",
    "details": []
  }
}
```

401 Unauthorized

Error: AUTHENTICATION_FAILED

```json
{
  "error": {
    "code": "ACCOUNT_SUSPENDED",
    "message": "Your account has been suspended",
    "request_id": "uuid",
    "details": [
      {
        "field": "reason",
        "message": "Violation of marketplace terms."
      },
      {
        "field": "suspended_at",
        "message": "2026-03-17T08:00:00Z"
      }
    ]
  }
}
```

403 Forbidden

Error: ACCOUNT_SUSPENDED

### 3) Refresh

`POST /v1/auth/refresh`

The refresh endpoint exists to issue the client with a new access token instead of having to login again. When the old access token has expired, the client calls refresh and will be issued with a new access token and refresh token. Both are issued so that the refresh token doesn’t remain static, this is more secure and ensures that as long as a user continues using the app, they won’t need to login. At this stage, the tenant_id is available in the refresh token, so there isn’t a need for the X-TENANT-ID header. The previous refresh token is invalidated immediately upon issuance of the new one.

#### Parameters

No params

#### Permissions

None

#### Request

```json
{
  "refresh_token": "server_generated_refresh_token"
}
```

#### Response

```json
{
  "data": {
    "access_token": "server_generated_access_token",
    "refresh_token": "server_generated_refresh_token",
    "expires_in": 900
  },
  "meta": {
    "request_id": "uuid"
  }
}
```

#### Success

200 OK

#### Error

```json
{
  "error": {
    "code": "INVALID_TOKEN",
    "message": "Token is invalid or has expired",
    "request_id": "uuid",
    "details": []
  }
}
```

401 Unauthorized

Error: INVALID_TOKEN

### 4) Logout

`POST /v1/auth/logout`

The logout endpoint exists to invalidate a refresh token and to end a user session.

#### Parameters

No params

#### Permissions

None

#### Request

```json
{
  "refresh_token": "server_generated_refresh_token"
}
```

#### Response

No body

#### Success

204 No Content

#### Error

No error if a bad token is sent
