# ADR-006: Apache 2.0 Licensing for Open Core Strategy

**Status:** Accepted  
**Date:** 2026-03-12  
**Author:** Wahina Prince Chabalala  
**Deciders:** Wahina Prince Chabalala

---

## Context

Osporo is an open core product. The engine and reference frontend are
publicly available on GitHub. A commercial offering — Osporo Cloud —
will be built on top of this foundation as a managed, hosted service.

A key early decision is how the core repositories should be licensed.

Two competing forces influence this decision:

- **Adoption and community growth** require a licence that is widely
  understood, unambiguous, and trusted by developers and organisations.
- **Commercial protection** suggests restricting the ability for third
  parties to offer Osporo as a competing hosted service.

Many infrastructure companies have faced this trade-off. Companies such
as Frappe, GitLab have adopted
source-available licences (e.g. BUSL, SSPL) to protect their cloud
businesses after initially using permissive licences.

However, these licence changes often occurred after significant community
adoption, and in some cases resulted in ecosystem fragmentation or forks.

At Osporo's current stage, the primary risk is not competition from large
platform providers, but lack of adoption, limited community engagement,
and insufficient real-world usage.

---

## Decision

**`osporo-engine` and `osporo-frontend` will be licensed under the
'Apache License 2.0'**

**`osporo-docs` will be licensed under the MIT License**

**`osporo-cloud` will remain proprietary and will not be publicly released.**

---

## Reasons For This Decision

### 1. Adoption is the primary constraint at this stage

Osporo's success depends on developers and agencies choosing to use,
extend, and contribute to the platform. A permissive, widely recognised
licence removes friction from:

- contributing to the codebase
- adopting Osporo in commercial projects
- integrating Osporo into existing systems

Custom or source-available licences introduce uncertainty. Developers may
avoid contributing or adopting the project if they do not clearly
understand the legal boundaries.

---

### 2. Apache 2.0 provides clarity with minimal downside

Apache 2.0 is a well-understood, OSI-approved licence that:

- permits commercial use, modification, and redistribution
- includes an explicit patent grant
- is widely accepted by both individual developers and enterprises

Compared to MIT, Apache 2.0 provides additional legal protection (notably
around patents) without introducing meaningful complexity for users.

---

### 3. Early-stage competitive risk is low

The risk of a large company forking Osporo and launching a competing
hosted service exists in theory, but is not a realistic near-term concern.

Large platform providers typically move when:

- a market is validated
- demand is proven
- the product has achieved meaningful adoption

At Osporo's current stage, the dominant risk is lack of traction, not
commoditisation.

---

### 4. Execution is a stronger moat than licensing

By choosing a permissive license, Osporo explicitly does not rely on
licence restrictions as its primary form of competitive protection.

Instead, long-term differentiation will come from:

- product quality and velocity
- developer experience (SDK, CLI, documentation)
- hosted offering reliability and ease of use (Osporo Cloud)
- community, ecosystem, and brand

This aligns with the approach taken by companies such as GitLab, Supabase and Vercel which compete on execution rather than code exclusivity.

---

### 5. Maximising career and learning outcomes

Osporo is not only a product, but also a vehicle for:

- developing deep system design expertise
- building publicly visible technical work
- creating opportunities through content, community, and networking

A permissive licence maximises exposure, usage, and contribution,
which in turn maximises these outcomes.

---

## Alternatives Considered

### Business Source Licence (BUSL)

**Rejected.** While BUSL provides protection against competing hosted
services, it introduces complexity and ambiguity that can discourage
adoption. Customising BUSL further increases legal uncertainty and
reduces recognisability.

---

### GNU Affero General Public Licence (AGPL)

**Rejected.** Strong copyleft requirements create friction for agencies
and commercial users, particularly when building client solutions.
Adoption risk outweighs protection benefits.

---

### MIT Licence

**Considered.** MIT provides maximum simplicity, but lacks the explicit
patent protections included in Apache 2.0. Apache 2.0 was selected as a
slightly more robust permissive alternative.

---

## Consequences

### Positive

- Zero ambiguity for developers and contributors
- Maximum compatibility with commercial usage
- Increased likelihood of adoption and community growth
- Strong alignment with industry-standard open source practices

---

### Negative

- No protection against competitors offering Osporo as a hosted service
- Potential for forks or competing distributions
- Competitive moat must be built through execution, not licensing

---

## Strategic Implications

By adopting Apache 2.0, Osporo explicitly commits to:

- competing on product quality and developer experience
- building a strong ecosystem (SDK, CLI, integrations)
- treating open source as a growth and distribution strategy, not a
  controlled asset

The success of Osporo will depend on becoming the **default and most
trusted implementation**, rather than the only available one.

---

## Revisit Criteria

This decision should be revisited if:

- Osporo achieves significant adoption and becomes a clear target for
  direct SaaS competition
- competitive pressure begins to materially impact the viability of
  Osporo Cloud
- a transition to a different licensing model can be made without
  damaging community trust

---

## References

- HashiCorp licensing changes (2023)
- Elastic licence change and OpenSearch fork
- ADR-005: Build vs Buy Decisions