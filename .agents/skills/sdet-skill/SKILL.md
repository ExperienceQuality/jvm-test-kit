---
name: sdet-skill
description: Maintain or develop this repository's framework-neutral backend test SDK, including its APIs, fixtures, JUnit support, service clients, contracts, dependencies, CI, and behavior documentation. Use for feature work, fixes, refactors, compatibility changes, and SDK design; skip tiny documentation-only edits.
---

# SDET workflow

Own requested SDK work through two phases separated by explicit user approval.

## Plan phase

Inspect relevant implementation, tests, build configuration, fixtures, documentation, and working-tree state. Establish current behavior with repository evidence.

Challenge the request when evidence shows hidden assumptions, scope creep, speculative features, needless dependencies, framework coupling, compatibility loss, weak verification, or a simpler design. Recommend rejection when that best protects the SDK. Offer one recommendation and at most two material alternatives. Ask only questions whose answers change the design.

Return a plan containing:

- goal and explicit non-goals;
- current behavior and evidence;
- proposed public API with a consumer usage example when public behavior changes;
- existing abstractions to reuse;
- source, binary, behavior, and migration impact;
- test strategy and likely changed files;
- risks, alternatives, and checkable acceptance criteria.

Stop after the plan. Treat implementation as authorized only when the parent explicitly says the user approved this plan. Discussion, questions, and requested plan changes are not approval. If the plan changes materially after approval, return the revised plan and wait for fresh approval.

## Design standards

Keep the SDK framework-neutral. Add Spring or other framework integration only when explicitly requested in an approved plan.

Optimize public APIs for ease of use and fluent discovery:

- express consumer domain language in names;
- keep the common path short with sensible defaults;
- choose immutable staged builders or ordinary fluent builders per use case and justify the choice;
- enforce required state at compile time when practical;
- reject invalid combinations early with actionable messages;
- provide escape hatches without exposing internals;
- design from concrete usage examples, not speculative abstraction.

Preserve deterministic isolation, parallel-test safety, clear failure diagnostics, minimal consumer setup, no hidden production dependencies, fast execution, supported Java compatibility, and existing consumer behavior.

Preserve public source and binary compatibility by default. Breaking changes require an explicit plan section, migration path, and user approval. Keep deprecated APIs for at least one release cycle. New public APIs require a fluent usage example plus misuse and error-path coverage. Internal APIs may change when observable behavior remains stable.

## Implementation and verification phase

Implement only approved scope. Preserve unrelated working-tree changes.

Apply verification by change type:

- Bug fix: first demonstrate the defect with a failing regression test.
- Feature: test public behavior, misuse, and the proposed fluent usage.
- Database or container behavior: use a real Testcontainers integration test rather than mocks.
- Public API change: verify the clean-consumer fixture and run `japicmp` when a baseline is available.
- Every code change: run `./gradlew check` plus narrower relevant tests.
- Behavior or workflow change: update affected documentation or CI configuration.

Reject flaky, timing-dependent, or order-dependent tests. Broaden or repeat verification only when failures, new edits, or unresolved risk justify it.

Do not commit, push, publish, release, or change the project version unless explicitly requested.

Completion requires all approved acceptance criteria accounted for. Report changed files, commands and results, compatibility impact, and remaining risks. Distinguish tests not run from tests that passed.
