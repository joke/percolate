## MODIFIED Requirements

### Requirement: ExpansionHarness two-mode entry points

The class `io.github.joke.percolate.processor.test.ExpansionHarness` in `processor/src/test/groovy/` (Groovy source) SHALL expose exactly one `public static` entry point:

- `ExpansionResult expand(MapperGraph seed, List<Bridge> bridges, List<SourceStep> sourceSteps, List<GroupTarget> groupTargets)` — bypasses `ServiceLoader` and runs the expansion pipeline directly with the supplied lists. Used by failure-mode tests (which supply controlled bundles) and any isolation scenarios that need explicit strategy injection.

The previously specified `ExpansionResult expand(MapperGraph seed)` overload (which loaded `Bridge` / `SourceStep` / `GroupTarget` lists via `ServiceLoader`) is removed. Production-parity wiring smoke is no longer the harness's responsibility; that contract is asserted directly against `ServiceLoader` in `percolate-strategies-builtin`'s `BuiltinServiceRegistrationSpec` (see the new "Built-in service registration smoke spec" requirement).

The remaining entry point SHALL go through `assembleExpansionPipeline(...)` to obtain the wired stage. The entry point SHALL invoke `ValidatePathsPhase` and expose the resulting diagnostics on the returned `ExpansionResult`. It SHALL NOT assert auto-invariants; invariant checks are exposed on `ExpansionResult` for tests to opt into explicitly.

#### Scenario: Explicit mode bypasses ServiceLoader for engine tests
- **WHEN** `ExpansionHarness.expand(seed, bridges, sourceSteps, groupTargets)` is called from a failure-mode test
- **THEN** no call to `ServiceLoader` is made
- **AND** exactly the supplied lists are used

#### Scenario: Harness goes through pipeline assembly
- **WHEN** the explicit-list entry point is invoked
- **THEN** the wired `ExpandStage` is obtained via `ProcessorModule.assembleExpansionPipeline(...)`
- **AND** no other path constructs `ExpandStage` in test code

#### Scenario: Single-arg overload is absent
- **WHEN** the public surface of `ExpansionHarness` is inspected
- **THEN** no method with the signature `expand(MapperGraph)` exists
- **AND** every caller in `processor/src/test/` passes explicit strategy lists

### Requirement: Engine tests live in `processor/src/test/`; type-system fixtures live in `spi` testFixtures

Every test that exercises `ExpandStage`, its phases, the harness, or the assertions SHALL live in the `processor` module's test sources (`processor/src/test/groovy/`). No published Gradle module SHALL exist whose sole purpose is to host engine-test infrastructure.

Engine-specific helpers — `ExpansionAssertions`, `ExpansionHarness`, `GraphFixtures`, plus any internal stubs the harness needs — SHALL be co-located with the tests at `processor/src/test/groovy/io/github/joke/percolate/processor/test/` (Groovy sources). These helpers depend on engine internals (`MapperContext`, `Diagnostics`, `ProcessorModule`, `MapperGraph`, `ValidatePathsPhase`) and therefore cannot move out of the processor module.

Type-system fixtures — `TypeUniverse`, `HarnessResolveCtx` — SHALL live in `spi/src/testFixtures/groovy/io/github/joke/percolate/spi/test/` and be published via `percolate-spi`'s `testFixtures` configuration. These fixtures depend only on JDK + SPI types, so any SPI consumer (the `processor` module, the `strategies-builtin` module, third-party strategy modules) can consume them by declaring `testImplementation testFixtures(project(':spi'))`.

#### Scenario: No separate test-support module
- **WHEN** `settings.gradle` is inspected
- **THEN** it does NOT include any module whose sole purpose is to host test infrastructure for the expansion engine

#### Scenario: Engine-bound helpers live next to engine tests
- **WHEN** the source tree is inspected
- **THEN** `processor/src/test/groovy/io/github/joke/percolate/processor/test/` contains `ExpansionAssertions`, `ExpansionHarness`, `GraphFixtures`, and any engine-internal stubs they require (all Groovy sources)
- **AND** these classes are not exported by any other module

#### Scenario: Type-system fixtures live in spi testFixtures
- **WHEN** the source tree is inspected
- **THEN** `spi/src/testFixtures/groovy/io/github/joke/percolate/spi/test/` contains `TypeUniverse` and `HarnessResolveCtx` (Groovy sources)
- **AND** `processor/src/test/groovy/` does NOT contain duplicate copies of either class
- **AND** `processor`'s `build.gradle` declares `testImplementation testFixtures(project(':spi'))`
- **AND** `strategies-builtin`'s `build.gradle` declares `testImplementation testFixtures(project(':spi'))`

## REMOVED Requirements

### Requirement: Fake strategies as the engine-algebra input alphabet

**Reason**: The jqwik property tests under `processor/src/test/groovy/io/github/joke/percolate/processor/stages/expand/properties/` are deleted by `extend-property-discovery`. Those tests were the sole consumers of the `FakeStrategies` package (`IdentityBridge`, `ChainBridge`, `NoOpBridge`, `DivergentBridge`). The property tests asserted structural invariants of the harness rather than behaviour of the algorithm: they ran against fake bridges over a tiny `TypeUniverse` and did not surface any of the bugs fixed by recent expansion refactors (subgraph-scoped expansion, cross-group fixed point). Removing the fakes alongside the property tests eliminates an unused test-only SPI surface and the `net.jqwik:jqwik` dependency from `processor/build.gradle.kts`.

**Migration**: No external consumers — the package was annotated `final` and never SPI-registered. The directory `processor/src/test/groovy/io/github/joke/percolate/processor/stages/expand/properties/fakes/` is deleted in its entirety along with the seven property-test specs (`DeterminismSpec`, `IdempotenceSpec`, `MonotonicitySpec`, `OrderIndependenceSpec`, `DisjointAdditivitySpec`, `EmptyStrategyIdentitySpec`, `IdentityCollapseSpec`) and their shared `ExpansionPropertyBase`. Failure-mode tests that need controlled strategy injection continue to construct concrete `Bridge` / `GroupTarget` instances inline in their respective specs; no new shared test-strategy package is introduced. If `IdentityCollapseSpec`'s identity-collision invariant is worth preserving as an explicit assertion, it is enforced by `MapperGraph.addNode` deduplication and by the per-strategy specs in `strategies-builtin`; an explicit harness-level assertion is out of scope for this change.
