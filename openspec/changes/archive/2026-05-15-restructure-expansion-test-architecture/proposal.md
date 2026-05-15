## Why

The previous change, `add-graph-expansion-tests` (archived 2026-05-14), scaffolded a separate `processor-test-support` module to host a fluent seed-graph DSL, harness, assertions, and property-test generators. In review, two flaws surfaced: (1) the `SeedDsl` builds malformed seed graphs whose directive target nodes carry no type — so the SPI-loaded production strategies never fire, and (2) the module's intended audience (external strategy authors) realistically writes mapper-level tests in their own downstream projects rather than building seed graphs against an internal harness. The result is a published API surface that maintains a maintenance burden without serving a concrete consumer, and a property-test suite whose algebraic laws pass trivially on no-op pipelines. The next step is to honestly restructure: collapse the framework into `processor/src/test/`, drive the engine algebra against fake strategies we control, and dog-food the harness on the built-in strategies that actually exist.

## What Changes

- **BREAKING** Remove the `processor-test-support` gradle module entirely. Drop it from `settings.gradle`, remove `testImplementation project(':processor-test-support')` from `processor/build.gradle`, and remove its `build.gradle` and `README.md`.
- Inline the test helpers worth keeping into `processor/src/test/groovy/io/github/joke/percolate/processor/test/` (Groovy sources): `TypeUniverse` (public-`JavacTask`-backed, unchanged), `GraphFixtures` (direct `MapperGraph` builders for SEED + REALISED + SUB_SEED shapes), `ExpansionAssertions` (AssertJ-style `reachable(...)`/`reportedError(...)`), and a thin `ExpansionHarness` wrapper around `ProcessorModule.assembleExpansionPipeline(...)`.
- **Drop `SeedDsl` entirely.** The fluent builder produces typeless directive-target nodes, has no realistic external audience, and its load-bearing use cases (engine tests, dog-food capability tests) are equally well served by direct `MapperGraph` construction via `GraphFixtures` builders and a small number of typed-node factories.
- **Drop `MapperSpec`, `StrategyBundle`, `PropertyTestBase` as published value types.** `MapperSpec.toGraph()` depended on `SeedDsl` and disappears with it. The engine-algebra property tests construct `MapperGraph` instances directly via `GraphFixtures` and parametric helpers; the `StrategyBundle` shape becomes an internal `record`-equivalent inside the test sources if useful.
- Drop the published `HarnessResolveCtx` and `HarnessScope`. The engine tests need equivalent stubs but they live next to the tests, not as published API.
- Add a `FakeStrategies` package in `processor/src/test/groovy/.../properties/fakes/` (Groovy sources) with at minimum `IdentityBridge`, `ChainBridge`, and `NoOpBridge`, each parameterised over `TypeUniverse`. The fakes implement the `Bridge`/`SourceStep`/`GroupTarget` SPI surface, return predictable steps for known type pairs, and produce a no-op `EdgeCodegen`. They are the input alphabet for the algebra tests.
- Rewrite the seven jqwik property specs (`DeterminismSpec`, `IdempotenceSpec`, `OrderIndependenceSpec`, `MonotonicitySpec`, `IdentityCollapseSpec`, `DisjointAdditivitySpec`, `EmptyStrategyIdentitySpec`) — Groovy sources whose methods carry jqwik `@Property` annotations — to use **explicit-mode** harness with strategy bundles drawn from fakes. Each property now actually exercises the engine: refactoring `ExpandStage` or its phases will break at least one property.
- Add a canary test that fails if no `REALISED` edge appears on a known-solvable seed-and-strategy pair (e.g., `String → String` with `IdentityBridge`). This guards against the silent-no-op regression the previous change failed to detect.
- Rewrite `ExpansionFailureModesSpec.groovy` to use explicit-mode harness with controlled fake bundles. Retain the cycle diagnostic test (via `GraphFixtures.subSeedCycle()` plus a no-op bundle). Add a **round-cap test** using a new `DivergentBridge` fake whose `bridge(from, to, ctx)` introduces a fresh synthetic intermediate node on every invocation, so the engine never converges and the `Expansion did not converge after N rounds` diagnostic fires. This resolves the previous change's deferred task 7.2.
- Rewrite `ExpansionCapabilitiesSpec.groovy` (in processor's test sources, dog-fooding the inlined harness) with real `reachable(...)`-style assertions for the built-in strategies that are wired and working today (`DirectAssign` at minimum). Seeds are constructed via direct `MapperGraph` + typed `Node` calls, not a DSL. Strategies that don't yet produce realised edges are noted in tasks as incremental work, not blockers.
- Strip the now-unused build infrastructure: the `checkNoInternalJavacImports` gradle task moves to `processor/build.gradle` (or is dropped if no `com.sun.tools.javac.*` imports remain in processor's sources), the `META-INF/services` rule disappears, the per-module jacoco override that was added for `processor-test-support` is removed, and the project-wide PMD exclusion of `AtLeastOneConstructor` stays (it's still needed for empty test classes).
- Replace any references to `processor-test-support` in `CLAUDE.md` / other docs with pointers to `processor/src/test/`.

## Capabilities

### New Capabilities

<!-- None. -->

### Modified Capabilities

- `expansion-test-harness`: Significantly reshape the spec. Drop requirements about the separate `processor-test-support` module, the no-internal-javac-imports build rule, the `SeedDsl` fluent builder, and the `MapperSpec`/`StrategyBundle` jqwik infrastructure. Retain the harness-shape requirements (two-mode entry, single source of truth via `assembleExpansionPipeline`), the `TypeUniverse` requirement (now sourced via public `JavacTask`), the AssertJ-style assertion contract, and the property/Spock test coverage requirements — re-anchored to `processor/src/test/` and to engine tests driven by fake strategies. Add new requirements covering the fake-strategy input alphabet, the explicit-mode property pattern, the canary test, and the dog-fooded built-in capability rows.

## Impact

- **Code**:
  - `processor-test-support/` module deleted entirely (~12 source files, the `build.gradle`, the `README.md`, the build directory).
  - New `processor/src/test/groovy/io/github/joke/percolate/processor/test/` package (Groovy sources) holds the inlined helpers (`TypeUniverse`, `GraphFixtures`, `ExpansionAssertions`, `ExpansionHarness`) plus internal stubs (`HarnessResolveCtx`-equivalent, `HarnessScope`-equivalent) co-located with the tests that need them.
  - New `processor/src/test/groovy/io/github/joke/percolate/processor/stages/expand/properties/fakes/` package (Groovy sources) holds `FakeStrategies` (`IdentityBridge`, `ChainBridge`, `NoOpBridge`, …).
  - `processor/src/test/groovy/.../properties/` property specs (Groovy + jqwik `@Property`) rewritten to use fakes via explicit-mode harness.
  - `processor/src/test/groovy/.../ExpansionCapabilitiesSpec.groovy` rewritten with real reachability assertions for working built-in strategies. Seeds constructed via direct `MapperGraph`/`Node`/`Edge.elementSeed(...)` calls.
  - `processor/src/test/groovy/.../ExpansionFailureModesSpec.groovy` retained, trimmed as needed for the dropped DSL.
  - `SeedDsl`, `MapperSpec`, `StrategyBundle`, `PropertyTestBase`, `HarnessResolveCtx`, `HarnessScope` all deleted (they moved with the module).
- **Build**:
  - `settings.gradle` loses `include 'processor-test-support'`.
  - `processor/build.gradle` loses `testImplementation project(':processor-test-support')`; gains direct `testImplementation` entries that previously transitively flowed through the module (`jqwik`, `assertj-core` are already direct).
  - `dependencies/build.gradle` may drop the platform entry for `jqwik` if it's only used by `processor` (likely retained — it's reasonable for the platform to manage versions).
  - `checkNoInternalJavacImports` either relocates to `processor/build.gradle` or is removed.
  - Per-module jacoco override (verification disabled on `processor`, enforced on `processor-test-support`) is replaced by a single project-level configuration appropriate for `processor`.
- **Specs**: `openspec/specs/expansion-test-harness/spec.md` is significantly modified — most requirements removed or rewritten.
- **CI**: Test execution time roughly unchanged. Number of modules drops by one. PMD/Spotless/CodeNarc/NullAway gates continue to apply to `processor`.
- **Dependencies**: No new external deps. `compile-testing` confirmed unused by the test layer (already removed in the previous change). `com.palantir.javapoet:javapoet` moves from `processor-test-support`'s `implementation` to `processor`'s `testImplementation` (it's already on processor's `implementation` for production codegen, so this may be a no-op).
- **Teams affected**: solo project (`joke@xckk.de`); no cross-team coordination required.
- **Out of scope** (explicitly): a `StrategyContractTest` base class that formalises the SPI conformance contract for external strategy authors — deferred until a real external consumer surfaces. Harness-side auto-invariant assertion (the strong-D6 model where the harness throws on convergence/identity-collapse/orphan-realised violations) — kept as opt-in checks on `ExpansionResult`, matching the prior change's revised D6. The previous change's deferred task 7.3 ("opt out of convergence auto-invariant") is closed by design rather than deferred: under explicit opt-in there is no auto-invariant to opt out of.
