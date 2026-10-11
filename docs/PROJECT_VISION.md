# Osporo Project Vision

## What Osporo Is

Osporo is open-source marketplace infrastructure. It exists to make building, deploying, and operating online marketplaces substantially easier by providing reusable foundations for capabilities such as identity, tenants, listings, orders, payments, messaging, moderation, and marketplace administration.

Osporo is not itself a marketplace. It is infrastructure for creating many different kinds of marketplaces, including physical-product, digital-goods, service, vehicle, B2B, and other specialized marketplaces. These examples describe the intended flexibility of the platform, not a claim that every workflow is currently implemented.

The long-term ambition is for Osporo to become a marketplace infrastructure ecosystem rather than a backend for one fixed business model.

## Product Directions

### Osporo Engine

Osporo Engine is the open-source, headless backend for developers and engineering teams. It should remain independently useful and should enable consumers to:

- self-host the engine;
- build their own web, mobile, and administrative frontends;
- integrate their own infrastructure, identity providers, and services;
- extend marketplace workflows for specific business requirements; and
- control their marketplace branding and customer experience.

The current technology direction is Java 25, Spring Boot 4, PostgreSQL, Flyway, and Docker. The engine is intended to use the Apache License 2.0.

**Fundamental principle:** Osporo Engine must remain useful independently of Osporo Cloud.

### Osporo Cloud

Osporo Cloud is a future hosted, commercial product intended to make marketplace creation accessible to less technical users and businesses. It may eventually include hosted provisioning, onboarding, administration, visual storefront customization, themes, managed infrastructure, billing, domains, dashboards, and analytics.

Cloud is a future direction, not a claim about currently implemented functionality. The engine should expose capabilities that Cloud can consume and orchestrate without introducing Cloud-specific concerns into the core.

## Architectural Principles

- **Headless and API-first:** clients should not depend on a particular frontend.
- **Extensible but deliberate:** provide reusable marketplace primitives and extension points without speculative abstractions or hardcoded assumptions about one marketplace category.
- **Identity-provider independent over time:** the current Osporo-managed JWT implementation is a present implementation detail, not a permanent requirement. Authentication, identity resolution, tenant membership, and authorization should remain conceptually distinct so external identity systems can eventually be integrated.
- **Tenant isolation by design:** the shared PostgreSQL/shared-schema direction requires explicit tenant scoping and authorization in application and service logic. Frontend filtering is never a security boundary.
- **Permission-based authorization:** roles and permissions are distinct; tenant configuration may control tenant-level authorization, but platform-wide administrative capabilities must not become tenant-assignable.
- **Clear modular boundaries:** authentication, tenants, listings, orders, payments, moderation, and messaging are distinct responsibilities. A modular monolith is preferred until operational or scaling needs justify distribution.

Known role categories include `BUYER`, `SELLER`, `MARKETPLACE_STAFF`, `MARKETPLACE_OWNER`, and `OSPORO_ADMIN`.

## Domain Vision

The broader target domain includes identity and users, tenants, listings and categories, orders, payment integrations, escrow-like workflows, messaging, moderation and reporting, and media storage. Stripe Connect and Cloudflare R2 are referenced in the repository's intended direction, but provider capabilities, legal requirements, and regulatory constraints must be validated before describing a workflow as supported.

These are target capabilities, not proof of implementation. Always distinguish among:

1. functionality verified in the current source;
2. repository documentation or migrations describing planned work; and
3. longer-term product ideas.

## Engineering Quality

Osporo aims to be credible, production-quality open-source infrastructure. Development should emphasize maintainable design, separation of business and infrastructure concerns, explicit security boundaries, Flyway schema evolution, unit and integration tests, CI checks, clear API contracts, useful errors, documentation, reproducible development environments, and backward compatibility for published contracts.

Quality does not mean unnecessary complexity. Prefer the simplest design that meets current requirements while preserving important boundaries. Do not add distributed infrastructure, elaborate patterns, or speculative abstractions merely to appear enterprise-grade.

## Open-Source Philosophy

The engine should be real, independently useful infrastructure—not a deliberately restricted demonstration of Cloud. Developers should be able to adopt, deploy, customize, and extend it under its license. The commercial opportunity should come from managed infrastructure, convenience, hosting, operational tooling, and the wider Cloud experience rather than from making the core unusable without Osporo-operated services.

## Long-Term Direction

Osporo aims to become a foundation on which developers, entrepreneurs, and businesses can create many kinds of marketplaces without repeatedly solving the same infrastructure problems. The immediate priority is a reliable, extensible engine with sound architectural foundations. Possible future integrations, operational tools, and developer services are directional possibilities, not committed roadmap items.

> Osporo is not being built simply to reproduce an existing marketplace application. It is being built to make the development of many different marketplace applications easier.

