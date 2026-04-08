# osporo-engine

The core API engine for Osporo — a headless, multi-tenant marketplace infrastructure platform. Deploy it once and power any marketplace: physical goods, digital products, services, vehicles, or custom verticals.

osporo-engine is the backend that every Osporo-powered marketplace runs on. It handles the hard parts — multi-tenancy, authentication, listings, orders, escrow, payments, messaging, and moderation — so developers can focus on building the marketplace experience, not the infrastructure underneath it.

---

## What Osporo Is

Osporo is open-source marketplace infrastructure. It is not a hosted marketplace you sign up for. It is not a Shopify alternative. It is the engine that developers deploy to build their own marketplace products.

```
Your marketplace frontend  →  osporo-engine API  →  PostgreSQL + Stripe + R2
```

A developer deploys osporo-engine, configures their tenant, builds a frontend against the REST API (or uses osporo-frontend as a reference), and has a fully operational marketplace with authentication, listings, orders, escrow, messaging, and moderation out of the box.

---

## Features

- **Multi-tenant** — one deployment powers multiple independent marketplaces, each fully isolated
- **Headless** — a clean REST API with no opinions about your frontend
- **Configurable listing types** — physical goods, digital goods, services, vehicles, or custom with JSONB attribute schemas per tenant
- **Order lifecycle** — full state machine covering payment, fulfilment, confirmation, and completion by delivery method
- **Escrow** — Stripe Connect delayed transfers with auto-release and manual override
- **Permission-based auth** — JWT authentication with tenant-configurable role bundles
- **Pre-send message filtering** — blocks off-platform contact attempts before delivery, not after
- **Moderation** — configurable filter rules, review queue, and report system
- **Soft deletes everywhere** — nothing is permanently lost, full audit trail throughout

---

## Tech Stack

| Layer | Technology                     |
|---|--------------------------------|
| Runtime | Java 25                        |
| Framework | Spring Boot 4.0.5              |
| Database | PostgreSQL 18.1                 |
| Migrations | Flyway                         |
| Auth | Spring Security + JJWT         |
| Payments | Stripe Connect                 |
| Storage | Cloudflare R2 (S3-compatible)  |
| Docs | springdoc-openapi (Swagger UI) |
| Containers | Docker + Docker Compose        |

---

## Getting Started

### Prerequisites

- [Docker](https://docs.docker.com/get-docker/) and Docker Compose

That is the only requirement. The database, migrations, and application all run inside containers.

### Setup

**1. Clone the repository**

```bash
git clone https://github.com/osporo/osporo-engine.git
cd osporo-engine
```

**2. Create your environment file**

```bash
cp .env.example .env
```

**3. Open `.env` and configure your values**

At minimum, change `POSTGRES_PASSWORD` and `JWT_SECRET`. See [Environment Variables](#environment-variables) for the full reference.

**4. Start the application**

```bash
docker compose up
```

Flyway migrations run automatically on startup. The API is ready when you see:

```
Started OsporoEngineApplication in X.XXX seconds
```

**API:** `http://localhost:8080`
**Swagger UI:** `http://localhost:8080/swagger-ui.html`

---

## Environment Variables

All configuration is supplied via environment variables. Copy `.env.example` to `.env` and populate it.

| Variable | Required | Default | Description |
|---|---|---|---|
| `POSTGRES_DB` | Yes | `osporo` | Database name |
| `POSTGRES_USER` | Yes | `osporo` | Database user |
| `POSTGRES_PASSWORD` | Yes | — | Database password |
| `JWT_SECRET` | Yes | — | Secret key for JWT signing. Use a long random string. |
| `SERVER_PORT` | No | `8080` | Port the API listens on |
| `SHOW_SQL` | No | `false` | Log generated SQL queries |

Sensitive values — `POSTGRES_PASSWORD` and `JWT_SECRET` — must be set. The application will not start without them.

---

## Development

### Running the Database Only

For a faster development loop, run only the database in Docker and the application directly from your IDE:

```bash
docker compose up db
```

Then run `OsporoEngineApplication` from IntelliJ or VS Code with your `.env` loaded as environment variables.

If using IntelliJ, install the [EnvFile plugin](https://plugins.jetbrains.com/plugin/7861-envfile) and point your run configuration at `.env`. Change `SPRING_DATASOURCE_URL` to `jdbc:postgresql://localhost:5432/osporo` for local connections.

### Running Tests

```bash
./mvnw test
```

Integration tests require a running PostgreSQL instance. Start the database first:

```bash
docker compose up db
./mvnw verify
```

### Rebuilding After Code Changes

```bash
docker compose up --build app
```

---

## Project Structure

```
osporo-engine/
├── src/main/java/com/osporo/engine/
│   ├── auth/               Authentication — register, login, refresh, logout
│   ├── user/               User profiles, suspension, invite flow
│   ├── tenant/             Tenant settings, role config, listing config
│   ├── category/           Category management
│   ├── listing/            Listing CRUD, search, image handling
│   ├── order/              Order lifecycle and state machine
│   ├── payment/            Payment provider abstraction, Stripe Connect
│   ├── escrow/             Escrow holds, auto-release, manual release
│   ├── messaging/          Threads, messages, pre-send filtering
│   ├── moderation/         Queue, review flow, reports, filter config
│   └── shared/             Config, exceptions, enums, security, storage, events
├── src/main/resources/
│   ├── application.properties
│   └── db/migration/       Flyway SQL migrations (V1 through V19)
├── src/test/               Unit and integration tests mirroring source structure
├── docker-compose.yml
├── Dockerfile
├── .env.example
└── pom.xml
```

---

## API Overview

The full API is documented in the Swagger UI at `/swagger-ui.html` when the application is running. A summary of the domains:

| Domain | Base Path | Description |
|---|---|---|
| Auth | `/v1/auth` | Registration, login, token refresh, logout |
| Users | `/v1/users` | Profiles, suspension, invites |
| Tenants | `/v1/tenant` | Settings, roles, listing configuration |
| Categories | `/v1/categories` | Category management |
| Listings | `/v1/listings` | Listing CRUD, search, images |
| Orders | `/v1/orders` | Order lifecycle, cancellation, refunds |
| Payments | `/v1/payments` | Seller onboarding, payment details |
| Escrow | `/v1/escrow` | Hold status, manual release |
| Messaging | `/v1/messages` | Threads and messages |
| Moderation | `/v1/moderation` | Queue, reports, filter config |

All endpoints follow a consistent response envelope:

```json
{
  "data": { },
  "meta": {
    "request_id": "uuid",
    "pagination": { }
  }
}
```

Errors follow a consistent error envelope:

```json
{
  "error": {
    "code": "MACHINE_READABLE_CODE",
    "message": "Human readable description.",
    "request_id": "uuid",
    "details": []
  }
}
```

---

## Licence

Osporo is licenced under the **Apache 2.0 License**

See [LICENSE](./LICENSE) for the full terms.

---

## Repositories

| Repository | Visibility | Description |
|---|---|---|
| [osporo-engine](https://github.com/osporo/osporo-engine) | Public | This repository. The core API. |
| [osporo-frontend](https://github.com/osporo/osporo-frontend) | Public | Reference Next.js frontend |
| [osporo-docs](https://github.com/osporo/osporo-docs) | Public | Documentation site |
| osporo-cloud | Private | Hosted SaaS layer |

---

## Trademark

The Osporo name and logo are trademarks of Clicalyst. You may use them to describe a product built on Osporo without implying official endorsement. See [osporo.com/trademark](https://osporo.com/trademark) for the full usage policy.

---

*Built by [Clicalyst](https://clicalyst.com). Refuse the Template.*
