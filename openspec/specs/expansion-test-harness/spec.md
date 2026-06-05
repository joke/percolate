# expansion-test-harness Specification

## Purpose
TBD - created by archiving change add-graph-expansion-tests. Update Purpose after archive.
## Requirements
### Requirement: assembleExpansionPipeline factory

The class `io.github.joke.percolate.processor.ProcessorModule` SHALL expose a `public static` method `assembleExpansionPipeline(List<ExpansionStrategy> strategies, ResolveCtx resolveCtx, NullabilityResolver nullabilityResolver)` that returns a wired `ExpandStage` instance. The returned stage SHALL run a single `ExpansionPhase` — `ExpandGroupsPhase`, constructed via `ExpandGroupsPhase.create(strategies, resolveCtx, nullabilityResolver)`. There is no `ResolveSourceChainsPhase`, `ResolveTargetChainsPhase`, or `BridgeSourceToTargetPhase`, and no separate `Bridge`/`SourceStep`/`GroupTarget` strategy lists — the expansion surface is the single unified `List<ExpansionStrategy>`.

The production `@Provides ExpandStage expandStage(List<ExpansionStrategy> strategies, ResolveCtx resolveCtx, NullabilityResolver nullabilityResolver)` provider SHALL delegate to `assembleExpansionPipeline(...)`, so production Dagger wiring and test wiring compose the stage through the one factory. `assembleExpansionPipeline` SHALL be the single source of truth for `ExpandStage` composition: adding a constructor parameter to `ExpandStage` or to `ExpandGroupsPhase` SHALL force callers to update through this one method.

#### Scenario: Factory returns a wired ExpandStage
- **WHEN** `ProcessorModule.assembleExpansionPipeline(strategies, resolveCtx, nullabilityResolver)` is invoked with non-null arguments
- **THEN** a non-null `ExpandStage` is returned whose phase list is `[ExpandGroupsPhase]`
- **AND** invoking `run(ctx)` on it produces the same expanded graph that production Dagger wiring would produce given the same unified strategy list

#### Scenario: Dagger wiring delegates to the factory
- **WHEN** the source of `ProcessorModule` is inspected
- **THEN** the `@Provides ExpandStage` provider calls `assembleExpansionPipeline(...)` with the SPI-loaded `List<ExpansionStrategy>`, `ResolveCtx`, and `NullabilityResolver`
- **AND** there is no separate construction of `ExpandStage` outside `assembleExpansionPipeline`

### Requirement: ExpansionHarness two-mode entry points

The class `io.github.joke.percolate.processor.test.ExpansionHarness` in `processor/src/test/groovy/` (Groovy source) SHALL expose exactly one `public static` entry point:

- `ExpansionResult expand(MapperGraph seed, List<ExpansionStrategy> strategies)` — bypasses `ServiceLoader` and runs the expansion pipeline directly with the supplied **unified** strategy list. Used by property tests and isolation scenarios that need explicit strategy injection.

There is no `expand(MapperGraph seed)` `ServiceLoader`-loading overload, and no overload taking separate `Bridge` / `SourceStep` / `GroupTarget` lists. Production-parity wiring smoke is asserted directly against `ServiceLoader` in `percolate-strategies-builtin`'s `BuiltinServiceRegistrationSpec` (see "Built-in service registration smoke spec").

The entry point SHALL obtain the wired stage via `ProcessorModule.assembleExpansionPipeline(...)` and expose the captured diagnostics on the returned `ExpansionResult`. It SHALL NOT assert auto-invariants; invariant checks are exposed on `ExpansionResult` for tests to opt into explicitly.

#### Scenario: Explicit mode bypasses ServiceLoader for engine tests
- **WHEN** `ExpansionHarness.expand(seed, strategies)` is called from a property/isolation test
- **THEN** no call to `ServiceLoader` is made
- **AND** exactly the supplied unified `List<ExpansionStrategy>` is used

#### Scenario: Harness goes through pipeline assembly
- **WHEN** the explicit-list entry point is invoked
- **THEN** the wired `ExpandStage` is obtained via `ProcessorModule.assembleExpansionPipeline(strategies, resolveCtx, nullabilityResolver)`
- **AND** no other path constructs `ExpandStage` in test code

