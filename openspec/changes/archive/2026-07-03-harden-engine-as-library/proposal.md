## Why

The `processor` engine is percolate's core, but today it is tested *through a classpath it should not need*:
fake strategies driving full compile-testing runs. That yields shallow, compile-coupled coverage (the
project's 60% branch gate) and leaves the engine's own logic — descent, hoisting, cost, plan extraction,
realisation diagnostics, nullness crossing — under-asserted at its seams. The engine should be trustworthy
like an external library: comprehensive **unit** tests at its own boundaries, **mutation-verified**, with no
strategy and no fake in sight. Real engine↔strategy integration is covered by the feature-e2e layer (the
next change), so the engine's fake-driven integration tests are redundant. See `openspec/notes.md` for the
three-layer plan this change opens.

## What Changes

- **Begin with a pitest spike.** Add the pitest (mutation testing) Gradle plugin scoped to the `processor`
  **unit** suite only — pitest is slow only against integration tests, fast on unit tests — and confirm it
  runs quickly, produces a useful mutation report, and supports a ratcheting threshold. The spike settles
  the plugin, the test-task wiring, and the initial mutation-score target before the bulk test-writing.
- **Raise the engine coverage gate** from `0.6` to `0.95` branch coverage for `processor`, measured on the
  unit suite.
- **Remove the engine integration tests** — the fake-driven compile-testing specs in `processor`
  (`EngineWeavingFakeStrategySpec`, the narrowed `SelfSeedExpansionSpec`, `GenerateStageFailureModesSpec`,
  `DocTagsEmissionSpec`, and any sibling that drives the engine through a compile with a fake). The
  now-unused `FakeStrategy` harness is dropped. Re-add an engine integration test only on a demonstrated
  need.
- **Write engine unit tests** to reach 95% and survive pitest: cover the discover/expand/generate/validate
  stages, the graph, plan extraction, cost, assembly hoisting, realisation diagnostics, and nullness
  crossing **at their own seams** — constructed inputs, asserted outputs, no compilation.
- **BREAKING (internal):** `processor`'s `integrationTest` suite is emptied; engine coverage comes from the
  unit suite + pitest. No production or behavioural change.

## Capabilities

### New Capabilities

- `engine-test-quality`: the engine is unit-tested as an isolated library to a 95% branch-coverage gate and
  mutation-tested (pitest) on its unit suite, declaring no test-time dependency on any strategy or fake.

### Modified Capabilities

- `e2e-test-architecture`: the "engine is tested without real strategies" requirement is updated — the
  engine is tested at its own seams by **unit tests** (not by `FakeStrategy` compile-tests), and engine
  integration/compile e2e is **removed** from `processor` (real integration moves to the feature layer).

## Impact

- **`processor`**: the `integrationTest` specs are removed; `FakeStrategy` usage drops out of engine tests;
  many new unit tests are added; the module's jacoco branch gate is raised to `0.95`.
- **`test-foundation`**: `FakeStrategy` (and its engine-side `META-INF/services` registration) becomes
  unused and is removed; `PercolateCompiler` stays for the feature-e2e layer.
- **Build**: the pitest plugin is added, scoped to the unit test task; a mutation-score threshold is
  introduced (ratcheted from the spike's baseline).
- **No production code change, no behavioural change** — this is engine test quality only.
- **Teams**: solo maintainer; net effect is an engine you can refactor against a fast, mutation-verified
  unit suite, with integration confidence supplied by the feature layer rather than fakes.
