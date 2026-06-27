## Why

The last three changes each *declared* an architectural boundary, and within days each one *leaked*: `consumer-packaging` said "each module owns itself," yet `strategies-builtin` reaches into the root `docs/` tree; `e2e-test-architecture` said "the engine is tested via fakes; `processor` has no edge to any strategy," yet `SelfSeedExpansionSpec` — a pure engine-expansion test — lives in `strategies-builtin` and imports `processor.graph` / `processor.stages` internals. These boundaries exist only as discipline and memory files, so they re-erode on every change. We need them *declared in code and enforced by the build* before doing a documentation overhaul on top of them.

## What Changes

- Introduce an explicit **api / internal package split** in the `processor` engine, so "reaching into engine internals" becomes a definable, checkable fact rather than a convention. Internal packages (e.g. graph, stages, plan-extraction internals) move under an `internal` segment; the narrow surface other modules legitimately use stays public.
- Add **ArchUnit** (test scope) with a dedicated `architecture-tests` module that has every publishable module on its test classpath, encoding:
  - **Layering**: `annotations` ← `spi` ← `processor`; strategy modules depend on `spi` in production and on `processor` / `test-foundation` only in test; `test-foundation` is strategy-agnostic.
  - **Encapsulation**: no class outside `processor` depends on `processor`'s `internal` packages; the engine↔strategy line is crossed only through `spi` types.
  - **Conventions made executable** (today enforced only by memory): an `ExpansionStrategy` / `Strategy` implementation must not depend on `processor.graph` ("strategies stay myopic"); classes implementing `Stage` are named `*Stage`; no package cycles.
- **Move only the tests the new rules force out**: `SelfSeedExpansionSpec` (and any sibling engine-contract spec that the encapsulation rule now makes illegal in `strategies-builtin`) relocates to `processor`, driven by `FakeStrategy` instead of ServiceLoading the real builtins.
- **Tool choice — ArchUnit over Jigsaw (JPMS)**: the leaks are test-scope and build-config (JPMS's weakest areas), and the highest-value rules are member-of-type conventions JPMS cannot express; the stack (annotation processor on a flat AP classpath, Groovy/Spock, in-process Google compile-testing, ServiceLoader, solo maintainer) makes JPMS's compile-time guarantee costly and partly moot. ArchUnit matches the project's actual boundary vocabulary and runs in the existing harness. JPMS stays an option for later hardening.

Out of scope (explicitly deferred): the full contract-audit and re-sort of all `strategies-builtin/e2e` specs (a later change); the `docs/` `srcDir` reach (healed by the later documentation-overhaul change via antora-collector — this change must **not** add a rule that fails on the existing `srcDir`).

## Capabilities

### New Capabilities

- `module-boundaries`: the declared, build-enforced separation between percolate's modules — the `processor` api/internal split, the allowed inter-module dependency layering, the rule that the engine↔strategy boundary is crossed only through `spi`, the strategy-myopia / `*Stage` / no-cycles conventions, and the requirement that engine-contract tests do not live in a strategy module.

### Modified Capabilities

<!-- None. Relocating SelfSeedExpansionSpec brings reality into compliance with the EXISTING e2e-test-architecture requirements ("the engine is tested without real strategies"; "processor declares no edge to any strategy module"); it changes no spec-level requirement. The api/internal split is a structural refactor with no behavioural requirement change to the processor capability. -->

## Impact

- **New module**: `architecture-tests` (test-only, not published) depending on every publishable module so ArchUnit can import their classes; wired into `./gradlew check`.
- **`processor`**: package moves to introduce the `internal` segment; imports updated across the engine. No behavioural change; the generated-mapper contract and public processing surface are unchanged.
- **`strategies-builtin`**: `SelfSeedExpansionSpec` and any other engine-only specs leave; the module keeps its genuine builtin atom/output e2e tests.
- **Build**: a new ArchUnit dependency (test scope); new convention-enforcing tests that fail the build on a boundary violation.
- **Teams**: solo maintainer; the operational effect is that boundary drift now fails CI instead of accumulating silently.
