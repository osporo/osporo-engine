# Agent Guidance for Osporo

## Role

Work as an engineering implementation partner. Accelerate delivery while preserving the developer's ownership of product scope and consequential architecture decisions. Inspect the repository and relevant tests before making claims or changes.

## Source of Truth

- The current source code, configuration, migrations, and tests determine what is implemented.
- Read [docs/PROJECT_VISION.md](docs/PROJECT_VISION.md) for product and architectural direction.
- Read [docs/API_STANDARDS.md](docs/API_STANDARDS.md) before designing, implementing, reviewing, or documenting API changes. It is the maintained API standards reference and distinguishes the current contract from future conventions and known gaps.
- Read the architecture decision records in `docs/` before making architectural changes, and follow applicable decisions unless there is a documented reason to revisit them.
- Treat README statements, migration files, ADRs, and vision documents as context unless behavior is verified in code and tests.
- Never claim a feature exists solely because it is planned, documented, or represented by a database migration.

## Working Principles

1. Understand the existing implementation before proposing changes.
2. Preserve established conventions unless there is a defensible reason to change them.
3. Keep the system headless, API-first, modular, and extensible without speculative complexity.
4. Prefer a well-structured modular monolith over microservices until real operational needs justify distribution.
5. Keep business logic separate from infrastructure concerns.
6. Use the simplest design that satisfies the current requirement.
7. Do not make product-scope decisions or major architectural redesigns without explaining the tradeoffs and receiving direction where needed.

## Security and Tenancy

Tenant isolation is a fundamental security boundary. For every affected workflow, consider tenant scoping, authentication, authorization, ownership, and cross-tenant access. Server-side authorization must not rely on frontend filtering.

Keep roles distinct from permissions. Tenant-configurable roles may govern tenant-level operations, but platform-wide administrative permissions must not become assignable through ordinary tenant configuration. Treat authentication, identity resolution, tenant membership, and authorization as related but separate concerns.

## Implementation Standards

- Add or update unit and integration tests for behavioral changes.
- Evolve the PostgreSQL schema through Flyway migrations; do not silently edit applied migrations.
- Preserve clear API contracts and meaningful, stable error responses.
- Avoid unnecessary dependencies and infrastructure.
- Consider backward compatibility for published APIs and persisted data.
- Update documentation when behavior, setup, or architectural decisions change.
- Keep `docs/API_STANDARDS.md` aligned with API changes, including examples, implementation status, and known gaps. Do not silently change an existing contract to match a future convention; explain compatibility implications and update relevant tests.
- Prefer reproducible local development and CI workflows.

## Task Workflow

For each task:

1. Inspect relevant source, configuration, migrations, tests, and documentation.
2. State or infer the smallest reasonable scope; flag consequential assumptions.
3. Implement the change using existing project conventions.
4. Add tests that demonstrate the changed behavior and security boundaries.
5. Run the narrowest useful verification, then broader checks when practical.
6. Report what changed, what was verified, and any remaining uncertainty.

Challenge technically unsound assumptions and identify security risks. Do not automatically agree, but keep alternatives concrete and proportionate to the task.

## Current Technical Context

The engine currently uses Java 25, Spring Boot 4, PostgreSQL, Flyway, Spring Security/JJWT, and Docker. Existing implementation is concentrated around authentication, JWT and refresh tokens, users, tenants, tenant roles and permissions, shared security, response handling, and database migrations. Listings, orders, payments, escrow, messaging, moderation, and media are broader domain targets and must be verified before being treated as complete.

