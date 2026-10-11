# Osporo Engine

Osporo is open-source marketplace infrastructure designed to make building, deploying, and operating online marketplaces easier. It provides a foundation so developers can focus on the business logic and experience that make their marketplace distinctive.

Osporo Engine is the headless backend at the heart of that vision. It is in early development: authentication and tenancy foundations are implemented, while most marketplace transaction capabilities remain planned. This repository does not yet provide a complete marketplace out of the box.

## Product Vision

The engine is intended for developers and engineering teams who want to self-host, build their own frontends, integrate their own services, and extend marketplace workflows. Intended applications include physical products, digital goods, services, vehicles, industry-specific B2B offerings, and custom marketplaces. These are examples of the desired flexibility, not currently supported business models.

Osporo Cloud is a future commercial direction for managed marketplace creation and operation. It could provide hosting, onboarding, administration, storefront customization, themes, domains, billing, and operational tooling. These capabilities are envisioned, not implemented by this repository.

**Osporo Engine must remain useful independently of Osporo Cloud.** The commercial offering should add value through convenience and capabilities while the open-source engine remains independently deployable and extensible.

Read the [project vision](docs/PROJECT_VISION.md) for the full context.

## Current Implementation

| Area | Current state |
|---|---|
| Authentication | Registration, login, JWT access tokens, persisted refresh tokens, refresh-token rotation, and logout through `/v1/auth`. Passwords use BCrypt. |
| Tenant-aware identity | Registration and login resolve users within a tenant. Public auth requests use `X-Tenant-ID`; authenticated requests resolve tenant context from JWT claims. |
| Roles and permissions | Tenant role configuration is stored in PostgreSQL. Login and refresh resolve permissions from the user's roles and include them in access tokens and Spring Security authorities. |
| Users and tenants | Entities, repositories, and services exist; dedicated user and tenant management REST controllers are not implemented. |
| API foundations | Request validation, response records, exception handling, and OpenAPI/Swagger dependencies. |
| Database | Flyway migrations V1–V12, including a default marketplace and role configuration seeds. Schemas also exist for planned marketplace domains. |
| Development and tests | Gradle wrapper, Docker Compose, authentication unit tests, a Spring context test, authentication integration tests using Testcontainers, and a GitHub Actions build workflow. |

Current role categories are `BUYER`, `SELLER`, `MARKETPLACE_STAFF`, `MARKETPLACE_OWNER`, and `OSPORO_ADMIN`. Role configuration storage and permission resolution are implemented; a role-management API and comprehensive authorization across future domains are not.

Listings, categories, orders, payments, escrow, messaging, moderation, reporting, and media storage remain broader targets. Database tables or security route matchers for these areas do not mean their APIs or business workflows exist. Stripe Connect and Cloudflare R2 are documented integration directions, not implemented integrations. Any future escrow-like flow must account for provider capabilities and applicable legal and regulatory requirements.

## Architecture and Engineering Direction

The engine follows a modular monolith direction with a REST API and PostgreSQL as its primary database. Multi-tenancy uses a shared database and schema, with tenant boundaries enforced through explicit server-side scoping and authorization. Isolation is a security requirement to verify in each workflow, not a guarantee supplied by the frontend or merely by having a tenant column.

The current built-in authentication uses Osporo-managed JWTs. Longer term, consumers should be able to integrate external identity systems, keeping authentication, identity resolution, tenant membership, and authorization distinct.

Prefer clear module boundaries, maintainable business logic, Flyway schema evolution, meaningful tests, and the simplest design that meets current requirements. Introduce abstractions and distributed infrastructure when concrete requirements justify them. Platform-wide administrative permissions must remain outside ordinary tenant configuration.

See the [architecture decision records](docs/) and [agent engineering guidance](AGENTS.md). ADRs record design decisions and may describe work that has not yet been implemented.

## Technology

| Layer | Current technology |
|---|---|
| Runtime | Java 25 |
| Framework | Spring Boot 4.0.5 |
| Build | Gradle 9.4.1 via the wrapper |
| Database | PostgreSQL; Compose and CI use `postgres:16-alpine` |
| Persistence and migrations | Spring Data JPA and Flyway |
| Authentication | Spring Security, BCrypt, and JJWT 0.13.0 |
| API documentation | springdoc-openapi 3.0.2 |
| Testing | JUnit Jupiter, Mockito, Spring Boot test support, and Testcontainers |
| Containers | Docker and Docker Compose |

## Getting Started

### Run with Docker Compose

Install Docker with Docker Compose. The container build supplies Java and Gradle.

Clone the repository:

```sh
git clone https://github.com/osporo/osporo-engine.git
cd osporo-engine
```

Copy the environment template to the filename used by Compose:

```sh
cp .env.example .env.docker
```

On PowerShell, use `Copy-Item .env.example .env.docker`.

Replace the file's contents with your own configuration following this layout:

```dotenv
DB_URL=jdbc:postgresql://db:5432/osporo_db
DB_USERNAME=osporo
DB_PASSWORD=replace-with-your-own-password
JWT_SECRET=replace-with-a-base64-encoded-random-signing-key
JWT_ACCESS_TOKEN_EXPIRY=900
JWT_REFRESH_TOKEN_EXPIRY=604800
```

The checked-in template is incomplete: its database name does not match Compose's fixed `osporo_db`, it omits token-expiry values, and its placeholder signing secret is unsuitable. Supply a Base64-encoded random key containing at least 32 bytes of decoded key material. Keep real credentials out of version control.

