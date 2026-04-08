# ADR-006: Business Source Licence for the Open Core Model

**Status:** Accepted  
**Date:** 2026-03-12  
**Author:** Wahina Prince Chabalala  
**Deciders:** Wahina Prince Chabalala  

---

## Context

Osporo is an open core product. The engine and reference frontend are publicly
available on GitHub. A business — Osporo Cloud — is built on top of this open
source foundation by offering the engine as a managed, hosted service.

This creates a fundamental licensing tension: the code must be open enough to
build a developer community and earn trust through transparency, but protected
enough that a well-resourced competitor cannot clone the repository and launch
a directly competing hosted service without cost or contribution.

This tension is not hypothetical. It has played out repeatedly in the open
source infrastructure space. HashiCorp (Terraform), Elasticsearch, MongoDB,
and Redis all navigated versions of this problem and each made different
choices at different points in their lifecycle. The licensing decision made at
inception shapes the community relationship for the life of the project.

---

## Decision

**`osporo-engine` and `osporo-frontend` will be licenced under the Osporo
Source Licence 1.0, a custom licence derived from the Business Source
Licence 1.1 (BUSL-1.1).**

BUSL 1.1 was adopted as the foundation because it is the most credible
source-available licence in the infrastructure space, with adoption by
HashiCorp, CockroachDB, and Sentry establishing it as a known and
respected model. However, BUSL's standard Additional Use Grant language
— centred on "competing services" — was too vague for Osporo's specific
protection requirement.

The core ambiguity in standard BUSL is the agency model: an agency
running a single Osporo instance to host fifty client marketplaces looks
structurally similar to a SaaS operator running a single instance for
fifty paying subscribers. Standard BUSL does not cleanly distinguish
between them. The Osporo Source Licence 1.0 resolves this by introducing
defined terms — Managed Use and Self-Service Use — and by anchoring the
prohibition on what is being sold (access to the Licensed Work's
functionality) rather than on how provisioning is delivered.

The practical consequence of deriving from rather than using verbatim
BUSL is that automated licence scanners may not recognise it and some
legal reviewers will flag it as non-standard. This is an accepted
tradeoff — the precision of the restriction is worth the scanner
recognition cost.

The licence includes:
- A Managed Use / Self-Service Use distinction with explicit definitions
- A prohibition anchored on primary value delivered, not provisioning
  method, closing the "manual SaaS" loophole
- A patent grant mirroring Apache 2.0
- A termination clause providing automatic rights termination on violation
- A Change Date of four years per version, converting to Apache 2.0

osporo-docs is licenced under MIT. Documentation should be freely
copyable and redistributable without restriction.

osporo-cloud is proprietary and not publicly released.

---

## Reasons For This Decision

### 1. MIT and Apache 2.0 leave the business unprotected

Permissive licences (MIT, Apache 2.0) grant anyone the right to use the code
for any purpose, including building a directly competing hosted service. A
well-funded competitor who clones the repository and offers Osporo-as-a-Service
with a larger marketing budget is a real risk under a permissive licence.

This is not theoretical. Amazon's history with Elasticsearch — running
Amazon Elasticsearch Service using Elastic's open source code without
contributing back or paying for licences — is the canonical example of why
infrastructure companies with permissive licences are vulnerable to large
platform competitors.

Osporo is not yet large enough to be vulnerable to Amazon. But establishing
the correct licence boundary at inception is easier than changing it later —
licence changes on established open source projects damage community trust
significantly (as HashiCorp discovered when they changed Terraform's licence
after years of MIT).

### 2. GPL/AGPL is too restrictive for the target audience

Copyleft licences (GPL, AGPL) require derivative works to be distributed under
the same licence. For Osporo's target audience — developers and agencies building
marketplace products for clients — this creates a significant problem. An agency
that customises `osporo-frontend` for a client's marketplace and deploys it would
potentially be required to open source their customisations under AGPL.

This is unacceptable for commercial client work. A licence that legal departments
at agencies flag as a risk is a licence that prevents adoption. Osporo's growth
depends on agencies and developers choosing it for client projects — the licence
cannot create legal uncertainty for that use case.

### 3. BUSL protects exactly the use case that matters

BUSL's "Additional Use Grant" can be written to precisely define what is
permitted and what requires a commercial licence. Osporo's grant is written to
protect the one use case that constitutes a direct competitive threat: operating
a hosted multi-tenant marketplace service for third parties.

Everything else — self-hosting, client deployments, forks, modifications,
internal use — is explicitly permitted. The developer community can use, extend,
and build on Osporo without encountering the licence restriction in normal usage.
Only the specific use case of launching a competing SaaS hits the boundary.

### 4. The four-year Change Date maintains long-term community trust

BUSL is sometimes criticised as "not really open source" because the commercial
use restriction is technically not OSI-compliant. The Change Date addresses this
directly: every version of Osporo will eventually become fully open source under
Apache 2.0. The restriction is temporary, not permanent.