#### Scenario: Only the unified-list overload exists
- **WHEN** the public surface of `ExpansionHarness` is inspected
- **THEN** the only `expand` entry point takes `(MapperGraph, List<ExpansionStrategy>)`
- **AND** no overload takes separate `Bridge` / `SourceStep` / `GroupTarget` lists, and no `expand(MapperGraph)` overload exists

### Requirement: TypeUniverse fixture

The class `io.github.joke.percolate.spi.test.TypeUniverse` in `spi/src/testFixtures/groovy/` (Groovy source, published via `percolate-spi`'s `testFixtures` configuration) SHALL expose a fixed set of `javax.lang.model.type.TypeMirror` instances backed by a single, JVM-lifetime `com.sun.source.util.JavacTask` held in a `static final` field. The task SHALL be obtained via `ToolProvider.getSystemJavaCompiler().getTask(null, null, null, null, null, null)` (no sources, no diagnostic listener) and cast to `com.sun.source.util.JavacTask`. The fixture SHALL NOT call `task.parse()`, `task.analyze()`, or `task.call()`; bootstrap class resolution happens lazily inside `Elements.getTypeElement(...)`. The fixture SHALL NOT use `com.google.testing.compile` for type sourcing.

The set SHALL include at minimum: `int`, `long`, the primitive wrapper types `Integer` and `Long`, `String`, one user-defined enum, `java.time.LocalDateTime`, `java.time.Instant`, and `java.util.List<E>` where `E` is parameterised over the universe. All `TypeMirror` instances SHALL come from the same `Types` and `Elements` instance — namely those returned by `task.getTypes()` and `task.getElements()` — so that javac's equality semantics hold across the universe.

`TypeUniverse` SHALL initialise javac at most once per JVM (modulo separate class loaders). Test classes SHALL access `TypeUniverse` through `static` fields initialised on first reference.

Consumers (the `processor` module's algebraic specs, the `strategies-builtin` module's per-strategy specs, and any third-party strategy author) SHALL access `TypeUniverse` by declaring `testImplementation testFixtures(project(':spi'))` (or the equivalent published-coordinate dependency).

#### Scenario: Universe types share an Elements
- **WHEN** `TypeUniverse.STRING` and `TypeUniverse.INTEGER` are obtained
- **THEN** both `TypeMirror` instances were resolved against the same `javax.lang.model.util.Elements`
- **AND** equality and `Types.isSameType(...)` behave consistently across the set

#### Scenario: Initialisation is amortised
- **WHEN** any two distinct test methods access `TypeUniverse` fields
- **THEN** the underlying `JavacTask` is constructed at most once across the JVM

#### Scenario: Type sourcing uses only public javac API
- **WHEN** the source of `TypeUniverse` is inspected
- **THEN** it imports `com.sun.source.util.JavacTask` and no class from `com.sun.tools.javac.*`
- **AND** it never invokes `task.parse()`, `task.analyze()`, or `task.call()`
- **AND** the `JavacTask` is held in a field whose lifetime equals the JVM's so that captured `TypeMirror` instances remain valid

#### Scenario: TypeUniverse is consumable from outside the processor module
- **WHEN** any Gradle module declares `testImplementation testFixtures(project(':spi'))`
- **THEN** `io.github.joke.percolate.spi.test.TypeUniverse` resolves from its tests' compile classpath
- **AND** the produced `TypeMirror` instances are interoperable across modules (same singleton task)

### Requirement: HarnessResolveCtx fixture

The class `io.github.joke.percolate.spi.test.HarnessResolveCtx` in `spi/src/testFixtures/groovy/` (Groovy source, published via `percolate-spi`'s `testFixtures` configuration) SHALL provide a `ResolveCtx` implementation suitable for testing strategy implementations in isolation. It SHALL expose a static `create()` factory method that returns a `ResolveCtx` backed by `TypeUniverse`'s `Types` and `Elements` instances.

Consumers SHALL access `HarnessResolveCtx` by declaring `testImplementation testFixtures(project(':spi'))`.

#### Scenario: HarnessResolveCtx provides Types and Elements from the shared universe
- **WHEN** `HarnessResolveCtx.create()` is invoked
- **THEN** the returned `ResolveCtx`'s `types()` and `elements()` return the same instances used by `TypeUniverse`

#### Scenario: HarnessResolveCtx is consumable from outside the processor module
- **WHEN** any Gradle module declares `testImplementation testFixtures(project(':spi'))`
- **THEN** `io.github.joke.percolate.spi.test.HarnessResolveCtx` resolves from its tests' compile classpath

### Requirement: Invariant checks exposed on `ExpansionResult`

`ExpansionResult` SHALL expose four invariant-related checks that tests MAY call explicitly. The harness itself SHALL NOT assert these before returning — under explicit opt-in there is no auto-invariant to opt out of, so the previously deferred opt-out mechanism is closed as not needed.

- **Convergence flag** — `result.converged()` returns `false` when `ExpandStage` emitted an "Expansion did not converge after N rounds" diagnostic, otherwise `true`.
- **Idempotence stub** — `result.isIdempotent()` is reserved for a structural same-graph check; the current implementation returns `true` unconditionally. Wiring it to a real comparison is a future concern.
- **Identity collapse** — `result.hasIdentityCollisions()` SHALL return `true` iff two distinct `Node` instances in the result share the same `Node.id()` (which encodes `(scope, location, type)`).
- **Orphan REALISED nodes** — `result.hasOrphanRealisedNodes()` SHALL return `true` iff any `REALISED` edge endpoint is unreachable, via `REALISED`/`MARKER` edges, from any `SEED`-edge endpoint.

The `roundCount()` accessor SHALL be present but its value is currently a placeholder (`1`) until `ExpandStage` publishes rounds through `MapperContext`. Tests SHOULD NOT rely on the value today.

#### Scenario: Identity-collapse check is structural and total
- **WHEN** `ExpansionHarness.expand(seed, ...)` returns
- **THEN** `result.hasIdentityCollisions()` examines every node in the expanded graph
- **AND** returns `true` iff at least two distinct nodes share `Node.id()`

#### Scenario: Orphan-detection respects the traversable lattice
- **WHEN** a result graph contains a `REALISED` edge whose endpoints have no `REALISED`/`MARKER` path to any `SEED`-edge endpoint
- **THEN** `result.hasOrphanRealisedNodes()` returns `true`
- **AND** a result graph whose `REALISED` edges are all anchored to `SEED` endpoints returns `false`

#### Scenario: Convergence reflects ExpandStage diagnostic
- **WHEN** the harness captures a diagnostic containing "did not converge"
- **THEN** `result.converged()` returns `false`
- **AND** `result.diagnostics()` contains that message verbatim

### Requirement: DOT rendering inlined in `ExpansionAssertions` failure messages

Every failure message produced by `ExpansionAssertions` (in `processor/src/test/groovy/io/github/joke/percolate/processor/test/`), including `reachable(...)`, `reportedError(...)`, and `Chain.forSeedEdge(...)`, SHALL include the DOT rendering of the expanded graph (produced by `io.github.joke.percolate.processor.graph.DotRenderer`) inlined in the message. No sibling file SHALL be written. If the result has no mapper type captured, a placeholder string SHALL be substituted for the DOT block.

If `ExpandStage` aborts before producing a complete graph (cycle detected, budget exhausted), the partial graph that the harness captured SHALL still be DOT-rendered and attached.

#### Scenario: Assertion failure carries DOT
- **WHEN** an `ExpansionAssertions.reachable(from, to)` assertion fails
- **THEN** the thrown `AssertionError`'s message contains the DOT rendering of the expanded graph

#### Scenario: Result without mapper type yields a placeholder block
- **WHEN** an `ExpansionResult` was constructed without a `TypeElement` (legacy `of(...)` overloads, or harness paths that supply `null`)
- **THEN** `result.dotRender()` returns a placeholder string instead of throwing
- **AND** assertion failure messages still produce a usable error

### Requirement: Engine tests live in `processor/src/test/`; type-system fixtures live in `spi` testFixtures

Every test that exercises `ExpandStage`, its phases, the harness, or the assertions SHALL live in the `processor` module's test sources (`processor/src/test/groovy/`). No published Gradle module SHALL exist whose sole purpose is to host engine-test infrastructure.

Engine-specific helpers — `ExpansionAssertions`, `ExpansionHarness`, `GraphFixtures`, plus any internal stubs the harness needs — SHALL be co-located with the tests at `processor/src/test/groovy/io/github/joke/percolate/processor/test/` (Groovy sources). These helpers depend on engine internals (`MapperContext`, `Diagnostics`, `ProcessorModule`, `MapperGraph`, `ExpandGroupsPhase`) and therefore cannot move out of the processor module.

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