Start the services with the same file used for Compose variable interpolation:

```sh
docker compose --env-file .env.docker up --build
```

Compose reads `.env.docker` into the containers, but `env_file` alone does not supply values for the Compose file's `${...}` expressions. The explicit `--env-file` option supplies those values.

Flyway runs on application startup and seeds a default marketplace in a new database.

- API base: [localhost:8080](http://localhost:8080)
- Ping: [localhost:8080/ping](http://localhost:8080/ping)
- Swagger UI: [localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
- OpenAPI: [localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

### Configuration Reference

The application reads the following environment variables. Compose maps the `DB_*` variables above onto the datasource variables.

| Application variable | Description |
|---|---|
| `SPRING_DATASOURCE_URL` | JDBC URL, such as `jdbc:postgresql://localhost:5432/osporo_db` for a locally running application. |
| `SPRING_DATASOURCE_USERNAME` | PostgreSQL username. |
| `SPRING_DATASOURCE_PASSWORD` | PostgreSQL password. |
| `JWT_SECRET` | Base64-encoded signing key; at least 32 decoded bytes. |
| `JWT_ACCESS_TOKEN_EXPIRY` | Access-token lifetime in seconds. |
| `JWT_REFRESH_TOKEN_EXPIRY` | Refresh-token lifetime in seconds. |

These properties have no defaults in `application.properties`.

Both token lifetimes and the API's `expires_in` value use seconds. The example above gives a 15-minute access token and a seven-day refresh token. If upgrading from the earlier millisecond-based access-token configuration, change values such as `900000` to `900` in your environment files and deployment settings.

## Development

To run the database only:

```sh
docker compose --env-file .env.docker up -d db
```

For a local application, install JDK 25 and provide the application variables from the table above through your shell or IDE. Use `localhost` rather than `db` in the JDBC URL. Spring Boot does not automatically load the repository's dotenv files.

Run the application:

```sh
./gradlew bootRun
```

On Windows, use `.\gradlew.bat bootRun`.

### Tests and Builds

Run authentication service unit tests:

```sh
./gradlew test --tests 'com.osporo.engine.auth.AuthServiceTest'
```

Run the full suite or build:

```sh
./gradlew test
./gradlew build
```

On Windows, substitute `.\gradlew.bat` for `./gradlew`.

The full suite requires a Docker daemon for Testcontainers. Authentication integration tests provision their own PostgreSQL container; the separate application context test needs datasource and JWT configuration supplied through the environment. The integration test currently lives in `AuthServiceIntegrationTest.java` and declares `AuthControllerIntegrationTest`.

GitHub Actions runs `./gradlew clean build --no-daemon` on pushes and pull requests targeting `main` or `develop`, supplies a PostgreSQL service, and uploads test results. Its JWT signing secret comes from the repository's `JWT_SECRET` Actions secret. The Docker image build skips tests; use Gradle or CI to run them.

These commands describe the configured workflows, not a claim that the full suite currently passes.

## API Overview

The current REST endpoints are:

| Method | Path | Behavior |
|---|---|---|
| GET | `/ping` | Returns plain text `pong`. |
| POST | `/v1/auth/register` | Creates a user in the supplied tenant; returns 201. |
| POST | `/v1/auth/login` | Validates tenant-scoped credentials and returns tokens; returns 200. |
| POST | `/v1/auth/refresh` | Rotates a valid refresh token and issues a new token pair; returns 200. |
| POST | `/v1/auth/logout` | Invalidates the supplied refresh token; returns 204 without a body. |

Registration and login accept an email and password (minimum eight characters) and require the tenant UUID in `X-Tenant-ID`. Discover the seeded tenant UUID in the development database:

```sh
docker compose --env-file .env.docker exec db sh -c 'psql -U "$POSTGRES_USER" -d osporo_db -c "SELECT id, name, slug FROM tenants;"'
```

A registration or login body is:

```json
{
  "email": "buyer@example.com",
  "password": "your-password"
}
```

Refresh and logout accept:

```json
{
  "refresh_token": "the-token-returned-by-login"
}
```

For authenticated requests, send `Authorization: Bearer <access_token>`. Logout invalidates the refresh token; an issued access token remains valid until its expiry.

Authentication responses with bodies use `data` and `meta`. The current metadata record has `requestId` and `pagination` fields; token responses explicitly use `access_token`, `refresh_token`, and `expires_in`. Application errors use `error.code`, `error.message`, and `error.details`; the current error envelope does not include a request ID.

## Repository Layout

```text
osporo-engine/
├── src/main/java/com/osporo/engine/
│   ├── auth/               Auth controller, services, tokens, DTOs
│   ├── user/               User entity, repository, service
│   ├── tenant/             Tenant and role configuration foundations
│   └── shared/             Security, configuration, responses, exceptions, enums
├── src/main/resources/
│   ├── application.properties
│   └── db/migration/       Flyway migrations V1–V12
├── src/test/               Unit, context, and authentication integration tests
├── docs/                   Project vision and architecture decision records
├── .github/workflows/      CI configuration
├── AGENTS.md               Agent engineering guidance
├── build.gradle
├── settings.gradle
├── gradle/                 Gradle wrapper configuration
├── gradlew
├── gradlew.bat
├── docker-compose.yml
├── Dockerfile
└── .env.example
```

## License

Osporo Engine is licensed under [Apache License 2.0](LICENSE). Its open-source foundation is intended to remain independently useful for deployment, customization, and extension.

Built by [Clicalyst](https://clicalyst.com).