Four years is the chosen window because it represents a reasonable commercial
exclusivity period for a given version — long enough to build a viable business
on it, not so long that the community feels the code is permanently withheld.
By the time a version's restriction expires, Osporo Cloud will be competing on
execution, reliability, support, and brand — not on code exclusivity.

### 5. Precedent from respected infrastructure companies

BUSL was created by MariaDB and has been adopted by HashiCorp (Terraform, Vault,
Consul), CockroachDB, and Sentry. These are credible, developer-respected
infrastructure companies. Their adoption of BUSL demonstrates that the licence
is viable in the developer community and does not prevent open source adoption
at scale. Developers who evaluate Osporo will be familiar with BUSL from these
precedents.

---

## Alternatives Considered

### BUSL 1.1 verbatim

**Evaluated but insufficient.** BUSL 1.1 was the starting point and
remains the foundation. The standard Additional Use Grant language was
assessed and found to be too vague for Osporo's specific protection
requirement — specifically, it does not cleanly distinguish between an
agency hosting client marketplaces (permitted) and a SaaS operator
selling access to marketplace functionality (prohibited). A derivative
licence — Osporo Source Licence 1.0 — was created to introduce the
Managed Use / Self-Service Use distinction and anchor the prohibition
on primary value delivered rather than provisioning method. The full
BUSL 1.1 terms are otherwise preserved.

### MIT Licence

**Rejected.** Provides no protection against a competitor offering Osporo as
a hosted service. The entire business model of Osporo Cloud is at risk under
a permissive licence with sufficient competitive pressure.

### Apache 2.0

**Rejected** for the same reasons as MIT, with the addition that Apache 2.0's
patent grant provisions, while valuable, do not address the competitive risk.

### GNU Affero General Public Licence (AGPL)

**Rejected.** Copyleft requirements create legal uncertainty for agencies doing
client work — the primary developer audience for Osporo. Adoption risk outweighs
the protection benefit.

### Server Side Public Licence (SSPL)

**Rejected.** SSPL was created by MongoDB and is more restrictive than BUSL.
It requires that anyone offering the software as a service must open source their
entire stack — infrastructure code, tooling, everything. This is widely regarded
as unworkable and has damaged MongoDB's community relationship. SSPL is more
controversial than BUSL and provides no meaningful additional protection for
Osporo's specific concern.

### Dual Licence (Community Edition + Commercial Edition)

**Rejected at inception.** Maintaining two separate codebases — a stripped-down
community edition and a full commercial edition — requires splitting engineering
effort and creates a permanent community resentment dynamic ("the real features
are behind the paywall"). BUSL with a clear Additional Use Grant achieves the
same commercial protection with a single codebase, no feature splitting, and a
cleaner community relationship.

---

## Consequences

### Positive

- The competitive use case (hosted MaaS competitors) is legally protected.
- Self-hosting, client deployment, and agency use are explicitly permitted —
  no friction for the primary developer audience.
- The Change Date ensures no version is permanently restricted — long-term
  community trust is maintained.
- BUSL precedent from HashiCorp and others means developers recognise and
  understand the licence.

### Negative

- BUSL is not OSI-approved. Some developers and organisations have policies
  against using non-OSI licences. This will exclude a segment of potential
  contributors and adopters.
- The licence boundary requires clear documentation. Developers must understand
  exactly what "offering as a hosted service" means in the Additional Use Grant
  to know whether their use case is permitted. Ambiguity creates friction.
- Future licence changes (if needed) would damage community trust. The BUSL
  decision should be treated as a long-term commitment, not a revisable preference.

---

## Additional Use Grant Text

The following Additional Use Grant is included in the BUSL licence file:

> You may make production use of the Licensed Work, including use in a commercial
> product or service, provided that your use does not include offering the Licensed
> Work to third parties on a hosted or embedded basis in order to compete with
> Osporo's paid versions of the Licensed Work.
>
> For purposes of this licence, "offering the Licensed Work to third parties on a
> hosted basis" means operating the Licensed Work as a multi-tenant marketplace
> infrastructure service where third parties are the marketplace operators.
>
> Permitted uses include: self-hosting for your own marketplace, deploying for a
> client's marketplace as part of professional services, forking and modifying for
> either of the above purposes.

---

## Revisit Criteria

This decision should be revisited when:

- The open source community's reaction to BUSL measurably reduces adoption to
  a degree that undermines the business more than the competitive protection
  helps it
- Osporo Cloud reaches a scale where brand, support, and execution moat are
  sufficient protection and full Apache 2.0 conversion would meaningfully
  accelerate community growth
- A specific large enterprise customer or integration partner requires an OSI-
  compliant licence as a condition of adoption

---

## References

- [Business Source Licence 1.1 full text](https://mariadb.com/bsl11/)
- HashiCorp's rationale for adopting BUSL for Terraform (2023)
- [Elastic's licence change announcement and community response](https://www.elastic.co/blog/why-license-change)
- ADR-005: Build vs Buy Decisions (commercial context)
